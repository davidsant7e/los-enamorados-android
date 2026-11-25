package com.davidsant7e.enamorados.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davidsant7e.enamorados.ble.Buscador
import com.davidsant7e.enamorados.ble.Encontrado
import com.davidsant7e.enamorados.ble.ActividadVista
import com.davidsant7e.enamorados.ble.Estado
import com.davidsant7e.enamorados.ble.Midiendo
import com.davidsant7e.enamorados.ble.ResultadoMedicion
import com.davidsant7e.enamorados.ble.LineaConsola
import com.davidsant7e.enamorados.ble.Reloj
import com.davidsant7e.enamorados.protocolo.Clima
import com.davidsant7e.enamorados.protocolo.Comandos
import com.davidsant7e.enamorados.protocolo.IconoApp
import com.davidsant7e.enamorados.protocolo.Protocolo
import com.davidsant7e.enamorados.protocolo.Salud
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class Acciones(
    val pedirPermisos: () -> Unit,
    val elegir: () -> Unit,
    val cancelarEleccion: () -> Unit,
    val elegido: (String, String?) -> Unit,
    val buscar: () -> Unit,
    val conectar: () -> Unit,
    val copiarConsola: () -> Unit,
)

@Composable
fun Enamorados(
    reloj: Reloj,
    buscador: Buscador,
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
    val encontrados by buscador.encontrados.collectAsState()
    val buscando by buscador.buscando.collectAsState()
    val listo = estado is Estado.Listo

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Encabezado() }
            if (!permisos) item { Permisos(acciones.pedirPermisos) }
            // Sin reloj elegido siempre se muestra la elección (el estado sí lo vigila la pantalla).
            if (eligiendo || estado is Estado.SinReloj) {
                item { Eleccion(cercanos, encontrados, buscando, permisos, reloj.direccionGuardada() != null, acciones) }
            } else {
                item { TarjetaReloj(reloj, estado, bateria, info, acciones) }
                item { PanelSalud(reloj, listo, actividad, midiendo, resultados) }
                item { PanelPruebas(reloj, listo) }
                item {
                    Tarjeta("Botones de música del reloj") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("En la pantalla de música del reloj, «anterior», «siguiente» y «pausa» controlan la música del celular.",
                                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = musica, onCheckedChange = reloj::cambiarMusica)
                        }
                    }
                }
                item { ComandoAMano(reloj, listo) }
                item { Consola(consola, reloj::limpiarConsola, acciones.copiarConsola) }
            }
        }
    }
}

@Composable
private fun Encabezado() {
    Column {
        Text("VI · Los Enamorados", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Text("El celular y el reloj, conversando directo (protocolo de HryFine).",
            style = MaterialTheme.typography.bodyMedium)
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
            "en tu reloj, 41:42:CE:02:D5:6C. Si HryFine está conectado, el reloj sale en «conectado ahora».",
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
            label = { Text("O escribe la dirección") }, placeholder = { Text("41:42:CE:02:D5:6C") },
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
        Text(reloj.direccionGuardada() ?: "", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        Text(estado.texto + (bateria?.let { "  ·  batería $it %" } ?: ""), fontWeight = FontWeight.SemiBold,
            color = if (estado is Estado.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PanelSalud(reloj: Reloj, listo: Boolean, actividad: ActividadVista?, midiendo: Midiendo?, resultados: List<ResultadoMedicion>) {
    val hora = remember { SimpleDateFormat("d MMM HH:mm", Locale.forLanguageTag("es")) }
    Tarjeta("Salud") {
        // Actividad del día
        if (actividad == null) {
            Text("Todavía no hay pasos del reloj. Pulsa «Actualizar pasos» o abre HryFine: cuando sincroniza, la app también los recibe.",
                style = MaterialTheme.typography.bodySmall)
        } else {
            val a = actividad.actividad
            Text("%,d pasos".format(Locale.forLanguageTag("es"), a.pasos), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Text("%.2f km  ·  %.1f kcal (por confirmar)".format(Locale.forLanguageTag("es"), a.kilometros, a.kcal), style = MaterialTheme.typography.bodyMedium)
            Text("Actualizado ${hora.format(Date(actividad.momento))}", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = reloj::pedirActividad, enabled = listo) { Text("Actualizar pasos") }

        HorizontalDivider()
        // Mediciones
        Text("Mediciones", fontWeight = FontWeight.SemiBold)
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Salud.Medicion.entries.forEach { m -> Button(onClick = { reloj.medir(m) }, enabled = listo) { Text(m.etiqueta) } }
            }
        }
        Text("Al terminar, anota lo que muestra el reloj en pantalla: así descifro el formato del resultado. " +
            "Las mediciones de un reloj son orientativas, no sirven para diagnóstico.", style = MaterialTheme.typography.bodySmall)
        if (resultados.isNotEmpty()) {
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Lo que mandó el reloj al medir", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                TextButton(onClick = reloj::borrarResultados) { Text("Borrar") }
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PanelPruebas(reloj: Reloj, listo: Boolean) {
    var texto by rememberSaveable { mutableStateOf("Hola desde Los Enamorados ★") }
    var icono by rememberSaveable { mutableStateOf(IconoApp.SMS) }
    var menuIcono by remember { mutableStateOf(false) }
    var quien by rememberSaveable { mutableStateOf("Mamá") }
    var clima by rememberSaveable { mutableStateOf(Clima.NUBLADO) }
    var menuClima by remember { mutableStateOf(false) }
    var temperatura by rememberSaveable { mutableStateOf("18") }
    var ciudad by rememberSaveable { mutableStateOf("Bogotá") }

    Tarjeta("Probar el reloj") {
        if (!listo) Text("Conecta el reloj para usar estos botones.", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = reloj::vibrar, enabled = listo) { Text("Vibrar") }
            Button(onClick = { reloj.vibrarDurante(5) }, enabled = listo) { Text("Vibrar 5 s") }
            OutlinedButton(onClick = reloj::sincronizarHora, enabled = listo) { Text("Poner la hora") }
        }

        HorizontalDivider()
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

        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                TextButton(onClick = { menuClima = true }) { Text("Clima: ${clima.etiqueta}") }
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
            reloj.lanzar("clima", Comandos.clima(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), clima, t - 4, t + 4, t, ciudad))
        }, enabled = listo) { Text("Enviar clima") }
    }
}

@Composable
private fun ComandoAMano(reloj: Reloj, listo: Boolean) {
    var categoria by rememberSaveable { mutableStateOf("02") }
    var sub by rememberSaveable { mutableStateOf("0B") }
    var datos by rememberSaveable { mutableStateOf("01") }
    Tarjeta("Comando a mano (para explorar)") {
        Text("Arma una trama DF con su suma. Categoría y subcomando en hexadecimal; datos como «01 00 FF». " +
            "Lo que responda el reloj aparece en la consola.", style = MaterialTheme.typography.bodySmall)
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
        Button(onClick = { trama?.let { reloj.lanzar("a mano ${Protocolo.nombre(cat!!, sc!!)}", it) } }, enabled = listo && trama != null) { Text("Enviar") }
    }
}

@Composable
private fun Consola(lineas: List<LineaConsola>, limpiar: () -> Unit, copiar: () -> Unit) {
    val formato = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    Tarjeta("Consola") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = copiar) { Text("Copiar") }
            TextButton(onClick = limpiar) { Text("Limpiar") }
        }
        Text("→ enviado · ← recibido · notas. Si algo no funciona, copia la consola y pégamela.",
            style = MaterialTheme.typography.bodySmall)
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
