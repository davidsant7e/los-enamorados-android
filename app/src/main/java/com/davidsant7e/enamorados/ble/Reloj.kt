package com.davidsant7e.enamorados.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import com.davidsant7e.enamorados.protocolo.Comandos
import com.davidsant7e.enamorados.protocolo.IconoApp
import com.davidsant7e.enamorados.protocolo.Lector
import com.davidsant7e.enamorados.protocolo.Protocolo
import com.davidsant7e.enamorados.protocolo.Recibido
import com.davidsant7e.enamorados.registro.Registro
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar

/** En qué va la conexión con el reloj. */
sealed class Estado(val texto: String) {
    data object SinReloj : Estado("Elige tu reloj")
    data object Desconectado : Estado("Desconectado")
    data object Conectando : Estado("Conectando…")
    data object Preparando : Estado("Preparando…")
    data class Listo(val mtu: Int) : Estado("Conectado")
    data class Error(val mensaje: String) : Estado("Error: $mensaje")
}

/** Una línea de la consola: lo que se envió (→), lo que llegó (←) o una nota (·). */
data class LineaConsola(val momento: Long, val direccion: Char, val texto: String, val hex: String = "")

/**
 * Conexión BLE con el reloj por el canal de HryFine. Android hace una operación GATT a la vez, así que todas
 * pasan por [operar] (un candado y la espera de su callback). Puede convivir con HryFine: si HryFine ya está
 * conectado, Android comparte el enlace y esta app solo abre su propio canal GATT.
 */
@SuppressLint("MissingPermission") // los permisos se piden en la pantalla antes de conectar
class Reloj(contexto: Context) {
    private val ctx = contexto.applicationContext
    private val manager = ctx.getSystemService(BluetoothManager::class.java)
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val preferencias = ctx.getSharedPreferences("reloj", Context.MODE_PRIVATE)

    private val _estado = MutableStateFlow<Estado>(if (direccionGuardada() == null) Estado.SinReloj else Estado.Desconectado)
    val estado: StateFlow<Estado> = _estado.asStateFlow()
    private val _bateria = MutableStateFlow<Int?>(null)
    val bateria: StateFlow<Int?> = _bateria.asStateFlow()
    private val _info = MutableStateFlow<Map<String, String>>(emptyMap())
    val info: StateFlow<Map<String, String>> = _info.asStateFlow()
    private val _consola = MutableStateFlow<List<LineaConsola>>(emptyList())
    val consola: StateFlow<List<LineaConsola>> = _consola.asStateFlow()
    private val _musica = MutableStateFlow(preferencias.getBoolean("musica", true))
    /** Los botones de música del reloj controlan la música del celular. */
    val musica: StateFlow<Boolean> = _musica.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var rx: BluetoothGattCharacteristic? = null
    private var mtu = 23
    private val lector = Lector()
    private val candado = Mutex()
    @Volatile private var pendiente: CompletableDeferred<Pair<Int, ByteArray?>>? = null
    private var vibrando: Job? = null

    fun direccionGuardada(): String? = preferencias.getString("direccion", null)
    fun nombreGuardado(): String? = preferencias.getString("nombre", null)

    fun elegir(direccion: String, nombre: String?) {
        preferencias.edit().putString("direccion", direccion.uppercase()).putString("nombre", nombre).apply()
        nota("Reloj elegido: ${nombre ?: "sin nombre"} ($direccion)")
        Registro.evento("RELOJ", "elegido", "direccion" to direccion, "nombre" to nombre)
        conectar()
    }

    fun cambiarMusica(activo: Boolean) {
        _musica.value = activo
        preferencias.edit().putBoolean("musica", activo).apply()
    }

    // ------------------------------------------------------------------ conexión

    /** Conecta solo si no hay ya una conexión en curso o lista (al volver a abrir la app). */
    fun conectarSiHaceFalta() {
        when (_estado.value) {
            is Estado.Listo, Estado.Conectando, Estado.Preparando -> return
            else -> conectar()
        }
    }

    fun conectar() {
        val direccion = direccionGuardada() ?: run { _estado.value = Estado.SinReloj; return }
        val adaptador = manager?.adapter
        if (adaptador == null || !adaptador.isEnabled) { _estado.value = Estado.Error("Bluetooth apagado"); return }
        cerrarGatt()
        _estado.value = Estado.Conectando
        nota("Conectando con $direccion…")
        Registro.evento("CONEXION", "inicio", "direccion" to direccion)
        val dispositivo = runCatching { adaptador.getRemoteDevice(direccion) }.getOrElse {
            _estado.value = Estado.Error("dirección inválida"); return
        }
        gatt = dispositivo.connectGatt(ctx, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun desconectar() {
        vibrando?.cancel()
        cerrarGatt()
        _estado.value = if (direccionGuardada() == null) Estado.SinReloj else Estado.Desconectado
        nota("Desconectado por ti")
        Registro.evento("CONEXION", "cerrada", "por" to "usuario")
    }

    private fun cerrarGatt() {
        gatt?.let { runCatching { it.disconnect() }; runCatching { it.close() } }
        gatt = null; rx = null; mtu = 23
        lector.vaciar()
        pendiente?.complete(-1 to null); pendiente = null
    }

    /** Dispositivos con los que el celular ya tiene un enlace GATT (por ejemplo por HryFine). */
    fun conectadosAhora(): List<BluetoothDevice> = runCatching { manager?.getConnectedDevices(BluetoothProfile.GATT) }.getOrNull() ?: emptyList()

    fun emparejados(): List<BluetoothDevice> = runCatching { manager?.adapter?.bondedDevices?.toList() }.getOrNull() ?: emptyList()

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, nuevo: Int) {
            alcance.launch {
                if (g != gatt) return@launch
                if (nuevo == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    _estado.value = Estado.Preparando
                    nota("Enlace abierto; buscando servicios…")
                    delay(600) // algunos relojes necesitan un momento antes de listar servicios
                    if (!g.discoverServices()) falla("no se pudieron pedir los servicios")
                } else {
                    val antes = _estado.value
                    cerrarGatt()
                    val motivo = if (status == 133 || status == 8 || status == 19) "el reloj no respondió (código $status)" else "código $status"
                    _estado.value = if (antes is Estado.Listo) Estado.Desconectado else Estado.Error(motivo)
                    nota("Se cerró la conexión: $motivo")
                    Registro.evento("CONEXION", "cerrada", "status" to status, "estaba" to antes.texto)
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            alcance.launch { if (g == gatt) preparar(g, status) }
        }

        override fun onMtuChanged(g: BluetoothGatt, nuevo: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) mtu = nuevo
            pendiente?.complete(status to null)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            pendiente?.complete(status to null)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            pendiente?.complete(status to null)
        }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, valor: ByteArray, status: Int) {
            pendiente?.complete(status to valor)
        }

        @Deprecated("Android 12 o anterior")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT < 33) pendiente?.complete(status to c.value)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, valor: ByteArray) {
            val copia = valor.copyOf()
            alcance.launch { llego(c, copia) }
        }

        @Deprecated("Android 12 o anterior")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) {
                val copia = c.value?.copyOf() ?: return
                alcance.launch { llego(c, copia) }
            }
        }
    }

    private suspend fun preparar(g: BluetoothGatt, status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) return falla("no se pudieron leer los servicios (código $status)")
        val arbol = g.services.joinToString("\n") { s ->
            s.uuid.toString() + "\n" + s.characteristics.joinToString("\n") { c -> "   " + c.uuid + " " + propiedades(c.properties) }
        }
        nota("Servicios del reloj:\n$arbol")
        Registro.evento("GATT", "servicios", "lista" to g.services.joinToString(" ") { it.uuid.toString().take(8) })

        val servicio = g.getService(Protocolo.SERVICIO) ?: g.getService(Protocolo.SERVICIO_NORDIC)
            ?: return falla("este reloj no tiene el canal de HryFine (6E400001…)")
        rx = servicio.getCharacteristic(Protocolo.RX)
            ?: servicio.characteristics.firstOrNull { it.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 }
        val tx = servicio.getCharacteristic(Protocolo.TX)
            ?: servicio.characteristics.firstOrNull { it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 }
        if (rx == null || tx == null) return falla("faltan las características RX/TX")

        operar("MTU") { g.requestMtu(247) }
        if (g != gatt) return // se desconectó mientras tanto
        nota("MTU: $mtu (cada envío lleva hasta ${mtu - 3} bytes)")
        if (!avisos(g, tx)) return if (g == gatt) falla("el reloj no aceptó los avisos de TX") else Unit

        g.getService(Protocolo.BATERIA)?.getCharacteristic(Protocolo.NIVEL_BATERIA)?.let { c ->
            leer(g, c)?.firstOrNull()?.let { _bateria.value = it.toInt() and 0xFF }
            if (c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) avisos(g, c)
        }
        g.getService(Protocolo.INFO)?.let { s ->
            val datos = LinkedHashMap<String, String>()
            for ((uuid, nombre) in Protocolo.CAMPOS_INFO) {
                val c = s.getCharacteristic(uuid) ?: continue
                leer(g, c)?.let { v -> String(v, Charsets.UTF_8).trim { it <= ' ' || it == '\u0000' }.takeIf { it.isNotEmpty() }?.let { datos[nombre] = it } }
            }
            _info.value = datos
        }
        if (g != gatt) return // el reloj se fue mientras se leían batería e info: no marcar «Conectado»
        _estado.value = Estado.Listo(mtu)
        nota("Listo. Batería: ${_bateria.value?.let { "$it %" } ?: "?"}  ${_info.value.entries.joinToString("  ") { "${it.key}: ${it.value}" }}")
        Registro.evento("CONEXION", "lista", "mtu" to mtu, "bateria" to _bateria.value, "info" to _info.value.toString())
    }

    private fun falla(mensaje: String) {
        _estado.value = Estado.Error(mensaje)
        nota("Error: $mensaje")
        Registro.evento("CONEXION", "error", "mensaje" to mensaje)
    }

    private suspend fun avisos(g: BluetoothGatt, c: BluetoothGattCharacteristic): Boolean {
        if (!g.setCharacteristicNotification(c, true)) return false
        val d = c.getDescriptor(Protocolo.CCCD) ?: return true // sin CCCD: algunos relojes avisan igual
        val valor = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        val r = operar("avisos ${c.uuid.toString().take(8)}") {
            if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, valor) == BluetoothGatt.GATT_SUCCESS
            else @Suppress("DEPRECATION") run { d.value = valor; g.writeDescriptor(d) }
        }
        return r?.first == BluetoothGatt.GATT_SUCCESS
    }

    private suspend fun leer(g: BluetoothGatt, c: BluetoothGattCharacteristic): ByteArray? =
        operar("leer ${c.uuid.toString().take(8)}") { g.readCharacteristic(c) }?.takeIf { it.first == BluetoothGatt.GATT_SUCCESS }?.second

    /** Ejecuta una operación GATT y espera su callback (máximo 5 s). */
    private suspend fun operar(que: String, accion: () -> Boolean): Pair<Int, ByteArray?>? = candado.withLock {
        val espera = CompletableDeferred<Pair<Int, ByteArray?>>()
        pendiente = espera
        val aceptada = runCatching(accion).getOrElse { Registro.error("GATT", it, "operacion" to que); false }
        val r = if (aceptada) withTimeoutOrNull(5_000) { espera.await() } else null
        pendiente = null
        if (r == null) Registro.evento("GATT", "sin respuesta", "operacion" to que, "aceptada" to aceptada)
        r
    }

    // ------------------------------------------------------------------ enviar

    /** Envía una trama ya armada, partida según la MTU. Devuelve false si no hay conexión o falla. */
    suspend fun enviar(trama: ByteArray, que: String): Boolean {
        val g = gatt; val c = rx
        if (g == null || c == null || _estado.value !is Estado.Listo) { nota("No conectado: no se envió «$que»"); return false }
        agregar(LineaConsola(System.currentTimeMillis(), '→', que, Protocolo.hex(trama)))
        Registro.evento("ENVIO", que, "bytes" to Protocolo.hex(trama).take(120))
        val tipo = if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        for (trozo in Protocolo.trozos(trama, mtu - 3)) {
            val r = operar("escribir $que") {
                if (Build.VERSION.SDK_INT >= 33) g.writeCharacteristic(c, trozo, tipo) == BluetoothGatt.GATT_SUCCESS
                else @Suppress("DEPRECATION") run { c.writeType = tipo; c.value = trozo; g.writeCharacteristic(c) }
            }
            if (r?.first != BluetoothGatt.GATT_SUCCESS) { nota("Falló el envío de «$que»"); return false }
        }
        return true
    }

    fun lanzar(que: String, trama: ByteArray) { alcance.launch { enviar(trama, que) } }

    fun vibrar() = lanzar("vibrar", Comandos.buscar(true))

    /** El motor vibra ~1,5 s por pulso: para vibrar más tiempo se repite cada 0,85 s y al final se detiene. */
    fun vibrarDurante(segundos: Int) {
        vibrando?.cancel()
        vibrando = alcance.launch {
            val fin = SystemClock.elapsedRealtime() + segundos * 1000L
            try {
                while (SystemClock.elapsedRealtime() < fin) {
                    if (!enviar(Comandos.buscar(true), "vibrar")) break
                    delay(850)
                }
            } finally {
                withContext(NonCancellable) { enviar(Comandos.buscar(false), "dejar de vibrar") }
            }
        }
    }

    fun sincronizarHora() {
        val c = Calendar.getInstance()
        lanzar("hora", Comandos.hora(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND)))
    }

    /** Como HryFine: primero los interruptores de avisos, luego el mensaje. */
    fun mensaje(texto: String, icono: IconoApp) {
        alcance.launch {
            if (enviar(Comandos.interruptoresAvisos(), "interruptores de avisos")) enviar(Comandos.mensaje(texto, icono), "mensaje")
        }
    }

    fun limpiarConsola() { _consola.value = emptyList() }

    // ------------------------------------------------------------------ recibir

    private fun llego(c: BluetoothGattCharacteristic, valor: ByteArray) {
        if (c.uuid == Protocolo.NIVEL_BATERIA) {
            valor.firstOrNull()?.let { _bateria.value = it.toInt() and 0xFF }
            nota("Batería: ${_bateria.value} %")
            return
        }
        for (r in lector.agregar(valor)) {
            agregar(LineaConsola(System.currentTimeMillis(), '←', r.describir(), Protocolo.hex(r.bytes)))
            Registro.evento("RECIBIDO", r.javaClass.simpleName.lowercase(), "que" to r.describir(), "bytes" to Protocolo.hex(r.bytes).take(120))
            if (r is Recibido.Evento && r.categoria == Protocolo.CAT_CONTROL && _musica.value) boton(r.sub)
        }
    }

    /** Botones de la pantalla de música del reloj → teclas multimedia del celular. */
    private fun boton(sub: Int) {
        val tecla = when (sub) {
            0x0A, 0x0B -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            0x0C -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            0x0D -> KeyEvent.KEYCODE_MEDIA_NEXT
            else -> return
        }
        val audio = ctx.getSystemService(AudioManager::class.java) ?: return
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, tecla))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, tecla))
        nota("Música: ${Protocolo.nombre(Protocolo.CAT_CONTROL, sub)}")
    }

    private fun nota(texto: String) = agregar(LineaConsola(System.currentTimeMillis(), '·', texto))

    private fun agregar(l: LineaConsola) = _consola.update { (it + l).takeLast(MAX_LINEAS) }

    companion object {
        const val MAX_LINEAS = 400

        fun propiedades(p: Int): String = buildList {
            if (p and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("leer")
            if (p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("escribir")
            if (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add("escribir-sin-respuesta")
            if (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("avisa")
            if (p and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("indica")
        }.joinToString(",", "[", "]")
    }
}
