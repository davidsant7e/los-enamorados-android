package com.davidsant7e.enamorados.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davidsant7e.enamorados.ble.ActividadVista
import com.davidsant7e.enamorados.ble.Buscador
import com.davidsant7e.enamorados.ble.Encontrado
import com.davidsant7e.enamorados.ble.Estado
import com.davidsant7e.enamorados.ble.LineaConsola
import com.davidsant7e.enamorados.ble.Midiendo
import com.davidsant7e.enamorados.ble.Reloj
import com.davidsant7e.enamorados.ble.ResultadoMedicion
import com.davidsant7e.enamorados.clima.OpenMeteo
import com.davidsant7e.enamorados.clima.ServicioClima
import com.davidsant7e.enamorados.clima.VistaClima
import com.davidsant7e.enamorados.protocolo.Clima
import com.davidsant7e.enamorados.protocolo.Comandos
import com.davidsant7e.enamorados.protocolo.IconoApp
import com.davidsant7e.enamorados.protocolo.Protocolo
import com.davidsant7e.enamorados.protocolo.Salud
import com.davidsant7e.enamorados.salud.Explicar
import com.davidsant7e.enamorados.salud.Tono
import com.davidsant7e.enamorados.salud.Zona
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class Acciones(
    val pedirPermisos: () -> Unit,
    val elegir: () -> Unit,
    val cancelarEleccion: () -> Unit,
    val elegido: (String, String?) -> Unit,
    val buscar: () -> Unit,
    val conectar: () -> Unit,
    val copiarConsola: () -> Unit,
    /** Pide el permiso de ubicación aproximada y, si lo dan, activa el clima automático. */
    val usarUbicacion: () -> Unit,
)

private val ES = Locale.forLanguageTag("es")

/**
 * Pantalla única. De arriba abajo: reloj, clima, salud y lo útil del día a día; al final, plegadas, las herramientas
 * de diagnóstico que usa Claude para descubrir órdenes nuevas.
 */
@Composable
fun Enamorados(
    reloj: Reloj,
    buscador: Buscador,
    clima: ServicioClima,
    ajustes: Ajustes,
    permisos: Boolean,
    eligiendo: Boolean,
    cercanos: List<Encontrado>,
    acciones: Acciones,
) {
    val estado by reloj.estado.collectAsState()
    val bateria by reloj.bateria.collectAsState()
    val info by reloj.info.collectAsState()
    val consola by reloj.consola.collectAsState()
    val musica by reloj.musica.collectAsState()
    val actividad by reloj.actividad.collectAsState()
    val midiendo by reloj.midiendo.collectAsState()
    val resultados by reloj.resultados.collectAsState()
    val lecturas by reloj.lecturas.collectAsState()
    val encontrados by buscador.encontrados.collectAsState()
    val buscando by buscador.buscando.collectAsState()
    val vistaClima by clima.vista.collectAsState()
    val listo = estado is Estado.Listo

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Encabezado() }
            if (!permisos) item { Permisos(acciones.pedirPermisos) }
            // Sin reloj elegido siempre se muestra la elección (el estado sí lo vigila la pantalla).
            if (eligiendo || estado is Estado.SinReloj) {
                item { Eleccion(cercanos, encontrados, buscando, permisos, reloj.direccionGuardada() != null, acciones) }
            } else {
                item { TarjetaReloj(reloj, estado, bateria, info, acciones) }
                item { TarjetaClima(vistaClima, clima, listo, acciones.usarUbicacion) }
                item { PanelSalud(reloj, ajustes, listo, actividad, midiendo, lecturas) }
                item { UsarElReloj(reloj, listo, musica) }
                item { CabeceraAvanzadas(ajustes.avanzadas) { ajustes.cambiarAvanzadas(!ajustes.avanzadas) } }
                if (ajustes.avanzadas) {
                    item { PruebasAvanzadas(reloj, listo) }
                    item { ClimaAMano(reloj, listo) }
                    item { OrdenEnCodigo(reloj, listo) }
                    if (resultados.isNotEmpty()) item { DatosSinDescifrar(resultados, reloj::borrarResultados) }
                    item { Consola(consola, reloj::limpiarConsola, acciones.copiarConsola) }
                }
            }
        }
    }
}

@Composable
private fun Encabezado() {
    Column {
        Text("VI · Los Enamorados", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Text("Tu celular y tu reloj, conversando directo.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Tarjeta(titulo: String, contenido: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            contenido()
        }
    }
}

/** Etiqueta de color con el estado de una lectura («Normal», «Alta, etapa 1»…). */
@Composable
private fun Etiqueta(texto: String, tono: Tono) {
    val color = colorTono(tono)
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f)) {
        Text(texto, Modifier.padding(horizontal = 10.dp, vertical = 2.dp), color = color,
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Permisos(pedir: () -> Unit) {
    Tarjeta("Falta un permiso") {
        Text("Para encontrar el reloj y hablarle, Android pide el permiso de «Dispositivos cercanos» " +
            "(en Android 11 o anterior se llama «Ubicación»). La app no guarda ni envía tu ubicación.")
        Button(onClick = pedir) { Text("Dar permiso") }
    }
}

@Composable
private fun Eleccion(
    fijos: List<Encontrado>, cerca: List<Encontrado>, buscando: Boolean, permisos: Boolean, puedeCancelar: Boolean, acciones: Acciones,
) {
    var manual by rememberSaveable { mutableStateOf("") }
    val todos = (fijos + cerca).groupBy { it.direccion }.map { (_, mismos) ->
        mismos.first().copy(
            canalHryFine = mismos.any { it.canalHryFine },
            senal = mismos.firstNotNullOfOrNull { it.senal },
            nombre = mismos.firstNotNullOfOrNull { it.nombre },
            origen = mismos.joinToString(", ") { it.origen },
        )
    }.sortedWith(Buscador.ORDEN)
    Tarjeta("Elige tu reloj") {
        Text("El reloj aparece como «Smartwatch». Su dirección de datos (BLE) es la que termina distinto a la de llamadas: " +
            "la ves en el menú «À propos / Acerca de» del reloj. Si HryFine está conectado, el reloj sale en «conectado ahora».",
            style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = acciones.buscar, enabled = permisos && !buscando) { Text(if (buscando) "Buscando…" else "Buscar cerca") }
            if (buscando) { Spacer(Modifier.width(12.dp)); CircularProgressIndicator(Modifier.width(24.dp)) }
            if (puedeCancelar) { Spacer(Modifier.weight(1f)); TextButton(onClick = acciones.cancelarEleccion) { Text("Cancelar") } }
        }
        if (todos.isEmpty()) Text(if (buscando) "Buscando aparatos…" else "Nada todavía.", style = MaterialTheme.typography.bodySmall)
        todos.forEach { e ->
            HorizontalDivider()
            Column(Modifier.fillMaxWidth().clickable { acciones.elegido(e.direccion, e.nombre) }.padding(vertical = 8.dp)) {
                Text((e.nombre ?: "Sin nombre") + if (e.canalHryFine) "  ★ canal HryFine" else "",
                    fontWeight = if (e.canalHryFine) FontWeight.Bold else FontWeight.Normal)
                Text(e.direccion + "  ·  " + e.origen + (e.senal?.let { "  ·  señal $it dBm" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
        HorizontalDivider()
        OutlinedTextField(
            value = manual, onValueChange = { manual = it.uppercase().take(17) },
            label = { Text("O escribe la dirección") }, placeholder = { Text("AA:BB:CC:DD:EE:FF") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        )
        val valida = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$").matches(manual)
        Button(onClick = { acciones.elegido(manual, null) }, enabled = valida && permisos) { Text("Usar esta dirección") }
    }
}

@Composable
private fun TarjetaReloj(reloj: Reloj, estado: Estado, bateria: Int?, info: Map<String, String>, acciones: Acciones) {
    Tarjeta(reloj.nombreGuardado() ?: "Reloj") {
        Text(estado.texto + (bateria?.let { "  ·  batería $it %" } ?: ""), fontWeight = FontWeight.SemiBold,
            color = if (estado is Estado.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        Text(reloj.direccionGuardada() ?: "", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        if (info.isNotEmpty()) Text(info.entries.joinToString("  ·  ") { "${it.key}: ${it.value}" }, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (estado) {
                Estado.Esperando -> OutlinedButton(onClick = reloj::desconectar) { Text("Cancelar") }
                is Estado.Listo, Estado.Conectando, Estado.Preparando -> OutlinedButton(onClick = reloj::desconectar) { Text("Desconectar") }
                else -> Button(onClick = acciones.conectar) { Text("Conectar") }
            }
            TextButton(onClick = acciones.elegir) { Text("Cambiar de reloj") }
        }
    }
}

// ---------------------------------------------------------------------------------------------- clima

@Composable
private fun TarjetaClima(v: VistaClima, servicio: ServicioClima, listo: Boolean, usarUbicacion: () -> Unit) {
    val hora = remember { SimpleDateFormat("HH:mm", ES) }
    var cambiando by rememberSaveable { mutableStateOf(false) }
    var ciudad by rememberSaveable { mutableStateOf("") }
    Tarjeta("Clima" + (v.lugar?.let { " en ${it.nombre}" } ?: "") + if (v.automatico) "  📍" else "") {
        val c = v.clima
        if (c != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(OpenMeteo.emoji(c.codigo), fontSize = 44.sp)
                Column {
                    Text("${c.actual.roundToInt()} °C", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    Text(OpenMeteo.descripcion(c.codigo), style = MaterialTheme.typography.bodyLarge)
                }
            }
            Text("Hoy: mínima ${c.minima.roundToInt()} °C · máxima ${c.maxima.roundToInt()} °C", fontWeight = FontWeight.SemiBold)
            Text((v.lugar?.descripcion ?: "") + "  ·  actualizado a las ${hora.format(Date(v.momento))}",
                style = MaterialTheme.typography.bodySmall)
        } else if (!v.cargando && v.error == null) {
            Text("Todavía no hay clima. Pulsa «Actualizar» (necesita internet).", style = MaterialTheme.typography.bodyMedium)
        }
        if (v.cargando) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("Trayendo el clima…", style = MaterialTheme.typography.bodySmall)
        }
        v.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        if (v.opciones.isNotEmpty()) {
            Text("¿Cuál de estas?", fontWeight = FontWeight.SemiBold)
            v.opciones.forEach { l ->
                Text(l.descripcion, Modifier.fillMaxWidth().clickable { servicio.elegir(l); cambiando = false }.padding(vertical = 8.dp))
            }
            TextButton(onClick = servicio::cancelarOpciones) { Text("Cancelar") }
        } else if (cambiando) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(ciudad, { ciudad = it }, label = { Text("Ciudad") }, placeholder = { Text("Medellín") },
                    singleLine = true, modifier = Modifier.weight(1f))
                Button(onClick = { servicio.buscarCiudad(ciudad); cambiando = false }, enabled = ciudad.isNotBlank()) { Text("Buscar") }
            }
            TextButton(onClick = { cambiando = false }) { Text("Cancelar") }
        }

        FlowRow2 {
            Button(onClick = servicio::enviar, enabled = listo && c != null) { Text("Enviar al reloj") }
            OutlinedButton(onClick = servicio::actualizar, enabled = !v.cargando) { Text("Actualizar") }
            if (!v.automatico) OutlinedButton(onClick = usarUbicacion, enabled = !v.cargando) { Text("📍 Usar mi ubicación") }
            if (!cambiando && v.opciones.isEmpty()) TextButton(onClick = { cambiando = true }) { Text(if (v.automatico) "Elegir ciudad a mano" else "Cambiar ciudad") }
        }
        if (v.automatico) Text("Automático: la ciudad sale de la ubicación aproximada del celular (se revisa cada vez que se actualiza el clima).",
            style = MaterialTheme.typography.bodySmall)
        Text(
            (if (v.enviadoAlReloj > 0) "Enviado al reloj a las ${hora.format(Date(v.enviadoAlReloj))}. " else "") +
                "Se envía solo al conectar el reloj y cada 3 horas mientras siga conectado.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text("En el reloj, el clima aparece en su pantalla de clima (desliza) o en las esferas que lo muestran.",
            style = MaterialTheme.typography.bodySmall)
        Text("Datos: Open-Meteo.", style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow2(contenido: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { contenido() }
}

// ---------------------------------------------------------------------------------------------- salud

private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

private fun fechaDe(l: Salud.Lectura): String {
    val hoy = Calendar.getInstance()
    val esHoy = hoy.get(Calendar.YEAR) == l.anio && hoy.get(Calendar.MONTH) + 1 == l.mes && hoy.get(Calendar.DAY_OF_MONTH) == l.dia
    return if (esHoy) "hoy a las ${l.hora}" else "${l.dia} ${MESES.getOrElse(l.mes - 1) { "?" }} a las ${l.hora}"
}

private fun esDeHoy(momento: Long): Boolean {
    val a = Calendar.getInstance()
    val b = Calendar.getInstance().apply { timeInMillis = momento }
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

@Composable
private fun PanelSalud(
    reloj: Reloj, ajustes: Ajustes, listo: Boolean, actividad: ActividadVista?, midiendo: Midiendo?, lecturas: List<Salud.Lectura>,
) {
    val hora = remember { SimpleDateFormat("d MMM HH:mm", ES) }
    val presion = lecturas.firstOrNull { it.tipo == Salud.Medicion.PRESION && it.valor2 != null }
    val pulso = lecturas.firstOrNull { it.tipo == Salud.Medicion.PULSO }
    val zonas = ajustes.edad?.let { Explicar.zonas(it, ajustes.reposo) }

    Tarjeta("Salud") {
        // Resumen en una línea
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Text(
                Explicar.resumen(actividad?.actividad?.pasos, pulso?.valor, presion?.let { it.valor to it.valor2!! },
                    deHoy = actividad?.let { esDeHoy(it.momento) } ?: false),
                Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            )
        }

        // Pasos
        Text("Pasos", fontWeight = FontWeight.SemiBold)
        if (actividad == null) {
            Text("Todavía no hay pasos del reloj. Pulsa «Actualizar pasos» o abre HryFine: cuando sincroniza, la app también los recibe.",
                style = MaterialTheme.typography.bodySmall)
        } else {
            val a = actividad.actividad
            Text("${Explicar.miles(a.pasos)} pasos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Text("%.2f km  ·  %.1f kcal (por confirmar)".format(ES, a.kilometros, a.kcal), style = MaterialTheme.typography.bodyMedium)
            Text("Actualizado ${hora.format(Date(actividad.momento))}. Es lo que cuenta el reloj.", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = reloj::pedirActividad, enabled = listo) { Text("Actualizar pasos") }

        // Presión
        HorizontalDivider()
        Text("Presión", fontWeight = FontWeight.SemiBold)
        if (presion != null) {
            val alta = presion.valor
            val baja = presion.valor2!!
            val cat = Explicar.presion(alta, baja)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("$alta/$baja", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = colorTono(cat.tono))
                Text("mmHg", style = MaterialTheme.typography.bodySmall)
                Etiqueta(cat.nombre, cat.tono)
            }
            Text(cat.frase, style = MaterialTheme.typography.bodyMedium)
            Text(Explicar.queSignificaPresion(alta, baja), style = MaterialTheme.typography.bodySmall)
            Text("Medida ${fechaDe(presion)}.", style = MaterialTheme.typography.bodySmall)
        } else {
            Text("Aún no hay mediciones de presión. Pulsa «Medir presión».", style = MaterialTheme.typography.bodySmall)
        }

        // Pulso
        HorizontalDivider()
        Text("Pulso", fontWeight = FontWeight.SemiBold)
        if (pulso != null) {
            val cat = Explicar.pulso(pulso.valor)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${pulso.valor}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = colorTono(cat.tono))
                Text("latidos por minuto", style = MaterialTheme.typography.bodySmall)
                Etiqueta(cat.nombre, cat.tono)
            }
            Text(cat.frase, style = MaterialTheme.typography.bodyMedium)
            zonas?.let { Text(Explicar.ubicar(pulso.valor, it).frase, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
            Text("Medido ${fechaDe(pulso)}.", style = MaterialTheme.typography.bodySmall)
        } else {
            Text("Aún no hay mediciones de pulso. Pulsa «Medir pulso».", style = MaterialTheme.typography.bodySmall)
        }

        // Medir
        if (midiendo != null) {
            var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(midiendo) { while (true) { ahora = System.currentTimeMillis(); delay(1000) } }
            val faltan = (midiendo.tipo.segundos - (ahora - midiendo.inicio) / 1000).coerceAtLeast(0)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.width(24.dp))
                Text("Midiendo ${midiendo.tipo.etiqueta.lowercase()}… $faltan s. Deja el reloj quieto en la muñeca.",
                    Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = reloj::detenerMedicion) { Text("Detener") }
        } else {
            FlowRow2 {
                Salud.Medicion.entries.forEach { m ->
                    Button(onClick = { reloj.medir(m) }, enabled = listo) { Text("Medir ${m.etiqueta.lowercase()}") }
                }
            }
            Text("Siéntate tranquilo y deja el reloj quieto; el resultado llega solo a los ~10 s. El oxígeno todavía no responde en este reloj.",
                style = MaterialTheme.typography.bodySmall)
        }

        // Zonas de cardio
        HorizontalDivider()
        ZonasCardio(ajustes, zonas, pulso?.valor)

        // Historial
        if (lecturas.size > 1) {
            HorizontalDivider()
            Text("Mediciones anteriores", fontWeight = FontWeight.SemiBold)
            lecturas.take(10).forEach { l ->
                val (nombre, tono) = when (l.tipo) {
                    Salud.Medicion.PRESION -> l.valor2?.let { Explicar.presion(l.valor, it).let { c -> c.nombre to c.tono } } ?: ("" to Tono.NEUTRO)
                    Salud.Medicion.PULSO -> Explicar.pulso(l.valor).let { c -> c.nombre to c.tono }
                    Salud.Medicion.OXIGENO -> "" to Tono.NEUTRO
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${l.tipo.etiqueta}: ${l.texto}", fontWeight = FontWeight.SemiBold, color = colorTono(tono))
                        Text(fechaDe(l), style = MaterialTheme.typography.bodySmall)
                    }
                    if (nombre.isNotEmpty()) Etiqueta(nombre, tono)
                }
            }
        }
        Text(Explicar.AVISO, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ZonasCardio(ajustes: Ajustes, zonas: List<Zona>?, pulso: Int?) {
    var editando by rememberSaveable { mutableStateOf(false) }
    Text("Zonas de cardio", fontWeight = FontWeight.SemiBold)
    Text("Dicen qué tan fuerte está trabajando tu corazón, de la zona 1 (muy suave) a la 5 (máxima). " +
        "Sirven para saber si un ejercicio es suave, moderado o intenso.", style = MaterialTheme.typography.bodySmall)
    val edad = ajustes.edad
    when {
        editando -> Perfil(ajustes) { editando = false }
        edad == null || zonas == null -> {
            Text("Para calcular tus zonas necesito tu edad.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { editando = true }) { Text("Poner mi edad") }
        }
        else -> {
            val maximo = Explicar.pulsoMaximo(edad)
            Text("Con $edad años, tu pulso máximo estimado es $maximo lpm (fórmula de Tanaka: 208 − 0,7 × edad; " +
                "se equivoca menos que «220 − edad»)." +
                (ajustes.reposo?.let { " Como pusiste tu pulso en reposo ($it lpm), los rangos se ajustan a ti (método de Karvonen)." } ?: ""),
                style = MaterialTheme.typography.bodySmall)
            BarraZonas(zonas, pulso)
            val ubicacion = pulso?.let { Explicar.ubicar(it, zonas) }
            zonas.forEach { z -> FilaZona(z, ubicacion?.zona == z) }
            ubicacion?.let { Text("Tu último pulso: ${it.frase}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
            TextButton(onClick = { editando = true }) { Text("Cambiar edad o pulso en reposo") }
        }
    }
}

@Composable
private fun Perfil(ajustes: Ajustes, listo: () -> Unit) {
    var edad by rememberSaveable { mutableStateOf(ajustes.edad?.toString() ?: "") }
    var reposo by rememberSaveable { mutableStateOf(ajustes.reposo?.toString() ?: "") }
    val edadOk = Explicar.edadDe(edad)
    val reposoOk = Explicar.reposoDe(reposo)
    val reposoValido = reposo.isBlank() || reposoOk != null
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(edad, { edad = it.filter(Char::isDigit).take(3) }, label = { Text("Edad") }, singleLine = true,
            modifier = Modifier.width(100.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = edad.isNotEmpty() && edadOk == null)
        OutlinedTextField(reposo, { reposo = it.filter(Char::isDigit).take(3) }, label = { Text("Pulso en reposo") }, singleLine = true,
            modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = !reposoValido, supportingText = { Text("Opcional") })
    }
    Text("El pulso en reposo es el que tienes sentado y tranquilo (mejor recién levantado). Si no lo sabes, déjalo vacío.",
        style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { ajustes.guardarPerfil(edadOk, reposoOk); listo() }, enabled = edadOk != null && reposoValido) { Text("Guardar") }
        TextButton(onClick = listo) { Text("Cancelar") }
    }
}

/** Barra de las 5 zonas con una marca ▼ donde cae el último pulso (pegada a la izquierda si está por debajo). */
@Composable
private fun BarraZonas(zonas: List<Zona>, pulso: Int?) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val ancho = maxWidth
        Column {
            if (pulso != null) {
                val desde = zonas.first().desde
                val hasta = zonas.last().hasta
                val fraccion = ((pulso - desde).toFloat() / (hasta - desde)).coerceIn(0f, 1f)
                val x = (ancho * fraccion - 6.dp).coerceIn(0.dp, ancho - 12.dp)
                Text("▼", Modifier.offset(x = x), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
                zonas.forEach { z -> Box(Modifier.weight(1f).fillMaxHeight().background(colorZona(z.numero))) }
            }
            Row(Modifier.fillMaxWidth()) {
                zonas.forEach { z ->
                    Text("Z${z.numero}\n${z.desde}", Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun FilaZona(z: Zona, resaltada: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(colorZona(z.numero)))
        Column(Modifier.weight(1f)) {
            Text("Zona ${z.numero} · ${z.nombre}" + if (resaltada) "  ← tu último pulso" else "",
                fontWeight = if (resaltada) FontWeight.Bold else FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text("${z.porcentajeDesde}–${z.porcentajeHasta} % del máximo · ${z.para}", style = MaterialTheme.typography.bodySmall)
        }
        Text("${z.desde}–${z.hasta}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------------------------------------- uso diario

@Composable
private fun UsarElReloj(reloj: Reloj, listo: Boolean, musica: Boolean) {
    Tarjeta("Usar el reloj") {
        if (!listo) Text("Conecta el reloj para usar estos botones.", style = MaterialTheme.typography.bodySmall)
        Text("¿No encuentras el reloj? Hazlo vibrar.", style = MaterialTheme.typography.bodyMedium)
        FlowRow2 {
            Button(onClick = reloj::vibrar, enabled = listo) { Text("Vibrar") }
            Button(onClick = { reloj.vibrarDurante(5) }, enabled = listo) { Text("Vibrar 5 s") }
        }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Poner en el reloj la hora del celular.", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = reloj::sincronizarHora, enabled = listo) { Text("Poner la hora") }
        }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Botones de música", fontWeight = FontWeight.SemiBold)
                Text("En la pantalla de música del reloj, «anterior», «siguiente» y «pausa» controlan la música del celular.",
                    style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = musica, onCheckedChange = reloj::cambiarMusica)
        }
    }
}

// ---------------------------------------------------------------------------------------------- avanzadas

@Composable
private fun CabeceraAvanzadas(abiertas: Boolean, cambiar: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = cambiar),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Herramientas avanzadas (para Claude)", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(if (abiertas) "▲ Cerrar" else "▼ Abrir", color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.labelLarge)
            }
            Text("Sirve para que Claude pruebe órdenes nuevas en el reloj y vea lo que responde; no necesitas usarlo. " +
                "Aquí están el mensaje y la llamada de prueba, el clima a mano, las órdenes en código y la consola.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun PruebasAvanzadas(reloj: Reloj, listo: Boolean) {
    var texto by rememberSaveable { mutableStateOf("Hola desde Los Enamorados ★") }
    var icono by rememberSaveable { mutableStateOf(IconoApp.SMS) }
    var menuIcono by remember { mutableStateOf(false) }
    var quien by rememberSaveable { mutableStateOf("Mamá") }

    Tarjeta("Mensaje y llamada de prueba") {
        Text("Comprueban que el reloj muestra avisos. Los avisos reales del celular todavía no se reenvían.",
            style = MaterialTheme.typography.bodySmall)
        val bytes = Comandos.textoParaReloj(texto, Comandos.MAX_BYTES_MENSAJE).size
        OutlinedTextField(texto, { texto = it }, label = { Text("Mensaje") }, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("${texto.length} de ~${Comandos.CARACTERES_EN_PANTALLA} caracteres que caben en pantalla · $bytes bytes · sin emojis") })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                TextButton(onClick = { menuIcono = true }) { Text("Ícono: ${icono.etiqueta}") }
                DropdownMenu(expanded = menuIcono, onDismissRequest = { menuIcono = false }) {
                    IconoApp.entries.forEach { i -> DropdownMenuItem(text = { Text(i.etiqueta) }, onClick = { icono = i; menuIcono = false }) }
                }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = { reloj.mensaje(texto, icono) }, enabled = listo && texto.isNotBlank()) { Text("Enviar mensaje") }
        }

        HorizontalDivider()
        OutlinedTextField(quien, { quien = it }, label = { Text("Llamada de prueba: quién llama") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { reloj.lanzar("llamada", Comandos.llamada(quien, true)) }, enabled = listo) { Text("Simular llamada") }
            OutlinedButton(onClick = { reloj.lanzar("colgar", Comandos.llamada(quien, false)) }, enabled = listo) { Text("Colgar") }
        }
    }
}

@Composable
private fun ClimaAMano(reloj: Reloj, listo: Boolean) {
    var clima by rememberSaveable { mutableStateOf(Clima.NUBLADO) }
    var menuClima by remember { mutableStateOf(false) }
    var temperatura by rememberSaveable { mutableStateOf("18") }
    var ciudad by rememberSaveable { mutableStateOf("Mi ciudad") }
    Tarjeta("Clima a mano") {
        Text("Envía un clima inventado para ver cómo lo muestra el reloj. El clima real ya se envía solo desde la tarjeta de arriba.",
            style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                TextButton(onClick = { menuClima = true }) { Text(clima.etiqueta) }
                DropdownMenu(expanded = menuClima, onDismissRequest = { menuClima = false }) {
                    Clima.entries.forEach { c -> DropdownMenuItem(text = { Text(c.etiqueta) }, onClick = { clima = c; menuClima = false }) }
                }
            }
            OutlinedTextField(temperatura, { temperatura = it.filter { ch -> ch.isDigit() || ch == '-' }.take(3) }, label = { Text("°C") },
                singleLine = true, modifier = Modifier.width(80.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(ciudad, { ciudad = it }, label = { Text("Ciudad") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Button(onClick = {
            val t = temperatura.toIntOrNull() ?: 0
            val c = Calendar.getInstance()
            reloj.lanzar("clima a mano", Comandos.clima(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), clima, t - 4, t + 4, t, ciudad))
        }, enabled = listo) { Text("Enviar clima a mano") }
    }
}

@Composable
private fun OrdenEnCodigo(reloj: Reloj, listo: Boolean) {
    var categoria by rememberSaveable { mutableStateOf("02") }
    var sub by rememberSaveable { mutableStateOf("0B") }
    var datos by rememberSaveable { mutableStateOf("01") }
    Tarjeta("Enviar una orden en código (avanzado)") {
        Text("Cada cosa que el reloj sabe hacer (vibrar, poner la hora…) es una orden: unos pocos números en código " +
            "hexadecimal. Aquí Claude te puede pedir que envíes una orden que todavía no tiene botón, para ver si el reloj la " +
            "entiende; lo que responda aparece en la consola. Si nadie te pidió un código, no lo necesitas. " +
            "(Por ejemplo 02 · 0B · 01 hace vibrar el reloj.)", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(categoria, { categoria = it.uppercase().take(2) }, label = { Text("Categoría") }, singleLine = true, modifier = Modifier.width(110.dp))
            OutlinedTextField(sub, { sub = it.uppercase().take(2) }, label = { Text("Subcomando") }, singleLine = true, modifier = Modifier.width(120.dp))
        }
        OutlinedTextField(datos, { datos = it.uppercase() }, label = { Text("Datos") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        val cat = categoria.toIntOrNull(16)
        val sc = sub.toIntOrNull(16)
        val bytes = Protocolo.deHex(datos)
        val trama = if (cat != null && sc != null && bytes != null) Protocolo.trama(cat, sc, bytes) else null
        Text(trama?.let { "Se enviará: ${Protocolo.hex(it)}  (${Protocolo.nombre(cat!!, sc!!)})" } ?: "Revisa los valores hexadecimales.",
            fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Button(onClick = { trama?.let { reloj.lanzar("a mano ${Protocolo.nombre(cat!!, sc!!)}", it) } }, enabled = listo && trama != null) { Text("Enviar orden") }
    }
}

@Composable
private fun DatosSinDescifrar(resultados: List<ResultadoMedicion>, borrar: () -> Unit) {
    val hora = remember { SimpleDateFormat("d MMM HH:mm", ES) }
    Tarjeta("Datos de mediciones sin descifrar") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Lo que mandó el reloj durante una medición y la app aún no entiende (por ejemplo el oxígeno).",
                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = borrar) { Text("Borrar") }
        }
        resultados.take(12).forEach { r ->
            Column(Modifier.padding(vertical = 2.dp)) {
                Text("${hora.format(Date(r.momento))} · ${r.tipo} · evento %02X/%02X".format(r.categoria, r.sub), fontSize = 12.sp)
                Text(r.decimales, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(r.hex, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun Consola(lineas: List<LineaConsola>, limpiar: () -> Unit, copiar: () -> Unit) {
    val formato = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    Tarjeta("Consola") {
        Text("Todo lo que la app envía al reloj (→) y lo que el reloj responde (←). Si algo no funciona, pulsa «Copiar» y pégaselo a Claude.",
            style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = copiar) { Text("Copiar") }
            TextButton(onClick = limpiar) { Text("Limpiar") }
        }
        if (lineas.isEmpty()) Text("Sin actividad.", style = MaterialTheme.typography.bodySmall)
        lineas.takeLast(120).asReversed().forEach { l ->
            Column(Modifier.padding(vertical = 2.dp)) {
                Text("${formato.format(Date(l.momento))} ${l.direccion} ${l.texto}", fontSize = 12.sp,
                    color = when (l.direccion) {
                        '→' -> MaterialTheme.colorScheme.primary
                        '←' -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurface
                    })
                if (l.hex.isNotEmpty()) Text(l.hex, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
    }
}
