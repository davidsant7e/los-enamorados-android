package com.davidsant7e.enamorados.protocolo

import java.util.UUID

/**
 * Protocolo de los relojes que usan la app HryFine (com.lianhezhuli.hyfit).
 *
 * Fuente: ingeniería inversa publicada por jsphu (MIT) en github.com/jsphu/polosmart-pbs163-ble,
 * a partir del APK de HryFine y de capturas HCI. Este archivo no depende de Android: se prueba en la JVM.
 *
 * Canal: Nordic UART Service con el último byte cambiado (…9F en vez de …9E).
 *   RX (el celular escribe) 6E400002-…-E50E24DCCA9F
 *   TX (el reloj avisa)     6E400003-…-E50E24DCCA9F
 *
 * Trama que envía el celular:
 *   DF | largoHi largoLo | suma | categoría | 01 | subcomando | datosHi datosLo | datos…
 *   largo = datos + 5; suma = suma de todos los demás bytes & 0xFF (va en la posición 3).
 * Respuesta del reloj:
 *   FD | largoHi largoLo | suma | categoría | subcomando | …estado (último byte 01 = bien)
 * Tamaño total de cualquier trama = largo + 4.
 */
object Protocolo {
    val SERVICIO: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9f")
    val RX: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9f")
    val TX: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9f")
    /** Variante «oficial» de Nordic, por si algún reloj la usa. */
    val SERVICIO_NORDIC: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    val BATERIA: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    val NIVEL_BATERIA: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    val INFO: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    /** Características de Device Information (0x180A) con su nombre. */
    val CAMPOS_INFO: Map<UUID, String> = mapOf(
        uuid16(0x2A24) to "modelo",
        uuid16(0x2A25) to "serie",
        uuid16(0x2A26) to "firmware",
        uuid16(0x2A27) to "hardware",
        uuid16(0x2A28) to "software",
        uuid16(0x2A29) to "fabricante",
    )

    const val INICIO_ENVIO = 0xDF
    const val INICIO_RESPUESTA = 0xFD
    const val VERSION = 0x01

    const val CAT_AJUSTES = 0x02
    const val CAT_CONTROL = 0x0C

    /** Subcomandos conocidos de la categoría 0x02 (ajustes). */
    val NOMBRES_AJUSTES: Map<Int, String> = mapOf(
        0x01 to "hora",
        0x02 to "alarma",
        0x03 to "sedentarismo",
        0x0B to "buscar reloj",
        0x0C to "interruptores de avisos",
        0x0D to "ritmo cardiaco",
        0x0E to "presión",
        0x11 to "llamada",
        0x12 to "mensaje",
        0x1A to "formato 12/24 h",
        0x1B to "unidades",
        0x1C to "oxígeno",
        0x1D to "clima",
        0x21 to "tomar agua",
    )

    /** Botones del reloj en la pantalla de música o cámara (categoría 0x0C). */
    val NOMBRES_CONTROL: Map<Int, String> = mapOf(
        0x03 to "cámara", 0x04 to "cámara",
        0x0A to "reproducir/pausa", 0x0B to "reproducir/pausa",
        0x0C to "anterior", 0x0D to "siguiente",
    )

    fun uuid16(corto: Int): UUID = UUID.fromString(String.format("0000%04x-0000-1000-8000-00805f9b34fb", corto))

    fun nombre(categoria: Int, sub: Int): String = when (categoria) {
        CAT_AJUSTES -> NOMBRES_AJUSTES[sub]
        CAT_CONTROL -> NOMBRES_CONTROL[sub]
        else -> null
    } ?: "%02X/%02X".format(categoria, sub)

    /** Arma una trama para el reloj. */
    fun trama(categoria: Int, sub: Int, datos: ByteArray = ByteArray(0)): ByteArray {
        require(datos.size <= 0xFFFF - 5) { "datos demasiado largos: ${datos.size}" }
        val largo = datos.size + 5
        val sinSuma = byteArrayOf(
            INICIO_ENVIO.toByte(),
            (largo shr 8).toByte(), largo.toByte(),
            categoria.toByte(), VERSION.toByte(), sub.toByte(),
            (datos.size shr 8).toByte(), datos.size.toByte(),
        ) + datos
        val suma = sinSuma.sumOf { it.toInt() and 0xFF } and 0xFF
        return sinSuma.copyOfRange(0, 3) + byteArrayOf(suma.toByte()) + sinSuma.copyOfRange(3, sinSuma.size)
    }

    /** Suma de comprobación de una trama ya armada (todos los bytes menos el de la posición 3). */
    fun suma(trama: ByteArray): Int {
        var s = 0
        for (i in trama.indices) if (i != 3) s += trama[i].toInt() and 0xFF
        return s and 0xFF
    }

    /** Parte una trama en trozos del tamaño que permite la conexión (MTU − 3). */
    fun trozos(trama: ByteArray, tam: Int): List<ByteArray> {
        require(tam > 0)
        if (trama.size <= tam) return listOf(trama)
        return (trama.indices step tam).map { trama.copyOfRange(it, minOf(it + tam, trama.size)) }
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    /** «DF 00 06 …», «df0006», «0xDF,0x00» → bytes. Devuelve null si no es hexadecimal válido. */
    fun deHex(texto: String): ByteArray? {
        val limpio = texto.replace("0x", "", ignoreCase = true).filter { !it.isWhitespace() && it != ',' && it != ':' && it != '-' }
        if (limpio.length % 2 != 0 || !limpio.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return ByteArray(limpio.length / 2) { limpio.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}

/** Íconos que el reloj sabe mostrar junto a un mensaje (subcomando 0x12). */
enum class IconoApp(val codigo: Int, val etiqueta: String) {
    LLAMADA(0, "Llamada"),
    SMS(1, "SMS"),
    QQ(2, "QQ"),
    WECHAT(3, "WeChat"),
    FACEBOOK(4, "Facebook"),
    TWITTER(5, "Twitter"),
    SKYPE(6, "Skype"),
    LINE(7, "Line"),
    WHATSAPP(8, "WhatsApp"),
    KAKAO(9, "KakaoTalk"),
    INSTAGRAM(10, "Instagram"),
    NINGUNO(11, "Sin ícono"),
}

/** Estados del clima que entiende el reloj (subcomando 0x1D). */
enum class Clima(val codigo: Int, val etiqueta: String) {
    SOLEADO(0, "Soleado"), NUBLADO(1, "Nublado"), CUBIERTO(2, "Cubierto"),
    LLUVIA(3, "Lluvia"), TORMENTA(4, "Tormenta"), NIEVE(5, "Nieve"), NIEBLA(6, "Niebla"),
}

/** Comandos listos para enviar. Funciones puras: devuelven la trama completa. */
object Comandos {
    /** Máximo que acepta el reloj en un mensaje; en pantalla caben ~42 caracteres en 3 líneas. */
    const val MAX_BYTES_MENSAJE = 196
    const val CARACTERES_EN_PANTALLA = 42
    const val MAX_BYTES_NOMBRE = 64
    const val MAX_BYTES_CIUDAD = 50

    /** Vibra y enciende la pantalla (un pulso de ~1,5 s). `false` lo detiene. */
    fun buscar(encender: Boolean = true) = Protocolo.trama(Protocolo.CAT_AJUSTES, 0x0B, byteArrayOf(if (encender) 1 else 0))

    /** Fecha y hora empaquetadas en 4 bytes (año desde 2000 en 6 bits, mes 4, día 5, hora 5, minuto 6, segundo 6). */
    fun hora(anio: Int, mes: Int, dia: Int, hora: Int, minuto: Int, segundo: Int) =
        Protocolo.trama(Protocolo.CAT_AJUSTES, 0x01, horaEmpaquetada(anio, mes, dia, hora, minuto, segundo))

    fun horaEmpaquetada(anio: Int, mes: Int, dia: Int, hora: Int, minuto: Int, segundo: Int): ByteArray {
        val y = anio - 2000
        require(y in 0..63 && mes in 1..12 && dia in 1..31 && hora in 0..23 && minuto in 0..59 && segundo in 0..59) {
            "fecha fuera de rango: $anio-$mes-$dia $hora:$minuto:$segundo"
        }
        val b0 = ((y shl 2) or (mes shr 2)) and 0xFF
        val b1 = (((mes and 3) shl 6) or (dia shl 1) or (hora shr 4)) and 0xFF
        val b2 = (((hora and 15) shl 4) or (minuto shr 2)) and 0xFF
        val b3 = (((minuto and 3) shl 6) or segundo) and 0xFF
        return byteArrayOf(b0.toByte(), b1.toByte(), b2.toByte(), b3.toByte())
    }

    /** Inverso de [horaEmpaquetada]: [año, mes, día, hora, minuto, segundo]. */
    fun horaDesempaquetada(b: ByteArray): IntArray {
        val v = ((b[0].toInt() and 0xFF) shl 24) or ((b[1].toInt() and 0xFF) shl 16) or ((b[2].toInt() and 0xFF) shl 8) or (b[3].toInt() and 0xFF)
        return intArrayOf(2000 + (v ushr 26), (v ushr 22) and 15, (v ushr 17) and 31, (v ushr 12) and 31, (v ushr 6) and 63, v and 63)
    }

    /** Activa los 12 interruptores de avisos del reloj (HryFine lo envía antes de cada mensaje). */
    fun interruptoresAvisos() = Protocolo.trama(Protocolo.CAT_AJUSTES, 0x0C, ByteArray(12) { 1 })

    /** Mensaje con ícono. El texto se limpia con [textoParaReloj]. */
    fun mensaje(texto: String, icono: IconoApp = IconoApp.SMS) =
        Protocolo.trama(Protocolo.CAT_AJUSTES, 0x12,
            byteArrayOf(icono.codigo.toByte(), 0, 0) + textoParaReloj(texto, MAX_BYTES_MENSAJE))

    /** Aviso de llamada con el nombre (vibra seguido) o, con `sonando=false`, la detiene. */
    fun llamada(nombre: String, sonando: Boolean = true) =
        Protocolo.trama(Protocolo.CAT_AJUSTES, 0x11,
            byteArrayOf(if (sonando) 1 else 0, 0) + textoParaReloj(nombre, MAX_BYTES_NOMBRE))

    fun clima(anio: Int, mes: Int, dia: Int, clima: Clima, minima: Int, maxima: Int, actual: Int, ciudad: String) =
        Protocolo.trama(Protocolo.CAT_AJUSTES, 0x1D,
            byteArrayOf(
                (anio shr 8).toByte(), anio.toByte(), mes.toByte(), dia.toByte(), 0,
                clima.codigo.toByte(), minima.toByte(), maxima.toByte(), actual.toByte(),
            ) + textoParaReloj(ciudad, MAX_BYTES_CIUDAD))

    fun formato24h(si: Boolean = true) = Protocolo.trama(Protocolo.CAT_AJUSTES, 0x1A, byteArrayOf(if (si) 1 else 0))
    fun metrico(si: Boolean = true) = Protocolo.trama(Protocolo.CAT_AJUSTES, 0x1B, byteArrayOf(if (si) 1 else 0))

    /**
     * Texto en UTF-8 que el reloj puede mostrar: quita emojis y otros caracteres de 4 bytes (no están en su
     * fuente), el byte 0 (corta el texto) y los caracteres de control salvo el salto de línea; recorta sin
     * partir un carácter.
     */
    fun textoParaReloj(texto: String, maxBytes: Int): ByteArray {
        val sb = StringBuilder()
        var bytes = 0
        var i = 0
        while (i < texto.length) {
            val cp = texto.codePointAt(i)
            i += Character.charCount(cp)
            val valido = cp <= 0xFFFF && !Character.isSurrogate(cp.toChar()) &&
                (cp == '\n'.code || cp >= 0x20) && cp != 0x7F && cp !in 0xFE00..0xFE0F && cp != 0x200D
            if (!valido) continue
            val tam = when {
                cp < 0x80 -> 1
                cp < 0x800 -> 2
                else -> 3
            }
            if (bytes + tam > maxBytes) break
            sb.appendCodePoint(cp)
            bytes += tam
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }
}

/** Lo que llega del reloj por TX, ya entendido. */
sealed class Recibido {
    abstract val bytes: ByteArray
    abstract val sumaBien: Boolean

    /** Respuesta (FD) a un comando: `bien` si el último byte es 01. */
    data class Respuesta(val categoria: Int, val sub: Int, val estado: ByteArray, override val bytes: ByteArray, override val sumaBien: Boolean) : Recibido() {
        val bien: Boolean get() = estado.isNotEmpty() && estado.last().toInt() == 1
    }

    /** Algo que el reloj manda por su cuenta (DF), p. ej. un botón de música. */
    data class Evento(val categoria: Int, val sub: Int, val datos: ByteArray, override val bytes: ByteArray, override val sumaBien: Boolean) : Recibido()

    /** Bytes que no forman una trama conocida (se guardan para estudiarlos). */
    data class Desconocido(override val bytes: ByteArray) : Recibido() {
        override val sumaBien: Boolean get() = false
    }

    /** Descripción corta para la consola y el registro. */
    fun describir(): String = when (this) {
        is Respuesta -> "respuesta ${Protocolo.nombre(categoria, sub)}: ${if (bien) "bien" else "estado ${Protocolo.hex(estado)}"}" +
            if (sumaBien) "" else " (suma no cuadra)"
        is Evento -> "evento ${Protocolo.nombre(categoria, sub)}" + (if (datos.isNotEmpty()) " ${Protocolo.hex(datos)}" else "") +
            if (sumaBien) "" else " (suma no cuadra)"
        is Desconocido -> "desconocido"
    }
}

/**
 * Junta los trozos que llegan por TX y devuelve tramas completas. Las notificaciones pueden partir
 * una trama en varias; si llega basura, se descarta hasta el próximo inicio (DF o FD).
 */
class Lector(private val maxTrama: Int = 4096) {
    private var buffer = ByteArray(0)

    fun agregar(trozo: ByteArray): List<Recibido> {
        buffer += trozo
        val salida = ArrayList<Recibido>()
        while (buffer.isNotEmpty()) {
            val primero = buffer[0].toInt() and 0xFF
            if (primero != Protocolo.INICIO_ENVIO && primero != Protocolo.INICIO_RESPUESTA) {
                val siguiente = buffer.indexOfFirst { val b = it.toInt() and 0xFF; b == Protocolo.INICIO_ENVIO || b == Protocolo.INICIO_RESPUESTA }
                val basura = if (siguiente < 0) buffer else buffer.copyOfRange(0, siguiente)
                salida += Recibido.Desconocido(basura)
                buffer = if (siguiente < 0) ByteArray(0) else buffer.copyOfRange(siguiente, buffer.size)
                continue
            }
            if (buffer.size < 3) break
            val largo = ((buffer[1].toInt() and 0xFF) shl 8) or (buffer[2].toInt() and 0xFF)
            val total = largo + 4
            if (largo < 2 || total > maxTrama) { // no es una trama de verdad: soltar este byte y seguir buscando
                salida += Recibido.Desconocido(buffer.copyOfRange(0, 1))
                buffer = buffer.copyOfRange(1, buffer.size)
                continue
            }
            if (buffer.size < total) break
            val trama = buffer.copyOfRange(0, total)
            buffer = buffer.copyOfRange(total, buffer.size)
            salida += interpretar(trama)
        }
        return salida
    }

    /** Bytes pendientes (trama incompleta). */
    val pendientes: Int get() = buffer.size

    fun vaciar() { buffer = ByteArray(0) }

    companion object {
        fun interpretar(t: ByteArray): Recibido {
            if (t.size < 6) return Recibido.Desconocido(t)
            val sumaBien = Protocolo.suma(t) == (t[3].toInt() and 0xFF)
            val cat = t[4].toInt() and 0xFF
            return when (t[0].toInt() and 0xFF) {
                Protocolo.INICIO_RESPUESTA -> Recibido.Respuesta(cat, t[5].toInt() and 0xFF, t.copyOfRange(6, t.size), t, sumaBien)
                else -> {
                    if (t.size < 9) return Recibido.Desconocido(t)
                    val sub = t[6].toInt() and 0xFF
                    val n = ((t[7].toInt() and 0xFF) shl 8) or (t[8].toInt() and 0xFF)
                    val datos = t.copyOfRange(9, minOf(t.size, 9 + n))
                    Recibido.Evento(cat, sub, datos, t, sumaBien)
                }
            }
        }
    }
}
