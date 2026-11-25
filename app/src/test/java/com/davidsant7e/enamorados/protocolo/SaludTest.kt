package com.davidsant7e.enamorados.protocolo

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bytes reales del registro del 24 nov 2026 (sincronización de HryFine). */
class SaludTest {
    private fun h(s: String) = Protocolo.deHex(s)!!

    @Test fun `actividad del dia desde el evento 05-0C real`() {
        val e = Lector().agregar(h("DF 00 11 1E 05 01 0C 00 0C 00 00 07 6A 00 00 04 99 00 00 B3 4F")).single() as Recibido.Evento
        assertTrue(e.sumaBien)
        val a = Salud.actividadDe(e.categoria, e.sub, e.datos)!!
        assertEquals(1898L, a.pasos)
        assertEquals(1177L, a.metros)
        assertEquals(45903L, a.calorias)
        assertEquals(1.177, a.kilometros, 1e-9)
        assertEquals(45.903, a.kcal, 1e-9)
    }

    @Test fun `otros eventos no son actividad`() {
        assertNull(Salud.actividadDe(0x05, 0x07, h("01")))
        assertNull(Salud.actividadDe(0x05, 0x0C, h("00 00 07")))
        assertNull(Salud.actividadDe(0x0C, 0x0C, ByteArray(12)))
    }

    @Test fun `fechas de dos bytes`() {
        assertEquals(Triple(2026, 11, 22), Salud.fecha(0x35, 0x76))
        assertEquals(Triple(2026, 11, 23), Salud.fecha(0x35, 0x77))
        assertEquals(Triple(2026, 11, 24), Salud.fecha(0x35, 0x78))
        assertNull(Salud.fecha(0x00, 0x00))
    }

    @Test fun `tramas de medicion y de pedir actividad`() {
        // 02/0E 01 (empezar presión) y 02/0E 00 (detener), con su suma.
        assertArrayEquals(h("DF 00 06 F8 02 01 0E 00 01 01"), Salud.medir(Salud.Medicion.PRESION, true))
        assertArrayEquals(h("DF 00 06 F7 02 01 0E 00 01 00"), Salud.medir(Salud.Medicion.PRESION, false))
        assertArrayEquals(h("DF 00 05 F0 05 01 06 00 00"), Salud.pedirActividad())
        listOf(Salud.medir(Salud.Medicion.PULSO, true), Salud.medir(Salud.Medicion.OXIGENO, true)).forEach {
            assertEquals(Protocolo.suma(it), it[3].toInt() and 0xFF)
        }
    }

    @Test fun `decimales y ruido`() {
        assertEquals("0 120 80 255", Salud.decimales(h("00 78 50 FF")))
        assertTrue(Salud.esRuido(0x0C, 0x02))
        assertTrue(Salud.esRuido(0x05, 0x0C))
        assertFalse(Salud.esRuido(0x0E, 0x04))
    }

    @Test fun `pulso y presion reales del 24 nov`() {
        val pulso = Lector().agregar(h("DF 00 11 BB 05 01 04 00 0C 35 78 00 01 00 01 22 91 00 00 00 53")).single() as Recibido.Evento
        assertTrue(pulso.sumaBien)
        val p = Salud.lecturasDe(pulso.categoria, pulso.sub, pulso.datos).single()
        assertEquals(Salud.Medicion.PULSO, p.tipo)
        assertEquals(Triple(2026, 11, 24), Triple(p.anio, p.mes, p.dia))
        assertEquals("20:39", p.hora)
        assertEquals("83 lpm", p.texto)

        val presion = Lector().agregar(h("DF 00 11 5C 05 01 05 00 0C 35 78 00 01 00 01 22 CB 00 00 70 49")).single() as Recibido.Evento
        assertTrue(presion.sumaBien)
        val b = Salud.lecturasDe(presion.categoria, presion.sub, presion.datos).single()
        assertEquals("112/73 mmHg", b.texto)
        assertEquals("20:40", b.hora)
        assertTrue(Salud.esRuido(0x05, 0x04))
    }

    @Test fun `varias lecturas y datos que no cuadran`() {
        val dos = h("35 78 00 02 00 01 22 91 00 00 00 53 00 01 23 00 00 00 00 4B")
        assertEquals(listOf("83 lpm", "75 lpm"), Salud.lecturasDe(0x05, 0x04, dos).map { it.texto })
        assertTrue(Salud.lecturasDe(0x05, 0x04, h("35 78 00 02 00 01 22 91 00 00 00 53")).isEmpty()) // falta la 2.ª
        assertTrue(Salud.lecturasDe(0x05, 0x05, h("35 78 00 01 00 01 22 CB 00 00 00 00")).isEmpty()) // sin valores
        assertTrue(Salud.lecturasDe(0x05, 0x0C, ByteArray(12)).isEmpty())
    }
}
