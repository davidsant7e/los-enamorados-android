package com.davidsant7e.enamorados.salud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplicarTest {

    // ------------------------------------------------------------------ presión (AHA/ACC 2017)

    @Test fun `presion en los limites de cada categoria`() {
        assertEquals(CategoriaPresion.NORMAL, Explicar.presion(119, 79))
        assertEquals(CategoriaPresion.ELEVADA, Explicar.presion(120, 79))
        assertEquals(CategoriaPresion.ELEVADA, Explicar.presion(129, 79))
        assertEquals(CategoriaPresion.ALTA_1, Explicar.presion(120, 80)) // basta con que la baja llegue a 80
        assertEquals(CategoriaPresion.ALTA_1, Explicar.presion(130, 80))
        assertEquals(CategoriaPresion.ALTA_1, Explicar.presion(130, 70))
        assertEquals(CategoriaPresion.ALTA_1, Explicar.presion(139, 89))
        assertEquals(CategoriaPresion.ALTA_2, Explicar.presion(140, 90))
        assertEquals(CategoriaPresion.ALTA_2, Explicar.presion(140, 70))
        assertEquals(CategoriaPresion.ALTA_2, Explicar.presion(118, 90))
        assertEquals(CategoriaPresion.ALTA_2, Explicar.presion(180, 120))
        assertEquals(CategoriaPresion.CRISIS, Explicar.presion(181, 121))
        assertEquals(CategoriaPresion.CRISIS, Explicar.presion(181, 100))
        assertEquals(CategoriaPresion.CRISIS, Explicar.presion(150, 121))
        assertEquals(CategoriaPresion.BAJA, Explicar.presion(89, 59))
        assertEquals(CategoriaPresion.BAJA, Explicar.presion(89, 65))
        assertEquals(CategoriaPresion.BAJA, Explicar.presion(100, 59))
        assertEquals(CategoriaPresion.NORMAL, Explicar.presion(90, 60))
    }

    @Test fun `la presion real del usuario es normal y verde`() {
        val c = Explicar.presion(112, 73)
        assertEquals(CategoriaPresion.NORMAL, c)
        assertEquals(Tono.BIEN, c.tono)
        assertTrue(Explicar.queSignificaPresion(112, 73).contains("La alta (112"))
    }

    @Test fun `tonos de presion`() {
        assertEquals(Tono.OJO, Explicar.presion(125, 75).tono)
        assertEquals(Tono.ALERTA, Explicar.presion(145, 85).tono)
        assertEquals(Tono.ALERTA, Explicar.presion(190, 100).tono)
        assertTrue(CategoriaPresion.CRISIS.frase.contains("atención médica"))
    }

    // ------------------------------------------------------------------ pulso

    @Test fun `pulso en reposo y sus limites`() {
        assertEquals(CategoriaPulso.BAJO, Explicar.pulso(59))
        assertEquals(CategoriaPulso.NORMAL, Explicar.pulso(60))
        assertEquals(CategoriaPulso.NORMAL, Explicar.pulso(83))
        assertEquals(CategoriaPulso.NORMAL, Explicar.pulso(100))
        assertEquals(CategoriaPulso.ALTO, Explicar.pulso(101))
        assertTrue(CategoriaPulso.NORMAL.frase.contains("reposo"))
    }

    // ------------------------------------------------------------------ zonas

    @Test fun `pulso maximo con la formula de Tanaka`() {
        assertEquals(187, Explicar.pulsoMaximo(30))  // 208 − 21
        assertEquals(177, Explicar.pulsoMaximo(45))  // 176,5 → 177
        assertEquals(173, Explicar.pulsoMaximo(50))
        assertEquals(194, Explicar.pulsoMaximo(20))
        assertEquals(166, Explicar.pulsoMaximo(60))
    }

    @Test fun `zonas para 30 anos`() {
        val z = Explicar.zonas(30)
        assertEquals(5, z.size)
        // 187 × 50 % = 93,5 → 94; 60 % = 112,2 → 112; 70 % = 130,9 → 131; 80 % = 149,6 → 150; 90 % = 168,3 → 168
        assertEquals(listOf(94 to 111, 112 to 130, 131 to 149, 150 to 167, 168 to 187), z.map { it.desde to it.hasta })
        assertEquals(listOf(1, 2, 3, 4, 5), z.map { it.numero })
        assertEquals(50, z[0].porcentajeDesde)
        assertEquals(100, z[4].porcentajeHasta)
    }

    @Test fun `zonas para 45 y 60 anos`() {
        // 177: 88,5 → 89 (redondeo hacia arriba); 106,2 → 106; 123,9 → 124; 141,6 → 142; 159,3 → 159
        assertEquals(listOf(89 to 105, 106 to 123, 124 to 141, 142 to 158, 159 to 177), Explicar.zonas(45).map { it.desde to it.hasta })
        // 166: 83; 99,6 → 100; 116,2 → 116; 132,8 → 133; 149,4 → 149
        assertEquals(listOf(83 to 99, 100 to 115, 116 to 132, 133 to 148, 149 to 166), Explicar.zonas(60).map { it.desde to it.hasta })
    }

    @Test fun `zonas sin huecos ni cruces`() {
        for (edad in listOf(15, 25, 38, 52, 70, 90)) for (reposo in listOf(null, 55, 70)) {
            val z = Explicar.zonas(edad, reposo)
            for (i in 0 until 4) assertEquals("edad $edad", z[i].hasta + 1, z[i + 1].desde)
            assertEquals(Explicar.pulsoMaximo(edad), z[4].hasta)
            z.forEach { assertTrue(it.desde <= it.hasta) }
        }
    }

    @Test fun `con pulso en reposo se usa Karvonen`() {
        // 30 años, reposo 60: reserva 127. 50 % → 60 + 63,5 = 123,5 → 124
        val z = Explicar.zonas(30, 60)
        assertEquals(124, z[0].desde)
        assertEquals(149, z[2].desde) // 60 + 88,9 = 148,9 → 149
        // un reposo absurdo se ignora
        assertEquals(Explicar.zonas(30), Explicar.zonas(30, 200))
    }

    @Test fun `ubicar las lecturas en las zonas`() {
        val z = Explicar.zonas(30)
        val reposo = Explicar.ubicar(83, z)
        assertTrue(reposo.debajo)
        assertNull(reposo.zona)
        assertTrue(reposo.frase.startsWith("83 lpm = por debajo de la zona 1"))
        assertEquals(1, Explicar.ubicar(94, z).zona?.numero)
        assertEquals(1, Explicar.ubicar(111, z).zona?.numero)
        assertEquals(2, Explicar.ubicar(112, z).zona?.numero)
        assertEquals(3, Explicar.ubicar(140, z).zona?.numero)
        assertEquals(5, Explicar.ubicar(187, z).zona?.numero)
        val encima = Explicar.ubicar(190, z)
        assertTrue(encima.encima)
        assertFalse(encima.debajo)
        assertTrue(Explicar.ubicar(120, z).frase.contains("zona 2 (suave)"))
    }

    @Test fun `edad y reposo escritos por el usuario`() {
        assertEquals(42, Explicar.edadDe(" 42 "))
        assertNull(Explicar.edadDe("4"))
        assertNull(Explicar.edadDe("abc"))
        assertNull(Explicar.edadDe("150"))
        assertEquals(62, Explicar.reposoDe("62"))
        assertNull(Explicar.reposoDe("10"))
    }

    // ------------------------------------------------------------------ resumen

    @Test fun `resumen en una linea`() {
        assertEquals("Hoy: 2.645 pasos · último pulso 83 lpm (normal en reposo) · presión 112/73 (normal)",
            Explicar.resumen(2645, 83, 112 to 73))
        assertEquals("Último pulso 83 lpm (normal en reposo)", Explicar.resumen(null, 83, null))
        assertEquals("2.645 pasos · presión 145/92 (alta, etapa 2)", Explicar.resumen(2645, null, 145 to 92, deHoy = false))
        assertTrue(Explicar.resumen(null, null, null).startsWith("Todavía no hay datos"))
    }

    @Test fun `miles en espanol`() {
        assertEquals("0", Explicar.miles(0))
        assertEquals("999", Explicar.miles(999))
        assertEquals("2.645", Explicar.miles(2645))
        assertEquals("1.234.567", Explicar.miles(1_234_567))
    }
}
