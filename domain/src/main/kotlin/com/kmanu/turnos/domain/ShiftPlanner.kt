package com.kmanu.turnos.domain

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/** Ubica un horario programado en fechas concretas. */
object ShiftPlanner {
    private const val ROUNDING_MINUTES = 30L

    /** Tiempo después de la salida programada (y su margen) en que avisamos que no se ha marcado. */
    const val REMINDER_AFTER_MINUTES = 30L

    /** Pasado este tiempo después de la salida, lo más probable es que se olvidó marcarla. */
    const val FORGOTTEN_AFTER_MINUTES = 3 * 60L

    /**
     * Turno programado que corresponde a [reference]: el más cercano que todavía no haya
     * terminado. Así, marcar a las 11:55 p. m. para un turno de 12:00 a. m. toma la medianoche
     * que viene, y marcar a la 1:00 a. m. en un turno de 10:00 p. m. toma el de la noche anterior.
     */
    fun planFor(reference: LocalDateTime, start: LocalTime, end: LocalTime): Pair<LocalDateTime, LocalDateTime> =
        (-1L..1L)
            .map { offset ->
                val plannedStart = reference.toLocalDate().plusDays(offset).atTime(start)
                plannedStart to endAfter(plannedStart, end)
            }
            .filter { (_, plannedEnd) -> plannedEnd.isAfter(reference) }
            .minBy { (plannedStart, _) -> Duration.between(plannedStart, reference).abs() }

    /** Primera vez que ocurre [end] después de [start]. */
    fun endAfter(start: LocalDateTime, end: LocalTime): LocalDateTime {
        val sameDay = start.toLocalDate().atTime(end)
        return if (sameDay.isAfter(start)) sameDay else sameDay.plusDays(1)
    }

    /** Sugerencia inicial de entrada: la media hora más cercana. */
    fun roundedToHalfHour(time: LocalDateTime): LocalDateTime {
        val minutes = time.hour * 60L + time.minute
        val rounded = (minutes + ROUNDING_MINUTES / 2) / ROUNDING_MINUTES * ROUNDING_MINUTES
        return time.toLocalDate().atStartOfDay().plusMinutes(rounded)
    }

    /** Si la salida se pasó del margen y hay que preguntar si las extras fueron autorizadas. */
    fun needsExtraConfirmation(shift: Shift, exit: LocalDateTime): Boolean =
        shift.scheduledStart != null && exit.isAfter(shift.scheduledEnd.plusMinutes(shift.marginMinutes.toLong()))

    /** Cuándo recordar que falta marcar la salida de [shift]. */
    fun reminderAt(shift: Shift): LocalDateTime =
        shift.scheduledEnd.plusMinutes(shift.marginMinutes + REMINDER_AFTER_MINUTES)

    /**
     * Cuándo avisar que la salida se acerca, o `null` si no aplica: aviso desactivado
     * ([minutesBefore] en 0), turno cerrado, o la hora del aviso ya pasó (por ejemplo, si se
     * marcó la entrada tarde en un turno corto).
     */
    fun exitNoticeAt(shift: Shift, minutesBefore: Int, now: LocalDateTime): LocalDateTime? {
        if (minutesBefore <= 0 || shift.end != null) return null
        return shift.scheduledEnd.minusMinutes(minutesBefore.toLong()).takeIf { it.isAfter(now) }
    }

    /** Si el turno sigue abierto mucho después de su salida: hay que preguntar a qué hora salió. */
    fun looksForgotten(shift: Shift, now: LocalDateTime): Boolean =
        shift.end == null && now.isAfter(shift.scheduledEnd.plusMinutes(shift.marginMinutes + FORGOTTEN_AFTER_MINUTES))
}
