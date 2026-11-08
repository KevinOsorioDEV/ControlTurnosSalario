package com.kmanu.turnos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Esquema completo generado a partir del azul de marca #1F5FA8. Todos los roles están definidos
 * para que ninguna superficie caiga en los tonos morados por defecto de Material.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5FA8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5E3FF),
    onPrimaryContainer = Color(0xFF001C3B),
    inversePrimary = Color(0xFFA6C8FF),
    secondary = Color(0xFF555D6B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE2EE),
    onSecondaryContainer = Color(0xFF141B26),
    tertiary = Color(0xFF6A3FB5),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEADDFF),
    onTertiaryContainer = Color(0xFF24005A),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF1F3F7),
    onBackground = Color(0xFF14171F),
    surface = Color(0xFFF1F3F7),
    onSurface = Color(0xFF14171F),
    surfaceVariant = Color(0xFFE1E4EB),
    onSurfaceVariant = Color(0xFF555D6B),
    surfaceTint = Color(0xFF1F5FA8),
    inverseSurface = Color(0xFF2B2F38),
    inverseOnSurface = Color(0xFFEFF1F6),
    outline = Color(0xFF767D8A),
    outlineVariant = Color(0xFFC4C8D2),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF1F3F7),
    surfaceDim = Color(0xFFD8DBE2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F8FB),
    surfaceContainer = Color(0xFFECEEF3),
    surfaceContainerHigh = Color(0xFFE6E9EF),
    surfaceContainerHighest = Color(0xFFE1E4EB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA6C8FF),
    onPrimary = Color(0xFF10131A),
    primaryContainer = Color(0xFF00468A),
    onPrimaryContainer = Color(0xFFD5E3FF),
    inversePrimary = Color(0xFF1F5FA8),
    secondary = Color(0xFFAEB5C2),
    onSecondary = Color(0xFF242A35),
    secondaryContainer = Color(0xFF3B4250),
    onSecondaryContainer = Color(0xFFDDE2EE),
    tertiary = Color(0xFFD0BCFF),
    onTertiary = Color(0xFF38107F),
    tertiaryContainer = Color(0xFF512B9A),
    onTertiaryContainer = Color(0xFFEADDFF),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF10131A),
    onBackground = Color(0xFFE6E8EE),
    surface = Color(0xFF10131A),
    onSurface = Color(0xFFE6E8EE),
    surfaceVariant = Color(0xFF3B4250),
    onSurfaceVariant = Color(0xFFAEB5C2),
    surfaceTint = Color(0xFFA6C8FF),
    inverseSurface = Color(0xFFE6E8EE),
    inverseOnSurface = Color(0xFF2B2F38),
    outline = Color(0xFF8A919E),
    outlineVariant = Color(0xFF3B4250),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF363A44),
    surfaceDim = Color(0xFF10131A),
    surfaceContainerLowest = Color(0xFF0B0E14),
    surfaceContainerLow = Color(0xFF181C24),
    surfaceContainer = Color(0xFF262B35),
    surfaceContainerHigh = Color(0xFF2C313C),
    surfaceContainerHighest = Color(0xFF363A44),
)

private val TurnosShapes = Shapes(
    small = RoundedCornerShape(Radius.pill),
    medium = RoundedCornerShape(Radius.card),
    large = RoundedCornerShape(Radius.hero),
)

/** Color sólido de un estado, con su texto y el botón que va encima. */
@Immutable
data class StatusStyle(
    val container: Color,
    val content: Color,
    val actionContainer: Color = content,
    val actionContent: Color = container,
)

private val Ink = Color(0xFF14171F)
private val White = Color(0xFFFFFFFF)

/**
 * Colores de los estados del turno. Son sólidos e iguales en claro y oscuro: el color dice
 * qué está pasando, y siempre va acompañado de un texto.
 */
@Immutable
data class TurnosColors(
    /** Fondo de las tarjetas neutras. */
    val card: Color,
    val active: StatusStyle = StatusStyle(Color(0xFF1F5FA8), White, actionContainer = White, actionContent = Ink),
    val extra: StatusStyle = StatusStyle(Color(0xFFF5B700), Color(0xFF1B1A17), actionContainer = Ink, actionContent = White),
    val closed: StatusStyle = StatusStyle(Color(0xFF0B6E4F), White, actionContainer = White, actionContent = Ink),
    val night: StatusStyle = StatusStyle(Color(0xFF1E2A44), White),
    val holiday: StatusStyle = StatusStyle(Color(0xFF6A3FB5), White),
)

private val LightTurnosColors = TurnosColors(card = Color(0xFFFFFFFF))

// El azul noche se perdería contra la tarjeta oscura: en oscuro se aclara.
private val DarkTurnosColors = TurnosColors(card = Color(0xFF1C2029), night = StatusStyle(Color(0xFF3A4D7A), White))

private val LocalTurnosColors = staticCompositionLocalOf { LightTurnosColors }

object TurnosTheme {
    val colors: TurnosColors
        @Composable @ReadOnlyComposable get() = LocalTurnosColors.current
}

@Composable
fun TurnosTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTurnosColors provides if (darkTheme) DarkTurnosColors else LightTurnosColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            shapes = TurnosShapes,
            content = content,
        )
    }
}
