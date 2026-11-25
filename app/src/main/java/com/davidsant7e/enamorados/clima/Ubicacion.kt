package com.davidsant7e.enamorados.clima

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Dónde está el celular, para el clima automático. Solo ubicación aproximada (permiso «ubicación aproximada»):
 * para el clima basta saber la ciudad. Las coordenadas se redondean a 2 decimales (~1 km) antes de usarlas y
 * no se guardan en el registro.
 */
object Ubicacion {
    const val PERMISO = Manifest.permission.ACCESS_COARSE_LOCATION
    /** Una ubicación conocida más nueva que esto se usa sin pedir otra. */
    private const val RECIENTE = 60 * 60 * 1000L

    fun tienePermiso(ctx: Context) = ctx.checkSelfPermission(PERMISO) == PackageManager.PERMISSION_GRANTED

    /** El lugar actual con nombre de ciudad, o null si no hay permiso o el celular no sabe dónde está. */
    suspend fun lugarActual(ctx: Context): OpenMeteo.Lugar? {
        if (!tienePermiso(ctx)) return null
        val ubicacion = posicion(ctx) ?: return null
        val lat = redondear(ubicacion.latitude)
        val lon = redondear(ubicacion.longitude)
        val (ciudad, region, pais) = nombre(ctx, lat, lon)
        return OpenMeteo.Lugar(ciudad ?: "Mi ubicación", region, pais, lat, lon)
    }

    fun redondear(grados: Double): Double = Math.round(grados * 100) / 100.0

    @SuppressLint("MissingPermission")
    private suspend fun posicion(ctx: Context): Location? {
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val proveedores = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) || it == LocationManager.PASSIVE_PROVIDER }.getOrDefault(false) }
        val conocida = proveedores.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (conocida != null && System.currentTimeMillis() - conocida.time < RECIENTE) return conocida
        if (Build.VERSION.SDK_INT >= 30) {
            val proveedor = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER)
                .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            if (proveedor != null) {
                val nueva = withTimeoutOrNull(15_000) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        val cancelar = CancellationSignal()
                        cont.invokeOnCancellation { cancelar.cancel() }
                        runCatching {
                            lm.getCurrentLocation(proveedor, cancelar, ctx.mainExecutor) { l -> if (cont.isActive) cont.resume(l) }
                        }.onFailure { if (cont.isActive) cont.resume(null) }
                    }
                }
                if (nueva != null) return nueva
            }
        }
        return conocida // vieja, pero mejor que nada
    }

    /** (ciudad, región, país) con el Geocoder de Android; nulls si no hay servicio o internet. */
    @Suppress("DEPRECATION")
    private suspend fun nombre(ctx: Context, lat: Double, lon: Double): Triple<String?, String?, String?> {
        if (!Geocoder.isPresent()) return Triple(null, null, null)
        val geocoder = Geocoder(ctx, Locale.forLanguageTag("es"))
        val direccion = if (Build.VERSION.SDK_INT >= 33) {
            withTimeoutOrNull(10_000) {
                suspendCancellableCoroutine { cont ->
                    runCatching {
                        geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(lista: MutableList<android.location.Address>) { if (cont.isActive) cont.resume(lista.firstOrNull()) }
                            override fun onError(mensaje: String?) { if (cont.isActive) cont.resume(null) }
                        })
                    }.onFailure { if (cont.isActive) cont.resume(null) }
                }
            }
        } else {
            withContext(Dispatchers.IO) { runCatching { geocoder.getFromLocation(lat, lon, 1)?.firstOrNull() }.getOrNull() }
        }
        val ciudad = direccion?.locality ?: direccion?.subAdminArea ?: direccion?.adminArea
        return Triple(ciudad, direccion?.adminArea, direccion?.countryName)
    }
}
