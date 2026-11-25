package com.davidsant7e.enamorados.salud

import kotlin.math.roundToInt

/**
 * Traduce los números del reloj a frases que entiende cualquiera. Funciones puras (sin Android), probadas en
 * `ExplicarTest`. Todo es orientativo: un reloj no sirve para diagnóstico.
 */

/** Cómo pintar una lectura: verde (bien), ámbar (ojo), rojo (atención) o neutro. */
enum class Tono { BIEN, OJO, ALERTA, NEUTRO }

/**
 * Categorías de presión de la guía AHA/ACC 2017, más «baja» (menos de 90/60).
 * Se revisan de la más grave a la más leve: manda el número que esté peor.
 */
enum class CategoriaPresion(val nombre: String, val tono: Tono, val frase: String) {
    CRISIS("Muy alta (crisis)", Tono.ALERTA,
        "Está muy por encima de lo normal (más de 180 la alta o más de 120 la baja). Repite la medición en reposo; " +
            "si sigue así, busca atención médica ya, sobre todo si tienes dolor de pecho, falta de aire o mareo."),
    ALTA_2("Alta, etapa 2", Tono.ALERTA,
        "Está alta (140 o más la alta, o 90 o más la baja). Si se repite en varios días, coméntaselo a tu médico."),
    ALTA_1("Alta, etapa 1", Tono.OJO,
        "Está un poco alta (130–139 la alta, o 80–89 la baja). Vale la pena vigilarla y medirla otros días."),
    ELEVADA("Elevada", Tono.OJO,
        "La alta está un poco por encima de lo ideal (120–129) y la baja bien. No es hipertensión, pero conviene vigilarla."),
    BAJA("Baja", Tono.OJO,
        "Está por debajo de 90/60. A muchas personas no les pasa nada; si te sientes mareado o débil, siéntate y consúltalo."),
    NORMAL("Normal", Tono.BIEN,
        "Está en el rango sano: menos de 120 la alta y menos de 80 la baja."),
}

/** Pulso en reposo de un adulto: menos de 60 bajo, 60–100 normal, más de 100 alto. */
enum class CategoriaPulso(val nombre: String, val tono: Tono, val frase: String) {
    BAJO("Bajo", Tono.OJO,
        "Más lento de lo común en reposo (menos de 60). Es normal en personas que entrenan mucho o al dormir; " +
            "si te mareas o te cansas sin razón, consúltalo."),
    NORMAL("Normal en reposo", Tono.BIEN,
        "Si estabas en reposo, está normal: lo sano es entre 60 y 100 latidos por minuto."),
    ALTO("Alto para reposo", Tono.OJO,
        "Más de 100 latidos por minuto. Es normal si te estabas moviendo, con café o nervios; si estabas quieto, " +
            "descansa cinco minutos y vuelve a medir."),
}

/** Una zona de cardio: [desde]–[hasta] en latidos por minuto (ambos incluidos). */
data class Zona(
    val numero: Int, val nombre: String, val para: String,
    val porcentajeDesde: Int, val porcentajeHasta: Int,
    val desde: Int, val hasta: Int,
)

/** Dónde cae un pulso respecto a las zonas: [zona] es null si está por debajo de la 1 o por encima del máximo. */
data class Ubicacion(val zona: Zona?, val debajo: Boolean, val encima: Boolean, val frase: String)

object Explicar {
    const val AVISO = "Orientativo: las mediciones de un reloj no sirven para diagnóstico."

    // ------------------------------------------------------------------ presión

    fun presion(alta: Int, baja: Int): CategoriaPresion = when {
        alta > 180 || baja > 120 -> CategoriaPresion.CRISIS
        alta >= 140 || baja >= 90 -> CategoriaPresion.ALTA_2
        alta >= 130 || baja >= 80 -> CategoriaPresion.ALTA_1
        alta >= 120 -> CategoriaPresion.ELEVADA
        alta < 90 || baja < 60 -> CategoriaPresion.BAJA
        else -> CategoriaPresion.NORMAL
    }

    /** Qué significa cada número, con los del usuario. */
    fun queSignificaPresion(alta: Int, baja: Int): String =
        "La alta ($alta, «sistólica») es la presión de la sangre cuando el corazón late; " +
            "la baja ($baja, «diastólica»), cuando descansa entre latido y latido. Se mide en mmHg."

    // ------------------------------------------------------------------ pulso

    fun pulso(lpm: Int): CategoriaPulso = when {
        lpm < 60 -> CategoriaPulso.BAJO
        lpm <= 100 -> CategoriaPulso.NORMAL
        else -> CategoriaPulso.ALTO
    }

    // ------------------------------------------------------------------ zonas de cardio

    /** Edades con las que tiene sentido calcular. */
    val EDADES = 10..100

    /**
     * Pulso máximo estimado con la fórmula de Tanaka (2001): 208 − 0,7 × edad. Se usa en vez de «220 − edad» porque
     * se equivoca menos después de los 40 años. Sigue siendo una estimación (± 10 lpm).
     */
    fun pulsoMaximo(edad: Int): Int = (208 - 0.7 * edad).roundToInt()

    private val NOMBRES = listOf(
        Triple("Muy suave", "recuperación, caminar tranquilo", 50),
        Triple("Suave", "quema de grasa y base: puedes conversar", 60),
        Triple("Moderada", "aeróbica: respiras más fuerte, hablas con frases cortas", 70),
        Triple("Intensa", "umbral: cuesta hablar, se aguanta pocos minutos", 80),
        Triple("Máxima", "esfuerzo total, solo segundos o pocos minutos", 90),
    )

    /**
     * Las 5 zonas para una edad. Sin pulso en reposo: porcentaje del pulso máximo. Con pulso en reposo: método de
     * Karvonen (porcentaje de la reserva = máximo − reposo, sumado al reposo), que se ajusta mejor a cada persona.
     */
    fun zonas(edad: Int, reposo: Int? = null): List<Zona> {
        val maximo = pulsoMaximo(edad)
        val base = reposo?.takeIf { it in 30 until maximo } ?: 0
        fun lpm(porcentaje: Int) = (base + (maximo - base) * porcentaje / 100.0).roundToInt()
        return NOMBRES.mapIndexed { i, (nombre, para, desde) ->
            val hastaPct = desde + 10
            Zona(i + 1, nombre, para, desde, hastaPct, lpm(desde), if (i == 4) maximo else lpm(hastaPct) - 1)
        }
    }

    fun ubicar(lpm: Int, zonas: List<Zona>): Ubicacion {
        val primera = zonas.first()
        val ultima = zonas.last()
        return when {
            lpm < primera.desde -> Ubicacion(null, true, false,
                "$lpm lpm = por debajo de la zona 1: ritmo de reposo o de actividad muy ligera.")
            lpm > ultima.hasta -> Ubicacion(null, false, true,
                "$lpm lpm = por encima de tu máximo estimado. Si no estabas haciendo un gran esfuerzo, vuelve a medir.")
            else -> {
                val z = zonas.first { lpm in it.desde..it.hasta }
                Ubicacion(z, false, false, "$lpm lpm = zona ${z.numero} (${z.nombre.lowercase()}): ${z.para}.")
            }
        }
    }

    /** Edad escrita por el usuario → edad válida o null. */
    fun edadDe(texto: String): Int? = texto.trim().toIntOrNull()?.takeIf { it in EDADES }

    /** Pulso en reposo escrito por el usuario → valor razonable o null. */
    fun reposoDe(texto: String): Int? = texto.trim().toIntOrNull()?.takeIf { it in 30..120 }

    // ------------------------------------------------------------------ resumen

    /** «Hoy: 2.645 pasos · último pulso 83 lpm (normal en reposo) · presión 112/73 (normal)». */
    fun resumen(pasos: Long?, pulso: Int?, presion: Pair<Int, Int>?, deHoy: Boolean = true): String {
        val partes = buildList {
            pasos?.let { add("${miles(it)} pasos") }
            pulso?.let { add("último pulso $it lpm (${pulso(it).nombre.lowercase()})") }
            presion?.let { (a, b) -> add("presión $a/$b (${presion(a, b).nombre.lowercase()})") }
        }
        if (partes.isEmpty()) return "Todavía no hay datos: mide tu pulso o pulsa «Actualizar pasos»."
        val texto = partes.joinToString(" · ")
        return if (deHoy && pasos != null) "Hoy: $texto" else texto.replaceFirstChar { it.uppercase() }
    }

    /** 2645 → «2.645» (separador de miles en español, sin depender del idioma del celular). */
    fun miles(n: Long): String {
        val s = kotlin.math.abs(n).toString()
        val conPuntos = s.reversed().chunked(3).joinToString(".").reversed()
        return if (n < 0) "-$conPuntos" else conPuntos
    }
}
