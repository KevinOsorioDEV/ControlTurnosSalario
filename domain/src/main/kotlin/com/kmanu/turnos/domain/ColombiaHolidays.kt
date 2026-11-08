package com.kmanu.turnos.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.temporal.TemporalAdjusters

/** Festivos de Colombia: fechas fijas, Ley Emiliani (Ley 51 de 1983) y los ligados a la Pascua. */
object ColombiaHolidays {
    private val cache = HashMap<Int, Set<LocalDate>>()

    fun isHoliday(date: LocalDate): Boolean = date in holidays(date.year)

    /** Domingo o festivo: los días que llevan recargo dominical. */
    fun isSundayOrHoliday(date: LocalDate): Boolean =
        date.dayOfWeek == DayOfWeek.SUNDAY || isHoliday(date)

    @Synchronized
    fun holidays(year: Int): Set<LocalDate> = cache.getOrPut(year) { compute(year) }

    private fun compute(year: Int): Set<LocalDate> {
        val easter = easterSunday(year)
        val fixed = listOf(
            LocalDate.of(year, Month.JANUARY, 1),
            LocalDate.of(year, Month.MAY, 1),
            LocalDate.of(year, Month.JULY, 20),
            LocalDate.of(year, Month.AUGUST, 7),
            LocalDate.of(year, Month.DECEMBER, 8),
            LocalDate.of(year, Month.DECEMBER, 25),
            easter.minusDays(3), // Jueves Santo
            easter.minusDays(2), // Viernes Santo
        )
        val movedToMonday = listOf(
            LocalDate.of(year, Month.JANUARY, 6),
            LocalDate.of(year, Month.MARCH, 19),
            LocalDate.of(year, Month.JUNE, 29),
            LocalDate.of(year, Month.AUGUST, 15),
            LocalDate.of(year, Month.OCTOBER, 12),
            LocalDate.of(year, Month.NOVEMBER, 1),
            LocalDate.of(year, Month.NOVEMBER, 11),
        ).map { it.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)) }
        val easterBased = listOf(
            easter.plusDays(43), // Ascensión del Señor
            easter.plusDays(64), // Corpus Christi
            easter.plusDays(71), // Sagrado Corazón
        )
        return (fixed + movedToMonday + easterBased).toSet()
    }

    /** Algoritmo de Butcher para el calendario gregoriano. */
    internal fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }
}
