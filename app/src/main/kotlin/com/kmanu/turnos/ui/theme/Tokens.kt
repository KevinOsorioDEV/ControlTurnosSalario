package com.kmanu.turnos.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/** Escala de espaciado. Las pantallas usan estos valores en lugar de literales `.dp`. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val ms = 12.dp
    val md = 16.dp
    val lg = 24.dp

    /** Espacio al final de una lista para que el botón flotante no tape la última fila. */
    val fabClearance = 96.dp
}

/** Esquinas: tarjeta principal, tarjetas de lista y controles. */
object Radius {
    val hero = 24.dp
    val card = 18.dp
    val control = 16.dp
    val pill = 8.dp
}

object Sizes {
    val action = 56.dp
}

/** Toda la app se mueve con las mismas duraciones y la misma curva. */
object Motion {
    const val SHORT = 150
    const val MEDIUM = 300

    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> standard(duration: Int = MEDIUM): FiniteAnimationSpec<T> = tween(duration, easing = Emphasized)
}

/** Números de ancho fijo: las cifras no "bailan" al cambiar y se alinean en columnas. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")
