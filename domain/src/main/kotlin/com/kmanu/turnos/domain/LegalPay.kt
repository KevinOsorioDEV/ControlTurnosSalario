package com.kmanu.turnos.domain

import java.time.LocalDate

/**
 * Valores que fija el Gobierno cada año. Un año sin decreto registrado usa el último conocido.
 *
 * 2026: Decretos 1469 y 1470 de 2025; el salario mínimo se mantuvo con el Decreto 159 de 2026.
 */
object LegalPay {
    /** Se paga a quienes ganan hasta esta cantidad de salarios mínimos. */
    private const val ALLOWANCE_LIMIT_IN_MINIMUM_WAGES = 2

    data class YearValues(val minimumWage: Long, val allowance: Long) {
        val allowanceSalaryLimit: Long get() = minimumWage * ALLOWANCE_LIMIT_IN_MINIMUM_WAGES
    }

    // Antes del primer año registrado se usan sus valores; por eso se guardan varios años atrás.
    private val VALUES = sortedMapOf(
        2020 to YearValues(minimumWage = 877_803, allowance = 102_854),
        2021 to YearValues(minimumWage = 908_526, allowance = 106_454),
        2022 to YearValues(minimumWage = 1_000_000, allowance = 117_172),
        2023 to YearValues(minimumWage = 1_160_000, allowance = 140_606),
        2024 to YearValues(minimumWage = 1_300_000, allowance = 162_000),
        2025 to YearValues(minimumWage = 1_423_500, allowance = 200_000),
        2026 to YearValues(minimumWage = 1_750_905, allowance = 249_095),
    )

    /** Último año con decreto registrado en la app. */
    val lastKnownYear: Int get() = VALUES.lastKey()

    /** `false` si para [year] se están usando los valores de un año anterior. */
    fun isKnown(year: Int): Boolean = year in VALUES

    fun at(date: LocalDate): YearValues = (VALUES.headMap(date.year + 1).values.lastOrNull() ?: VALUES.values.first())

    /** Auxilio de transporte o conectividad que corresponde por ley a [monthlySalary]. */
    fun allowanceFor(monthlySalary: Long, date: LocalDate): Long {
        val values = at(date)
        return if (monthlySalary <= values.allowanceSalaryLimit) values.allowance else 0
    }
}
