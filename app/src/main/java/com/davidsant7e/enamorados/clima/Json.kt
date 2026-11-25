package com.davidsant7e.enamorados.clima

/**
 * Lector de JSON mínimo y puro (sin org.json, que en las pruebas de JVM de Android viene vacío).
 * Devuelve Map<String, Any?>, List<Any?>, String, Double, Boolean o null. Lanza IllegalArgumentException si no es JSON.
 */
object Json {
    fun leer(texto: String): Any? {
        val l = Lectura(texto)
        val v = l.valor()
        l.espacios()
        require(l.i == texto.length) { "Sobra texto en la posición ${l.i}" }
        return v
    }

    private class Lectura(val s: String) {
        var i = 0

        fun espacios() { while (i < s.length && s[i].isWhitespace()) i++ }

        fun valor(): Any? {
            espacios()
            require(i < s.length) { "JSON incompleto" }
            return when (val c = s[i]) {
                '{' -> objeto()
                '[' -> lista()
                '"' -> cadena()
                't' -> palabra("true", true)
                'f' -> palabra("false", false)
                'n' -> palabra("null", null)
                else -> if (c == '-' || c.isDigit()) numero() else throw IllegalArgumentException("Carácter inesperado «$c» en $i")
            }
        }

        private fun palabra(p: String, v: Any?): Any? {
            require(s.startsWith(p, i)) { "Se esperaba $p en $i" }
            i += p.length
            return v
        }

        private fun numero(): Double {
            val inicio = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(inicio, i).toDoubleOrNull() ?: throw IllegalArgumentException("Número mal escrito en $inicio")
        }

        private fun cadena(): String {
            i++ // comilla
            val sb = StringBuilder()
            while (true) {
                require(i < s.length) { "Texto sin cerrar" }
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        require(i < s.length) { "Escape incompleto" }
                        when (val e = s[i++]) {
                            '"', '\\', '/' -> sb.append(e)
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> {
                                require(i + 4 <= s.length) { "Escape \\u incompleto" }
                                sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4
                            }
                            else -> throw IllegalArgumentException("Escape desconocido \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun lista(): List<Any?> {
            i++
            val r = mutableListOf<Any?>()
            espacios()
            if (i < s.length && s[i] == ']') { i++; return r }
            while (true) {
                r += valor()
                espacios()
                require(i < s.length) { "Lista sin cerrar" }
                when (s[i++]) { ',' -> continue; ']' -> return r; else -> throw IllegalArgumentException("Se esperaba , o ] en ${i - 1}") }
            }
        }

        private fun objeto(): Map<String, Any?> {
            i++
            val r = LinkedHashMap<String, Any?>()
            espacios()
            if (i < s.length && s[i] == '}') { i++; return r }
            while (true) {
                espacios()
                require(i < s.length && s[i] == '"') { "Se esperaba una clave en $i" }
                val clave = cadena()
                espacios()
                require(i < s.length && s[i] == ':') { "Se esperaba : en $i" }
                i++
                r[clave] = valor()
                espacios()
                require(i < s.length) { "Objeto sin cerrar" }
                when (s[i++]) { ',' -> continue; '}' -> return r; else -> throw IllegalArgumentException("Se esperaba , o } en ${i - 1}") }
            }
        }
    }
}
