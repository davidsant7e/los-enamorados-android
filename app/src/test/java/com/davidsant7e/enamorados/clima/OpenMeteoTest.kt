package com.davidsant7e.enamorados.clima

import com.davidsant7e.enamorados.protocolo.Clima
import com.davidsant7e.enamorados.protocolo.Comandos
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Respuestas con la forma documentada en open-meteo.com/en/docs y open-meteo.com/en/docs/geocoding-api. */
class OpenMeteoTest {

    private val busqueda = """
        {"results":[
          {"id":3688689,"name":"Bogotá","latitude":4.60971,"longitude":-74.08175,"elevation":2582.0,
           "feature_code":"PPLC","country_code":"CO","timezone":"America/Bogota","population":7674366,
           "country_id":3686110,"country":"Colombia","admin1":"Bogotá D.C.","admin1_id":3688685},
          {"id":3688690,"name":"Bogotá","latitude":-19.9,"longitude":-63.3,"country":"Bolivia"}
        ],"generationtime_ms":0.92}
    """.trimIndent()

    private val pronostico = """
        {"latitude":4.625,"longitude":-74.125,"generationtime_ms":0.05,"utc_offset_seconds":-18000,
         "timezone":"America/Bogota","timezone_abbreviation":"GMT-5","elevation":2582.0,
         "current_units":{"time":"iso8601","interval":"seconds","temperature_2m":"°C","weather_code":"wmo code"},
         "current":{"time":"2026-11-24T20:45","interval":900,"temperature_2m":14.6,"weather_code":61},
         "daily_units":{"time":"iso8601","temperature_2m_max":"°C","temperature_2m_min":"°C","weather_code":"wmo code"},
         "daily":{"time":["2026-11-24","2026-11-25"],"temperature_2m_max":[19.4,20.1],"temperature_2m_min":[8.6,9.0],
                  "weather_code":[80,3]}}
    """.trimIndent()

    @Test fun `direcciones de Open-Meteo`() {
        assertEquals("https://geocoding-api.open-meteo.com/v1/search?name=Bogot%C3%A1&count=5&language=es", OpenMeteo.urlBusqueda(" Bogotá "))
        assertEquals("https://geocoding-api.open-meteo.com/v1/search?name=San+Jos%C3%A9&count=5&language=es", OpenMeteo.urlBusqueda("San José"))
        assertEquals("https://api.open-meteo.com/v1/forecast?latitude=4.6097&longitude=-74.0818" +
            "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min,weather_code&timezone=auto",
            OpenMeteo.urlPronostico(4.60971, -74.08175))
    }

    @Test fun `lugares de la busqueda`() {
        val l = OpenMeteo.lugaresDe(busqueda)
        assertEquals(2, l.size)
        assertEquals("Bogotá, Bogotá D.C., Colombia", l[0].descripcion)
        assertEquals(4.60971, l[0].latitud, 1e-9)
        assertEquals(-74.08175, l[0].longitud, 1e-9)
        assertEquals("Bogotá, Bolivia", l[1].descripcion)
        assertEquals(emptyList<OpenMeteo.Lugar>(), OpenMeteo.lugaresDe("""{"generationtime_ms":0.3}"""))
    }

    @Test fun `clima de hoy del pronostico`() {
        val c = OpenMeteo.climaDe(pronostico)!!
        assertEquals(14.6, c.actual, 1e-9)
        assertEquals(61, c.codigo)
        assertEquals(8.6, c.minima, 1e-9)
        assertEquals(19.4, c.maxima, 1e-9)
        assertEquals("2026-11-24T20:45", c.hora)
        assertNull(OpenMeteo.climaDe("""{"error":true,"reason":"Latitude must be in range"}"""))
    }

    @Test fun `la actual nunca queda fuera de minima y maxima`() {
        val c = OpenMeteo.climaDe("""{"current":{"temperature_2m":21.0,"weather_code":0},"daily":{"temperature_2m_max":[20.0],"temperature_2m_min":[9.0]}}""")!!
        assertEquals(21.0, c.maxima, 1e-9)
        assertEquals(9.0, c.minima, 1e-9)
    }

    @Test fun `codigos WMO al clima del reloj`() {
        val esperado = mapOf(
            0 to Clima.SOLEADO, 1 to Clima.SOLEADO, 2 to Clima.NUBLADO, 3 to Clima.CUBIERTO,
            45 to Clima.NIEBLA, 48 to Clima.NIEBLA,
            51 to Clima.LLUVIA, 55 to Clima.LLUVIA, 57 to Clima.LLUVIA, 61 to Clima.LLUVIA, 65 to Clima.LLUVIA,
            67 to Clima.LLUVIA, 80 to Clima.LLUVIA, 82 to Clima.LLUVIA,
            71 to Clima.NIEVE, 77 to Clima.NIEVE, 85 to Clima.NIEVE, 86 to Clima.NIEVE,
            95 to Clima.TORMENTA, 96 to Clima.TORMENTA, 99 to Clima.TORMENTA,
            42 to Clima.NUBLADO, -1 to Clima.NUBLADO,
        )
        esperado.forEach { (codigo, clima) -> assertEquals("código $codigo", clima, OpenMeteo.climaReloj(codigo)) }
    }

    @Test fun `descripcion y emoji`() {
        assertEquals("Despejado", OpenMeteo.descripcion(0))
        assertEquals("Lluvia débil", OpenMeteo.descripcion(61))
        assertEquals("Tormenta con granizo", OpenMeteo.descripcion(99))
        assertEquals("☀️", OpenMeteo.emoji(0))
        assertEquals("🌧️", OpenMeteo.emoji(63))
        assertEquals("⛈️", OpenMeteo.emoji(95))
    }

    @Test fun `trama del reloj igual a la del clima manual`() {
        val c = OpenMeteo.climaDe(pronostico)!!
        // 14,6 → 15; 8,6 → 9; 19,4 → 19; código 61 → lluvia
        assertArrayEquals(Comandos.clima(2026, 11, 24, Clima.LLUVIA, 9, 19, 15, "Bogotá"),
            OpenMeteo.tramaReloj(c, "Bogotá", 2026, 11, 24))
        val bajoCero = OpenMeteo.ClimaHoy(-2.4, 71, -6.5, 1.2, null)
        val t = OpenMeteo.tramaReloj(bajoCero, "Oslo", 2026, 12, 1)
        assertTrue(t.size > 17)
        assertArrayEquals(Comandos.clima(2026, 12, 1, Clima.NIEVE, -6, 1, -2, "Oslo"), t)
    }
}
