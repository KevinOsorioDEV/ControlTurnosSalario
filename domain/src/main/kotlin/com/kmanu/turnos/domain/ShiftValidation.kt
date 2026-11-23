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
    private const val FUTURE_EXIT = "La salida no puede ser una hora que aún no llega."
    private const val OVERLAP = "Se cruza con otro turno registrado."

    /** Devuelve el error que impide guardar [shift], o `null` si es válido. */
    fun validate(shift: Shift, others: List<Shift>, now: LocalDateTime): String? {
        val effectiveEnd = shift.end ?: now
        return when {
            !effectiveEnd.isAfter(shift.start) -> "La salida debe ser posterior a la entrada."
            shift.end != null && shift.end.isAfter(now) -> FUTURE_EXIT
            !shift.scheduledEnd.isAfter(shift.start) -> "La salida programada debe ser posterior a la entrada."
            Duration.between(shift.start, effectiveEnd).toHours() >= MAX_SHIFT_HOURS -> "Un turno no puede durar 24 horas o más."
            else -> breakProblem(shift.breakMinutes, shift.scheduledStart ?: shift.start, shift.scheduledEnd)
                ?: if (others.any { it.id != shift.id && overlaps(shift, effectiveEnd, it, now) }) OVERLAP else null
        }
    }

    /**
     * Error que impide marcar la entrada ahora, o `null` si se puede. El almuerzo se revisa aquí
     * con la misma regla que al cerrar, para no dejar abierto un turno que luego no se pueda cerrar.
     */
    fun validateCheckIn(
        scheduledEnd: LocalDateTime,
        others: List<Shift>,
        now: LocalDateTime,
        scheduledStart: LocalDateTime? = null,
        breakMinutes: Int = 0,
    ): String? = when {
        others.any { it.end == null } -> "Ya tienes un turno abierto."
        !scheduledEnd.isAfter(now) -> "La salida programada debe ser posterior a la entrada."
        others.any { it.start < scheduledEnd && (it.end ?: now) > now } -> OVERLAP
        else -> breakProblem(breakMinutes, scheduledStart ?: now, scheduledEnd)
    }

    /**
     * El almuerzo se mide contra el turno programado, no contra lo trabajado: si se entra tarde o
     * se sale temprano, la nómina lo recorta a lo trabajado y el turno se debe poder cerrar.
     */
    private fun breakProblem(breakMinutes: Int, scheduledStart: LocalDateTime, scheduledEnd: LocalDateTime): String? = when {
        breakMinutes < 0 -> "El almuerzo no puede ser negativo."
        breakMinutes > 0 && breakMinutes >= Duration.between(scheduledStart, scheduledEnd).toMinutes() ->
            "El almuerzo debe ser menor que el tiempo programado."
        else -> null
    }

    /** Error que impide cerrar [open] con salida en [exit], o `null` si se puede. */
    fun validateCheckOut(open: Shift, exit: LocalDateTime, others: List<Shift>, now: LocalDateTime): String? = when {
        exit.isAfter(now) -> FUTURE_EXIT
        else -> validate(open.copy(end = exit), others, now)
    }

    private fun overlaps(shift: Shift, end: LocalDateTime, other: Shift, now: LocalDateTime): Boolean =
        shift.start < (other.end ?: now) && other.start < end
}
