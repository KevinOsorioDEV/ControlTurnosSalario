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

    private val VALUES = sortedMapOf(
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

    /**
     * Valor personalizado que hay que guardar, o `null` si el auxilio debe seguir el valor de ley.
     * Así, quien no lo ha cambiado sigue la ley aunque cambien su salario o el año.
     */
    fun customAllowanceToStore(
        allowance: Long,
        salary: Long,
        previousCustom: Long?,
        previousSalary: Long,
        date: LocalDate,
    ): Long? {
        val untouched = previousCustom == null && allowance == allowanceFor(previousSalary, date)
        return if (untouched || allowance == allowanceFor(salary, date)) null else allowance
    }
}
