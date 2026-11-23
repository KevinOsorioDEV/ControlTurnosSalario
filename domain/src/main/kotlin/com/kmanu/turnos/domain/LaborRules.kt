package com.kmanu.turnos.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Reglas laborales vigentes en una fecha (Colombia). Se resuelven por fecha porque cambian
 * a mitad de año y un mismo corte puede cruzar el cambio.
 *
 * @property monthlyHours divisor del valor hora (jornada semanal × 5).
 * @property sundayRate recargo dominical/festivo, como fracción (0.90 = 90 %).
 * @property nightStart hora en que empieza la jornada nocturna; termina a las 6:00 a. m.
 */
data class LaborRules(
    val monthlyHours: Int,
    val sundayRate: BigDecimal,
    val nightStart: LocalTime,
) {
    fun isNight(time: LocalTime): Boolean = time >= nightStart || time < NIGHT_END

    companion object {
        val NIGHT_END: LocalTime = LocalTime.of(6, 0)

        /** Ley 2101 de 2021 (jornada) y Ley 2466 de 2025 (dominical y jornada nocturna). */
        fun at(date: LocalDate): LaborRules = LaborRules(
            monthlyHours = when {
                // Ley 2101: 48 h semanales hasta el 14-07-2023 y luego una hora menos cada año.
                date < LocalDate.of(2023, 7, 15) -> 240
                date < LocalDate.of(2024, 7, 15) -> 235
                date < LocalDate.of(2025, 7, 15) -> 230
                date < LocalDate.of(2026, 7, 15) -> 220
                else -> 210
            },
            sundayRate = when {
                date < LocalDate.of(2025, 7, 1) -> BigDecimal("0.75")
                date < LocalDate.of(2026, 7, 1) -> BigDecimal("0.80")
                date < LocalDate.of(2027, 7, 1) -> BigDecimal("0.90")
                else -> BigDecimal("1.00")
            },
            nightStart = if (date < LocalDate.of(2025, 12, 25)) LocalTime.of(21, 0) else LocalTime.of(19, 0),
        )
    }
}
