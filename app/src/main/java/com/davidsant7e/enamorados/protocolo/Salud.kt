package com.davidsant7e.enamorados.protocolo

/**
 * Datos de salud del reloj. Descifrados del registro del 24 nov 2026 (sincronización de HryFine
 * capturada por esta app, porque los avisos del canal se comparten):
 *
 *  - Evento 05/0C, 12 bytes = tres números de 4 bytes (mayor primero):
 *      `00 00 07 6A | 00 00 04 99 | 00 00 B3 4F` → 1898 pasos, 1177 m (0,62 m por paso), 45903.
 *    El tercero parece calorías en «cal» (45,9 kcal): **por confirmar** contra lo que muestra HryFine.
 *  - Fechas de 2 bytes: día (5 bits bajos), mes (4 bits), año − 2000 (bits altos). `35 78` = 24 nov 2026.
 *
 * Mediciones (protocolo publicado del PBS163): 02/0D pulso, 02/0E presión, 02/1C oxígeno, dato 01 = empezar,
 * 00 = detener. Resultados descifrados del registro del 24 nov (20:39–20:44, versión 0.2):
 *
 *  - Evento 05/04 (pulso) y 05/05 (presión): `fecha(2) | cantidad(2) | cantidad × [segundos del día(4) | valor(4)]`.
 *      `35 78 | 00 01 | 00 01 22 91 | 00 00 00 53` → 24 nov, 20:39:45, pulso 83.
 *      `35 78 | 00 01 | 00 01 22 CB | 00 00 70 49` → 24 nov, 20:40:43, presión 112/73 (alta en el 3.er byte, baja en el 4.º).
 *    Llegan ~10 s después de empezar a medir, y otra vez (como historial) cuando HryFine sincroniza.
 *  - Oxígeno (02/1C): el reloj no mandó nada en 45 s; formato desconocido (la app sigue capturando).
 */
object Salud {
    const val CAT_DATOS = 0x05
    const val SUB_ACTIVIDAD = 0x0C
    /** HryFine envía 05/06 al sincronizar y el reloj contesta con 05/0C (no se sabe si lleva datos; se prueba vacío). */
    const val SUB_PEDIR_ACTIVIDAD = 0x06

    data class Actividad(val pasos: Long, val metros: Long, val calorias: Long) {
        val kilometros: Double get() = metros / 1000.0
        /** Si el tercer número son calorías en «cal», esto son kcal. */
        val kcal: Double get() = calorias / 1000.0
    }

    fun actividadDe(categoria: Int, sub: Int, datos: ByteArray): Actividad? {
        if (categoria != CAT_DATOS || sub != SUB_ACTIVIDAD || datos.size < 12) return null
        return Actividad(entero(datos, 0), entero(datos, 4), entero(datos, 8))
    }

    fun pedirActividad(): ByteArray = Protocolo.trama(CAT_DATOS, SUB_PEDIR_ACTIVIDAD)

    /** Fecha de 2 bytes del reloj → (año, mes, día), o null si no tiene sentido. */
    fun fecha(alto: Int, bajo: Int): Triple<Int, Int, Int>? {
        val v = ((alto and 0xFF) shl 8) or (bajo and 0xFF)
        val dia = v and 0x1F
        val mes = (v shr 5) and 0x0F
        val anio = 2000 + (v shr 9)
        return if (dia in 1..31 && mes in 1..12) Triple(anio, mes, dia) else null
    }

    enum class Medicion(val sub: Int, val etiqueta: String, val segundos: Int) {
        PULSO(0x0D, "Pulso", 45),
        PRESION(0x0E, "Presión", 60),
        OXIGENO(0x1C, "Oxígeno", 45),
    }

    fun medir(m: Medicion, empezar: Boolean): ByteArray =
        Protocolo.trama(Protocolo.CAT_AJUSTES, m.sub, byteArrayOf(if (empezar) 1 else 0))

    const val SUB_PULSO = 0x04
    const val SUB_PRESION = 0x05

    /** Una medición guardada en el reloj. [valor] es el pulso (lpm); en presión, [valor]/[valor2] = alta/baja (mmHg). */
    data class Lectura(val tipo: Medicion, val anio: Int, val mes: Int, val dia: Int, val segundos: Int, val valor: Int, val valor2: Int? = null) {
        val hora: String get() = "%02d:%02d".format(segundos / 3600, segundos / 60 % 60)
        val texto: String get() = when (tipo) {
            Medicion.PRESION -> "$valor/$valor2 mmHg"
            Medicion.PULSO -> "$valor lpm"
            Medicion.OXIGENO -> "$valor %"
        }
        val clave: String get() = "${tipo.name}|$anio|$mes|$dia|$segundos"
    }

    /** Lecturas de pulso (05/04) o presión (05/05); lista vacía si el evento es otro o no cuadra. */
    fun lecturasDe(categoria: Int, sub: Int, datos: ByteArray): List<Lectura> {
        val tipo = when {
            categoria != CAT_DATOS -> return emptyList()
            sub == SUB_PULSO -> Medicion.PULSO
            sub == SUB_PRESION -> Medicion.PRESION
            else -> return emptyList()
        }
        if (datos.size < 4) return emptyList()
        val (anio, mes, dia) = fecha(datos[0].toInt(), datos[1].toInt()) ?: return emptyList()
        val cantidad = ((datos[2].toInt() and 0xFF) shl 8) or (datos[3].toInt() and 0xFF)
        if (cantidad == 0 || datos.size < 4 + cantidad * 8) return emptyList()
        return (0 until cantidad).mapNotNull { i ->
            val base = 4 + i * 8
            val seg = entero(datos, base).toInt()
            if (seg !in 0 until 86_400) return@mapNotNull null
            val v = datos.copyOfRange(base + 4, base + 8).map { it.toInt() and 0xFF }
            when (tipo) {
                Medicion.PRESION -> if (v[2] == 0 || v[3] == 0) null else Lectura(tipo, anio, mes, dia, seg, v[2], v[3])
                else -> entero(datos, base + 4).toInt().takeIf { it > 0 }?.let { Lectura(tipo, anio, mes, dia, seg, it) }
            }
        }
    }

    /** Los bytes de un evento en decimal, para compararlos con lo que muestra la pantalla del reloj. */
    fun decimales(datos: ByteArray): String = datos.joinToString(" ") { (it.toInt() and 0xFF).toString() }

    /** Si un evento es de los que llegan solos y no dicen nada de una medición (botones, batería, actividad). */
    fun esRuido(categoria: Int, sub: Int): Boolean =
        categoria == Protocolo.CAT_CONTROL || (categoria == CAT_DATOS && sub in setOf(SUB_ACTIVIDAD, SUB_PULSO, SUB_PRESION))

    private fun entero(b: ByteArray, i: Int): Long =
        ((b[i].toLong() and 0xFF) shl 24) or ((b[i + 1].toLong() and 0xFF) shl 16) or
            ((b[i + 2].toLong() and 0xFF) shl 8) or (b[i + 3].toLong() and 0xFF)
}
