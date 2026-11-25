package com.davidsant7e.enamorados.clima

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class JsonTest {
    @Test fun `valores basicos`() {
        assertEquals(mapOf("a" to 1.0, "b" to listOf(true, false, null), "c" to "x"), Json.leer("""{ "a": 1, "b": [true, false, null], "c": "x" }"""))
        assertEquals(-74.08175, Json.leer("-74.08175") as Double, 1e-12)
        assertEquals(1.5e3, Json.leer("1.5E3") as Double, 1e-12)
        assertEquals(emptyList<Any?>(), Json.leer("[ ]"))
        assertEquals(emptyMap<String, Any?>(), Json.leer("{}"))
        assertNull(Json.leer("null"))
    }

    @Test fun `escapes y tildes`() {
        assertEquals("Bogotá", Json.leer("\"Bogot\\u00e1\""))
        assertEquals("Bogotá", Json.leer("\"Bogotá\""))
        assertEquals("a\"b\\c/d\ne", Json.leer("\"a\\\"b\\\\c\\/d\\ne\""))
    }

    @Test fun `JSON roto lanza error`() {
        for (roto in listOf("", "{", "[1,", "{\"a\" 1}", "\"sin cerrar", "nul", "{} x", "[1 2]")) {
            try { Json.leer(roto); fail("debía fallar: $roto") } catch (_: IllegalArgumentException) {}
        }
    }
}
