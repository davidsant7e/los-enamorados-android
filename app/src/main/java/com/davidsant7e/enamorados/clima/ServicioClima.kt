package com.davidsant7e.enamorados.clima

import android.content.Context
import com.davidsant7e.enamorados.ble.Estado
import com.davidsant7e.enamorados.ble.Reloj
import com.davidsant7e.enamorados.registro.Registro
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

/** Lo que muestra la tarjeta de Clima. */
data class VistaClima(
    val lugar: OpenMeteo.Lugar? = null,
    val clima: OpenMeteo.ClimaHoy? = null,
    /** Cuándo se descargó [clima] (ms). */
    val momento: Long = 0,
    val cargando: Boolean = false,
    val error: String? = null,
    /** Varias ciudades con el mismo nombre: el usuario elige. */
    val opciones: List<OpenMeteo.Lugar> = emptyList(),
    /** Última vez que se envió al reloj (ms), o 0. */
    val enviadoAlReloj: Long = 0,
    /** La ciudad sale de la ubicación del celular (si no, la elegida a mano). */
    val automatico: Boolean = false,
)

/**
 * Descarga el clima de Open-Meteo para la ciudad elegida (la que elijas, o tu ubicación si lo pides) y lo envía al
 * reloj solo: al conectar y luego cada 3 horas mientras siga conectado. Vive con el proceso, como el [Reloj].
 */
class ServicioClima(contexto: Context, private val reloj: Reloj) {
    private val ctx = contexto.applicationContext
    private val preferencias = contexto.applicationContext.getSharedPreferences("clima", Context.MODE_PRIVATE)
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _vista = MutableStateFlow(guardada())
    val vista: StateFlow<VistaClima> = _vista.asStateFlow()
    private var envios: Job? = null

    init {
        // Al pasar a «Conectado» arranca el envío periódico; al desconectarse se detiene.
        alcance.launch {
            reloj.estado.distinctUntilChangedBy { it is Estado.Listo }.collect { e ->
                envios?.cancel()
                if (e is Estado.Listo) envios = alcance.launch {
                    delay(3_000) // que el reloj termine de prepararse (hora, avisos…)
                    while (true) {
                        enviarAlReloj(automatico = true)
                        delay(CADA_CUANTO_ENVIAR)
                    }
                }
            }
        }
    }

    /** Descarga de nuevo si lo guardado tiene más de media hora (al abrir la app). */
    fun actualizarSiHaceFalta() {
        if (System.currentTimeMillis() - _vista.value.momento > VIEJO) actualizar()
    }

    fun actualizar() { alcance.launch { descargar() } }

    /** Busca la ciudad; si hay una sola la usa, si hay varias deja que el usuario elija. */
    fun buscarCiudad(nombre: String) {
        if (nombre.isBlank()) return
        alcance.launch {
            _vista.update { it.copy(cargando = true, error = null, opciones = emptyList()) }
            val lugares = runCatching { OpenMeteo.lugaresDe(bajar(OpenMeteo.urlBusqueda(nombre))) }
            lugares.onFailure { f ->
                _vista.update { it.copy(cargando = false, error = "No se pudo buscar la ciudad (${f.message ?: "sin internet"}).") }
            }
            val l = lugares.getOrNull() ?: return@launch
            when {
                l.isEmpty() -> _vista.update { it.copy(cargando = false, error = "No encontré «$nombre». Prueba con otro nombre.") }
                l.size == 1 -> elegir(l.first())
                else -> _vista.update { it.copy(cargando = false, opciones = l) }
            }
        }
    }

    /** Clima según la ubicación del celular (el permiso ya debe estar dado). */
    fun usarUbicacion() {
        preferencias.edit().putBoolean("automatico", true).apply()
        _vista.update { it.copy(automatico = true, opciones = emptyList(), error = null) }
        Registro.evento("CLIMA", "automatico", "activo" to true)
        alcance.launch {
            descargar()
            if (reloj.estado.value is Estado.Listo) enviarAlReloj(automatico = false)
        }
    }

    fun elegir(lugar: OpenMeteo.Lugar) {
        preferencias.edit().putBoolean("automatico", false)
            .putString("nombre", lugar.nombre).putString("region", lugar.region).putString("pais", lugar.pais)
            .putString("lat", lugar.latitud.toString()).putString("lon", lugar.longitud.toString())
            .remove("clima").apply()
        _vista.update { it.copy(lugar = lugar, clima = null, momento = 0, opciones = emptyList(), error = null, automatico = false) }
        alcance.launch {
            descargar()
            if (reloj.estado.value is Estado.Listo) enviarAlReloj(automatico = false)
        }
    }

    fun cancelarOpciones() = _vista.update { it.copy(opciones = emptyList(), cargando = false) }

    /** Botón «Enviar al reloj». */
    fun enviar() { alcance.launch { enviarAlReloj(automatico = false) } }

    private suspend fun enviarAlReloj(automatico: Boolean) {
        // Clima fresco: si lo guardado es viejo, se descarga antes de enviar.
        if (System.currentTimeMillis() - _vista.value.momento > VIEJO) descargar()
        val v = _vista.value
        val c = v.clima ?: return
        val ciudad = v.lugar?.nombre ?: ""
        val hoy = Calendar.getInstance()
        val trama = OpenMeteo.tramaReloj(c, ciudad, hoy.get(Calendar.YEAR), hoy.get(Calendar.MONTH) + 1, hoy.get(Calendar.DAY_OF_MONTH))
        if (reloj.enviar(trama, if (automatico) "clima (automático)" else "clima")) {
            val ahora = System.currentTimeMillis()
            preferencias.edit().putLong("enviado", ahora).apply()
            _vista.update { it.copy(enviadoAlReloj = ahora) }
        }
    }

    private suspend fun descargar() {
        _vista.update { it.copy(cargando = true, error = null) }
        val resultado = runCatching {
            val aqui = if (_vista.value.automatico) {
                if (!Ubicacion.tienePermiso(ctx)) error("falta el permiso de ubicación; pulsa «Usar mi ubicación»")
                Ubicacion.lugarActual(ctx) // si el celular no sabe dónde está, se usa la última ciudad
            } else null
            // Sin ciudad elegida ni ubicación no se adivina ninguna: cada quien elige la suya.
            val lugar = aqui ?: _vista.value.lugar ?: error("elige tu ciudad o pulsa «Usar mi ubicación»")
            val json = bajar(OpenMeteo.urlPronostico(lugar.latitud, lugar.longitud))
            val clima = OpenMeteo.climaDe(json) ?: error("respuesta inesperada")
            Triple(lugar, clima, json)
        }
        resultado.onSuccess { (lugar, clima, json) ->
            val ahora = System.currentTimeMillis()
            preferencias.edit()
                .putString("nombre", lugar.nombre).putString("region", lugar.region).putString("pais", lugar.pais)
                .putString("lat", lugar.latitud.toString()).putString("lon", lugar.longitud.toString())
                .putString("clima", json).putLong("momento", ahora).apply()
            _vista.update { it.copy(lugar = lugar, clima = clima, momento = ahora, cargando = false, error = null) }
            Registro.evento("CLIMA", "descargado", "ciudad" to lugar.nombre, "temp" to clima.actual, "codigo" to clima.codigo)
        }.onFailure { f ->
            _vista.update { it.copy(cargando = false, error = "No se pudo traer el clima (${f.message ?: "sin internet"}).") }
            Registro.evento("CLIMA", "error", "motivo" to (f.message ?: f.javaClass.simpleName))
        }
    }

    private suspend fun bajar(direccion: String): String = withContext(Dispatchers.IO) {
        val c = URL(direccion).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            c.setRequestProperty("User-Agent", "LosEnamorados/0.3 (Android)")
            val codigo = c.responseCode
            if (codigo !in 200..299) error("HTTP $codigo")
            c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: java.io.IOException) {
            throw IllegalStateException("sin conexión a internet", e)
        } finally {
            c.disconnect()
        }
    }

    private fun guardada(): VistaClima {
        val nombre = preferencias.getString("nombre", null)
        val lat = preferencias.getString("lat", null)?.toDoubleOrNull()
        val lon = preferencias.getString("lon", null)?.toDoubleOrNull()
        val lugar = if (nombre != null && lat != null && lon != null)
            OpenMeteo.Lugar(nombre, preferencias.getString("region", null), preferencias.getString("pais", null), lat, lon) else null
        val clima = preferencias.getString("clima", null)?.let { runCatching { OpenMeteo.climaDe(it) }.getOrNull() }
        return VistaClima(lugar = lugar, clima = clima, momento = if (clima != null) preferencias.getLong("momento", 0) else 0,
            enviadoAlReloj = preferencias.getLong("enviado", 0), automatico = preferencias.getBoolean("automatico", false))
    }

    companion object {
        const val CADA_CUANTO_ENVIAR = 3 * 60 * 60 * 1000L
        const val VIEJO = 30 * 60 * 1000L
    }
}
