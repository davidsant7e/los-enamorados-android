package com.davidsant7e.enamorados.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Duotono de los Arcanos: azul noche y durazno. */
val AzulNoche = Color(0xFF1F2A5C)
val Durazno = Color(0xFFF4A582)

private val Claro = lightColorScheme(
    primary = AzulNoche,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1F5),
    onPrimaryContainer = AzulNoche,
    secondary = Color(0xFFB4532A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFDE3D6),
    onSecondaryContainer = Color(0xFF4A1E0A),
    background = Color(0xFFFBF8F6),
    surface = Color(0xFFFBF8F6),
)

private val Oscuro = darkColorScheme(
    primary = Durazno,
    onPrimary = AzulNoche,
    primaryContainer = Color(0xFF2E3B78),
    onPrimaryContainer = Color(0xFFFDE3D6),
    secondary = Durazno,
    onSecondary = AzulNoche,
    secondaryContainer = Color(0xFF5A3424),
    onSecondaryContainer = Color(0xFFFDE3D6),
    background = Color(0xFF12183A),
    surface = Color(0xFF12183A),
)

@Composable
fun TemaEnamorados(contenido: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Oscuro else Claro, content = contenido)
}

/** Colores legibles para el estado de una lectura, en claro y en oscuro. */
@Composable
fun colorTono(tono: com.davidsant7e.enamorados.salud.Tono): Color {
    val oscuro = isSystemInDarkTheme()
    return when (tono) {
        com.davidsant7e.enamorados.salud.Tono.BIEN -> if (oscuro) Color(0xFF8FD694) else Color(0xFF2E7D32)
        com.davidsant7e.enamorados.salud.Tono.OJO -> if (oscuro) Color(0xFFFFC870) else Color(0xFF8A5A00)
        com.davidsant7e.enamorados.salud.Tono.ALERTA -> if (oscuro) Color(0xFFFF8A80) else Color(0xFFC62828)
        com.davidsant7e.enamorados.salud.Tono.NEUTRO -> MaterialTheme.colorScheme.onSurface
    }
}

/** Zonas de cardio: de azul suave (1) a rojo (5), pasando por el durazno de los Arcanos. */
fun colorZona(numero: Int): Color = when (numero) {
    1 -> Color(0xFF8FA6D8)
    2 -> Color(0xFF6BAF73)
    3 -> Color(0xFFE8C04A)
    4 -> Durazno
    else -> Color(0xFFD9534F)
}
