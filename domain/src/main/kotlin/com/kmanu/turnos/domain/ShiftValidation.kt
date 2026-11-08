package com.kmanu.turnos.domain

import java.time.Duration
import java.time.LocalDateTime

/**
 * Almuerzo no pagado. Por defecto no hay: [minutes] en 0. Solo se aplica a los turnos
 * programados de más de [appliesOverMinutes], para no descontarlo en turnos cortos.
 */
data class BreakPolicy(val minutes: Int = 0, val appliesOverMinutes: Int = 8 * 60) {
    fun minutesFor(scheduledMinutes: Long): Int = if (scheduledMinutes > appliesOverMinutes) minutes else 0
}

object ShiftValidation {
    private const val MAX_SHIFT_HOURS = 24L

    /** Devuelve el error que impide guardar [shift], o `null` si es válido. */
    fun validate(shift: Shift, others: List<Shift>, now: LocalDateTime): String? {
        val effectiveEnd = shift.end ?: now
        return when {
            !effectiveEnd.isAfter(shift.start) -> "La salida debe ser posterior a la entrada."
            !shift.scheduledEnd.isAfter(shift.start) -> "La salida programada debe ser posterior a la entrada."
            Duration.between(shift.start, effectiveEnd).toHours() >= MAX_SHIFT_HOURS -> "Un turno no puede durar 24 horas o más."
            shift.breakMinutes < 0 -> "El almuerzo no puede ser negativo."
            shift.breakMinutes > 0 &&
                shift.breakMinutes >= Duration.between(shift.start, minOf(effectiveEnd, shift.scheduledEnd)).toMinutes() ->
                "El almuerzo debe ser menor que el tiempo programado."
            others.any { it.id != shift.id && overlaps(shift, effectiveEnd, it, now) } -> "Se cruza con otro turno registrado."
            else -> null
        }
    }

    private fun overlaps(shift: Shift, end: LocalDateTime, other: Shift, now: LocalDateTime): Boolean =
        shift.start < (other.end ?: now) && other.start < end
}
