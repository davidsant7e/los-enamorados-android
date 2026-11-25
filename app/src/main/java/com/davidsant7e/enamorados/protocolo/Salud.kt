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
 * 00 = detener. El formato del resultado todavía no se conoce: la app captura lo que llegue mientras mide.
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

    /** Los bytes de un evento en decimal, para compararlos con lo que muestra la pantalla del reloj. */
    fun decimales(datos: ByteArray): String = datos.joinToString(" ") { (it.toInt() and 0xFF).toString() }

    /** Si un evento es de los que llegan solos y no dicen nada de una medición (botones, batería, actividad). */
    fun esRuido(categoria: Int, sub: Int): Boolean =
        categoria == Protocolo.CAT_CONTROL || (categoria == CAT_DATOS && sub == SUB_ACTIVIDAD)

    private fun entero(b: ByteArray, i: Int): Long =
        ((b[i].toLong() and 0xFF) shl 24) or ((b[i + 1].toLong() and 0xFF) shl 16) or
            ((b[i + 2].toLong() and 0xFF) shl 8) or (b[i + 3].toLong() and 0xFF)
}
