package com.davidsant7e.enamorados.protocolo

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Valores esperados calculados aparte con el algoritmo publicado (no con este código). */
class ProtocoloTest {
    private fun h(s: String) = Protocolo.deHex(s)!!

    @Test fun `buscar reloj arma la trama con su suma`() {
        assertArrayEquals(h("DF 00 06 F5 02 01 0B 00 01 01"), Comandos.buscar(true))
        assertArrayEquals(h("DF 00 06 F4 02 01 0B 00 01 00"), Comandos.buscar(false))
    }

    @Test fun `mensaje con icono de WhatsApp y enie`() {
        assertArrayEquals(h("DF 00 0F 2D 02 01 12 00 0A 08 00 00 48 6F 6C 61 20 C3 B1"), Comandos.mensaje("Hola ñ", IconoApp.WHATSAPP))
    }

    @Test fun `hora empaquetada en 4 bytes y de vuelta`() {
        assertArrayEquals(h("6A F0 FB DE"), Comandos.horaEmpaquetada(2026, 11, 24, 15, 47, 30))
        assertArrayEquals(h("DF 00 09 23 02 01 01 00 04 6A F0 FB DE"), Comandos.hora(2026, 11, 24, 15, 47, 30))
        assertArrayEquals(intArrayOf(2026, 11, 24, 15, 47, 30), Comandos.horaDesempaquetada(h("6A F0 FB DE")))
        // límites del año (6 bits) y del día
        assertArrayEquals(intArrayOf(2000, 1, 1, 0, 0, 0), Comandos.horaDesempaquetada(Comandos.horaEmpaquetada(2000, 1, 1, 0, 0, 0)))
        assertArrayEquals(intArrayOf(2063, 12, 31, 23, 59, 59), Comandos.horaDesempaquetada(Comandos.horaEmpaquetada(2063, 12, 31, 23, 59, 59)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `anio fuera de los 6 bits se rechaza`() {
        Comandos.horaEmpaquetada(2064, 1, 1, 0, 0, 0)
    }

    @Test fun `interruptores de avisos son doce unos`() {
        assertArrayEquals(h("DF 00 11 17 02 01 0C 00 0C 01 01 01 01 01 01 01 01 01 01 01 01"), Comandos.interruptoresAvisos())
    }

    @Test fun `la suma coincide en todas las tramas armadas`() {
        listOf(Comandos.buscar(), Comandos.llamada("Mamá"), Comandos.clima(2026, 11, 24, Clima.LLUVIA, 14, 22, 18, "Bogotá"),
            Comandos.formato24h(), Comandos.metrico(false)).forEach {
            assertEquals(Protocolo.suma(it), it[3].toInt() and 0xFF)
            assertEquals(it.size, ((it[1].toInt() and 0xFF) shl 8 or (it[2].toInt() and 0xFF)) + 4)
        }
    }

    @Test fun `texto sin emojis ni byte cero y recortado sin partir caracteres`() {
        assertEquals("Hola ", String(Comandos.textoParaReloj("Hola 🚀", 196), Charsets.UTF_8))
        assertEquals("ab", String(Comandos.textoParaReloj("a\u0000b", 196), Charsets.UTF_8))
        assertEquals("línea1\nlínea2", String(Comandos.textoParaReloj("línea1\nlínea2\t", 196), Charsets.UTF_8))
        assertEquals("★ ok", String(Comandos.textoParaReloj("★ ok", 196), Charsets.UTF_8))
        // «ñ» ocupa 2 bytes: con 4 bytes caben «añ» (3) pero no «ña» completo después
        assertEquals("añ", String(Comandos.textoParaReloj("añña", 4), Charsets.UTF_8))
        assertEquals(196, Comandos.textoParaReloj("x".repeat(500), 196).size)
    }

    @Test fun `trozos para MTU pequena`() {
        val t = Comandos.mensaje("Un mensaje bastante largo para partir")
        val partes = Protocolo.trozos(t, 20)
        assertTrue(partes.all { it.size <= 20 })
        assertArrayEquals(t, partes.reduce { a, b -> a + b })
        assertEquals(1, Protocolo.trozos(Comandos.buscar(), 20).size)
    }

    @Test fun `respuesta del reloj a un mensaje`() {
        val r = Lector().agregar(h("FD 00 05 25 02 12 00 0E 01")).single() as Recibido.Respuesta
        assertEquals(0x02, r.categoria); assertEquals(0x12, r.sub)
        assertTrue(r.bien); assertTrue(r.sumaBien)
        assertEquals("respuesta mensaje: bien", r.describir())
    }

    @Test fun `evento de boton siguiente llegando en dos trozos`() {
        val lector = Lector()
        assertTrue(lector.agregar(h("DF 00 06 01")).isEmpty())
        assertEquals(4, lector.pendientes)
        val e = lector.agregar(h("0C 01 0D 00 01 01")).single() as Recibido.Evento
        assertEquals(0x0C, e.categoria); assertEquals(0x0D, e.sub); assertArrayEquals(byteArrayOf(1), e.datos)
        assertTrue(e.sumaBien)
        assertEquals("evento siguiente 01", e.describir())
        assertEquals(0, lector.pendientes)
    }

    @Test fun `dos tramas pegadas y basura al inicio`() {
        val r = Lector().agregar(h("00 11 FD 00 05 25 02 12 00 0E 01 DF 00 06 01 0C 01 0D 00 01 01"))
        assertEquals(3, r.size)
        assertTrue(r[0] is Recibido.Desconocido)
        assertTrue(r[1] is Recibido.Respuesta)
        assertTrue(r[2] is Recibido.Evento)
    }

    @Test fun `suma equivocada se marca`() {
        val r = Lector().agregar(h("FD 00 05 99 02 12 00 0E 01")).single()
        assertFalse(r.sumaBien)
        assertTrue(r.describir().endsWith("(suma no cuadra)"))
    }

    @Test fun `largo absurdo no bloquea el lector`() {
        val lector = Lector(maxTrama = 64)
        val r = lector.agregar(h("DF FF FF FD 00 05 25 02 12 00 0E 01"))
        assertTrue(r.last() is Recibido.Respuesta)
        assertEquals(0, lector.pendientes)
    }

    @Test fun `hexadecimal tolerante`() {
        assertArrayEquals(byteArrayOf(0xDF.toByte(), 0, 6), Protocolo.deHex("0xDF, 0x00,0x06"))
        assertArrayEquals(byteArrayOf(0xDF.toByte(), 0, 6), Protocolo.deHex("df:00-06"))
        assertNull(Protocolo.deHex("DF 0"))
        assertNull(Protocolo.deHex("ZZ"))
        assertEquals("DF 00 06", Protocolo.hex(byteArrayOf(0xDF.toByte(), 0, 6)))
    }

    @Test fun `botones de musica del Smartwatch y del PBS163`() {
        // Registro del 24 nov: el Smartwatch del usuario manda 0C/02, 0C/03 y 0C/04.
        val r = Lector().agregar(h("DF 00 06 F6 0C 01 02 00 01 01")).single() as Recibido.Evento
        assertEquals(Protocolo.Tecla.PAUSA, Protocolo.teclaDe(r.sub))
        assertEquals(Protocolo.Tecla.ANTERIOR, Protocolo.teclaDe(0x03))
        assertEquals(Protocolo.Tecla.SIGUIENTE, Protocolo.teclaDe(0x04))
        assertEquals(Protocolo.Tecla.PAUSA, Protocolo.teclaDe(0x0A))
        assertEquals(Protocolo.Tecla.ANTERIOR, Protocolo.teclaDe(0x0C))
        assertEquals(Protocolo.Tecla.SIGUIENTE, Protocolo.teclaDe(0x0D))
        assertNull(Protocolo.teclaDe(0x7F))
        assertEquals("evento reproducir/pausa 01", r.describir())
    }

    @Test fun `datos del reloj legibles o en hexadecimal`() {
        assertEquals("10000004", Protocolo.textoLegible("10000004".toByteArray()))
        assertEquals("10000", Protocolo.textoLegible(byteArrayOf(0x31, 0x30, 0x30, 0x30, 0x30, 0, 0)))
        assertEquals("0xC0F238", Protocolo.textoLegible(byteArrayOf(0xC0.toByte(), 0xF2.toByte(), 0x38, 0x00, 0x00)))
        assertEquals("0x0138", Protocolo.textoLegible(byteArrayOf(0x01, 0x38)))
        assertNull(Protocolo.textoLegible(ByteArray(3)))
    }

    @Test fun `codigos de tiempo agotado`() {
        assertTrue(Protocolo.esTiempoAgotado(147)) // el que salió en el Motorola
        assertTrue(Protocolo.esTiempoAgotado(133))
        assertFalse(Protocolo.esTiempoAgotado(0))
        assertFalse(Protocolo.esTiempoAgotado(22))
    }

    @Test fun `nombres conocidos y desconocidos`() {
        assertEquals("buscar reloj", Protocolo.nombre(0x02, 0x0B))
        assertEquals("anterior", Protocolo.nombre(0x0C, 0x0C))
        assertEquals("05/7A", Protocolo.nombre(0x05, 0x7A))
    }
}
