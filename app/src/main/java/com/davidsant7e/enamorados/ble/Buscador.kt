package com.davidsant7e.enamorados.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.davidsant7e.enamorados.protocolo.Protocolo
import com.davidsant7e.enamorados.registro.Registro
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Un aparato encontrado. `canalHryFine`: anuncia el servicio 6E400001… (casi seguro es el reloj). */
data class Encontrado(val direccion: String, val nombre: String?, val senal: Int?, val canalHryFine: Boolean, val origen: String)

/**
 * Busca relojes cerca durante unos segundos. Si HryFine ya está conectado al reloj, el reloj suele dejar de
 * anunciarse: por eso la pantalla muestra también los aparatos «conectados ahora» ([Reloj.conectadosAhora]).
 */
@SuppressLint("MissingPermission")
class Buscador(contexto: Context) {
    private val manager = contexto.applicationContext.getSystemService(BluetoothManager::class.java)
    private val principal = Handler(Looper.getMainLooper())
    private val _encontrados = MutableStateFlow<List<Encontrado>>(emptyList())
    val encontrados: StateFlow<List<Encontrado>> = _encontrados.asStateFlow()
    private val _buscando = MutableStateFlow(false)
    val buscando: StateFlow<Boolean> = _buscando.asStateFlow()

    private val callback = object : ScanCallback() {
        override fun onScanResult(tipo: Int, r: ScanResult) {
            val uuids = r.scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()
            val nuevo = Encontrado(
                direccion = r.device.address,
                nombre = r.scanRecord?.deviceName ?: runCatching { r.device.name }.getOrNull(),
                senal = r.rssi,
                canalHryFine = Protocolo.SERVICIO in uuids || Protocolo.SERVICIO_NORDIC in uuids,
                origen = "cerca",
            )
            _encontrados.update { lista -> (lista.filter { it.direccion != nuevo.direccion } + nuevo).sortedWith(ORDEN) }
        }

        override fun onScanFailed(codigo: Int) {
            _buscando.value = false
            Registro.evento("BUSQUEDA", "error", "codigo" to codigo)
        }
    }

    fun buscar(segundos: Int = 12) {
        val escaner = manager?.adapter?.bluetoothLeScanner ?: return
        detener()
        _encontrados.value = emptyList()
        _buscando.value = true
        Registro.evento("BUSQUEDA", "inicio")
        val ajustes = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        runCatching { escaner.startScan(null, ajustes, callback) }.onFailure { _buscando.value = false; Registro.error("BUSQUEDA", it) }
        principal.postDelayed({ detener() }, segundos * 1000L)
    }

    fun detener() {
        principal.removeCallbacksAndMessages(null)
        if (_buscando.value) {
            runCatching { manager?.adapter?.bluetoothLeScanner?.stopScan(callback) }
            Registro.evento("BUSQUEDA", "fin", "encontrados" to _encontrados.value.size)
        }
        _buscando.value = false
    }

    companion object {
        /** Primero los que tienen el canal de HryFine, luego por señal. */
        val ORDEN: Comparator<Encontrado> = compareByDescending<Encontrado> { it.canalHryFine }.thenByDescending { it.senal ?: -200 }
    }
}
