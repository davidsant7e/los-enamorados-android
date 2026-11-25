package com.davidsant7e.enamorados.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.davidsant7e.enamorados.ble.Buscador
import com.davidsant7e.enamorados.ble.Encontrado
import com.davidsant7e.enamorados.ble.Reloj
import com.davidsant7e.enamorados.clima.ServicioClima
import com.davidsant7e.enamorados.registro.Registro
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** La conexión vive mientras viva el proceso (girar la pantalla no la corta). */
object Piezas {
    @Volatile private var reloj: Reloj? = null
    @Volatile private var buscador: Buscador? = null
    @Volatile private var clima: ServicioClima? = null
    @Volatile private var ajustes: Ajustes? = null
    fun reloj(a: android.content.Context) = reloj ?: synchronized(this) { reloj ?: Reloj(a).also { reloj = it } }
    fun buscador(a: android.content.Context) = buscador ?: synchronized(this) { buscador ?: Buscador(a).also { buscador = it } }
    fun clima(a: android.content.Context) = clima ?: synchronized(this) { clima ?: ServicioClima(a, reloj(a)).also { clima = it } }
    fun ajustes(a: android.content.Context) = ajustes ?: synchronized(this) { ajustes ?: Ajustes(a).also { ajustes = it } }
}

class MainActivity : ComponentActivity() {
    private val reloj by lazy { Piezas.reloj(this) }
    private val buscador by lazy { Piezas.buscador(this) }
    private val clima by lazy { Piezas.clima(this) }
    private val ajustes by lazy { Piezas.ajustes(this) }
    private var permisos by mutableStateOf(false)
    private var eligiendo by mutableStateOf(false)
    private var cercanos by mutableStateOf<List<Encontrado>>(emptyList())

    private val pedirPermisos = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permisos = tienePermisos()
        Registro.evento("PERMISOS", "respuesta", "concedidos" to permisos)
        if (permisos) alListo() else aviso("Sin el permiso de «Dispositivos cercanos» la app no puede hablar con el reloj.")
    }

    private val pedirUbicacion = registerForActivityResult(ActivityResultContracts.RequestPermission()) { dado ->
        Registro.evento("PERMISOS", "ubicacion", "concedido" to dado)
        if (dado) clima.usarUbicacion()
        else aviso("Sin el permiso de ubicación el clima sigue con la ciudad elegida. Puedes darlo en Ajustes → Apps → Los Enamorados → Permisos.")
    }

    private val encenderBluetooth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (bluetoothEncendido()) reloj.conectar()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permisos = tienePermisos()
        setContent {
            TemaEnamorados {
                Enamorados(
                    reloj = reloj,
                    buscador = buscador,
                    clima = clima,
                    ajustes = ajustes,
                    permisos = permisos,
                    eligiendo = eligiendo,
                    cercanos = cercanos,
                    acciones = Acciones(
                        pedirPermisos = ::pedir,
                        elegir = { abrirEleccion() },
                        cancelarEleccion = { eligiendo = false; buscador.detener() },
                        elegido = { direccion, nombre -> eligiendo = false; buscador.detener(); reloj.elegir(direccion, nombre) },
                        buscar = { buscador.buscar() },
                        conectar = { if (bluetoothEncendido()) reloj.conectar() else pedirBluetooth() },
                        copiarConsola = ::copiarConsola,
                        usarUbicacion = {
                            if (com.davidsant7e.enamorados.clima.Ubicacion.tienePermiso(this)) clima.usarUbicacion()
                            else pedirUbicacion.launch(com.davidsant7e.enamorados.clima.Ubicacion.PERMISO)
                        },
                    ),
                )
            }
        }
        if (savedInstanceState == null) {
            if (!permisos) pedir() else alListo()
        }
    }

    override fun onStart() {
        super.onStart()
        clima.actualizarSiHaceFalta()
    }

    private fun alListo() {
        if (reloj.direccionGuardada() == null) abrirEleccion()
        else if (bluetoothEncendido()) reloj.conectarSiHaceFalta() else pedirBluetooth()
    }

    private fun abrirEleccion() {
        eligiendo = true
        cercanos = (reloj.conectadosAhora().map { Encontrado(it.address, nombre(it), null, false, "conectado ahora") } +
            reloj.emparejados().map { Encontrado(it.address, nombre(it), null, false, "emparejado") })
            .distinctBy { it.direccion }
        if (permisos && bluetoothEncendido()) buscador.buscar()
    }

    @SuppressLint("MissingPermission")
    private fun nombre(d: android.bluetooth.BluetoothDevice): String? = runCatching { d.name }.getOrNull()

    private fun permisosNecesarios(): Array<String> =
        if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun tienePermisos() = permisosNecesarios().all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

    private fun pedir() = pedirPermisos.launch(permisosNecesarios())

    private fun bluetoothEncendido() = getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    private fun pedirBluetooth() {
        runCatching { encenderBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
            .onFailure { aviso("Enciende el Bluetooth del celular.") }
    }

    private fun copiarConsola() {
        val formato = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val texto = reloj.consola.value.joinToString("\n") { l ->
            "${formato.format(Date(l.momento))} ${l.direccion} ${l.texto}" + if (l.hex.isNotEmpty()) "  [${l.hex}]" else ""
        }
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Consola de Los Enamorados", texto))
        aviso("Consola copiada (${reloj.consola.value.size} líneas).")
    }

    private fun aviso(texto: String) = Toast.makeText(this, texto, Toast.LENGTH_LONG).show()
}
