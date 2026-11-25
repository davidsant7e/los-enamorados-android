package com.davidsant7e.enamorados.clima

import com.davidsant7e.enamorados.protocolo.Clima
import com.davidsant7e.enamorados.protocolo.Comandos
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Clima de Open-Meteo (open-meteo.com, gratis y sin clave). Aquí solo lo puro: las direcciones, la lectura de las
 * respuestas y la traducción de los códigos WMO a lo que entiende el reloj. La descarga está en `ServicioClima`.
 */
object OpenMeteo {
    const val CIUDAD_POR_DEFECTO = "Bogotá"

    data class Lugar(val nombre: String, val region: String?, val pais: String?, val latitud: Double, val longitud: Double) {
        /** «Bogotá, Bogotá D.C., Colombia» sin repetir. */
        val descripcion: String get() = listOfNotNull(nombre, region, pais).distinct().joinToString(", ")
    }

    data class ClimaHoy(val actual: Double, val codigo: Int, val minima: Double, val maxima: Double, val hora: String?)

    fun urlBusqueda(ciudad: String): String =
        "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(ciudad.trim(), "UTF-8")}&count=5&language=es"

    fun urlPronostico(latitud: Double, longitud: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f".format(Locale.US, latitud, longitud) +
            "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min,weather_code&timezone=auto"

    /** Respuesta de la búsqueda de ciudades → lugares (lista vacía si no encontró nada). */
    fun lugaresDe(json: String): List<Lugar> {
        val raiz = Json.leer(json) as? Map<*, *> ?: return emptyList()
        val resultados = raiz["results"] as? List<*> ?: return emptyList()
        return resultados.mapNotNull { r ->
            val m = r as? Map<*, *> ?: return@mapNotNull null
            val nombre = m["name"] as? String ?: return@mapNotNull null
            val lat = m["latitude"] as? Double ?: return@mapNotNull null
            val lon = m["longitude"] as? Double ?: return@mapNotNull null
            Lugar(nombre, m["admin1"] as? String, m["country"] as? String, lat, lon)
        }
    }

    /** Respuesta del pronóstico → clima de ahora con mínima y máxima de hoy (el primer día), o null si no cuadra. */
    fun climaDe(json: String): ClimaHoy? {
        val raiz = Json.leer(json) as? Map<*, *> ?: return null
        val actual = raiz["current"] as? Map<*, *> ?: return null
        val diario = raiz["daily"] as? Map<*, *>
        val temp = actual["temperature_2m"] as? Double ?: return null
        val codigo = (actual["weather_code"] as? Double)?.toInt()
            ?: ((diario?.get("weather_code") as? List<*>)?.firstOrNull() as? Double)?.toInt() ?: return null
        val maxima = (diario?.get("temperature_2m_max") as? List<*>)?.firstOrNull() as? Double ?: temp
        val minima = (diario?.get("temperature_2m_min") as? List<*>)?.firstOrNull() as? Double ?: temp
        return ClimaHoy(temp, codigo, minOf(minima, temp), maxOf(maxima, temp), actual["time"] as? String)
    }

    /**
     * Código del tiempo WMO (el que usa Open-Meteo) → estado que entiende el reloj.
     * 0–1 despejado, 2 parcialmente nublado, 3 cubierto, 45/48 niebla, 51–67 y 80–82 lluvia o llovizna,
     * 71–77 y 85–86 nieve, 95–99 tormenta.
     */
    fun climaReloj(codigo: Int): Clima = when (codigo) {
        0, 1 -> Clima.SOLEADO
        2 -> Clima.NUBLADO
        3 -> Clima.CUBIERTO
        45, 48 -> Clima.NIEBLA
        in 51..67, in 80..82 -> Clima.LLUVIA
        in 71..77, 85, 86 -> Clima.NIEVE
        in 95..99 -> Clima.TORMENTA
        else -> Clima.NUBLADO
    }

    fun descripcion(codigo: Int): String = when (codigo) {
        0 -> "Despejado"
        1 -> "Mayormente despejado"
        2 -> "Parcialmente nublado"
        3 -> "Cubierto"
        45, 48 -> "Niebla"
        51, 53, 55 -> "Llovizna"
        56, 57 -> "Llovizna helada"
        61 -> "Lluvia débil"
        63 -> "Lluvia"
        65 -> "Lluvia fuerte"
        66, 67 -> "Lluvia helada"
        71, 73, 75, 77 -> "Nieve"
        80, 81 -> "Chubascos"
        82 -> "Chubascos fuertes"
        85, 86 -> "Chubascos de nieve"
        95 -> "Tormenta"
        96, 99 -> "Tormenta con granizo"
        else -> "Nublado"
    }

    fun emoji(codigo: Int): String = when (codigo) {
        0 -> "☀️"
        1 -> "🌤️"
        2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 56, 57, 80, 81, 82 -> "🌦️"
        in 61..67 -> "🌧️"
        in 71..77, 85, 86 -> "❄️"
        in 95..99 -> "⛈️"
        else -> "☁️"
    }

    /** La trama 02/1D lista para el reloj (temperaturas redondeadas a grados enteros). */
    fun tramaReloj(c: ClimaHoy, ciudad: String, anio: Int, mes: Int, dia: Int): ByteArray =
        Comandos.clima(anio, mes, dia, climaReloj(c.codigo), c.minima.roundToInt(), c.maxima.roundToInt(), c.actual.roundToInt(), ciudad)
}
