package com.davidsant7e.enamorados.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Preferencias de la pantalla: edad y pulso en reposo (zonas de cardio) y si las herramientas avanzadas están abiertas. */
class Ajustes(contexto: Context) {
    private val preferencias = contexto.applicationContext.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    var edad by mutableStateOf(preferencias.getInt("edad", 0).takeIf { it > 0 })
        private set
    var reposo by mutableStateOf(preferencias.getInt("reposo", 0).takeIf { it > 0 })
        private set
    var avanzadas by mutableStateOf(preferencias.getBoolean("avanzadas", false))
        private set

    fun guardarPerfil(nuevaEdad: Int?, nuevoReposo: Int?) {
        edad = nuevaEdad
        reposo = nuevoReposo
        preferencias.edit().putInt("edad", nuevaEdad ?: 0).putInt("reposo", nuevoReposo ?: 0).apply()
    }

    fun cambiarAvanzadas(abiertas: Boolean) {
        avanzadas = abiertas
        preferencias.edit().putBoolean("avanzadas", abiertas).apply()
    }
}
