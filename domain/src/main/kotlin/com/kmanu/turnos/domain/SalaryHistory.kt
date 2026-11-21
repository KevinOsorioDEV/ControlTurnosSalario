package com.kmanu.turnos.domain

import java.time.LocalDate

/**
 * Salario mensual vigente desde [from].
 *
 * @property minimumWage si es `true`, el salario es el mínimo de cada año según el decreto y
 * [monthlySalary] no se usa: sube solo cada enero.
 */
data class SalaryChange(val from: LocalDate, val monthlySalary: Long, val minimumWage: Boolean = false) {
    fun salaryAt(date: LocalDate): Long = if (minimumWage) LegalPay.at(date).minimumWage else monthlySalary

    companion object {
        fun minimumWage(from: LocalDate) = SalaryChange(from, 0, minimumWage = true)
    }
}

/**
 * Salarios con su fecha de vigencia. Así, un aumento no cambia lo que ya se liquidó: cada hora
 * se valora con el salario vigente el día en que se trabajó.
 */
class SalaryHistory(changes: List<SalaryChange>) {
    val changes: List<SalaryChange> = changes.sortedBy { it.from }

    init {
        require(this.changes.isNotEmpty()) { "El historial de salarios no puede estar vacío." }
    }

    /** Cambio vigente en [date]. Antes del primer cambio se usa el primero conocido. */
    fun changeAt(date: LocalDate): SalaryChange = changes.lastOrNull { it.from <= date } ?: changes.first()

    /** Salario vigente en [date]. */
    fun at(date: LocalDate): Long = changeAt(date).salaryAt(date)

    /** Registra [change]; reemplaza el que empezaba ese mismo día. Los demás se conservan. */
    fun withChange(change: SalaryChange): SalaryHistory =
        SalaryHistory(changes.filter { it.from != change.from } + change)

    fun withChange(from: LocalDate, salary: Long): SalaryHistory = withChange(SalaryChange(from, salary))

    /** Quita el cambio que empieza en [from]. `null` si es el único: siempre debe quedar un salario. */
    fun without(from: LocalDate): SalaryHistory? =
        changes.filter { it.from != from }.takeIf { it.isNotEmpty() }?.let(::SalaryHistory)

    /** Texto para guardar: `2000-01-01=minimo;2027-01-01=1900000`. */
    fun encode(): String = changes.joinToString(";") {
        "${it.from}=${if (it.minimumWage) MINIMUM_WAGE else it.monthlySalary}"
    }

    override fun equals(other: Any?): Boolean = other is SalaryHistory && other.changes == changes
    override fun hashCode(): Int = changes.hashCode()
    override fun toString(): String = "SalaryHistory(${encode()})"

    companion object {
        private const val MINIMUM_WAGE = "minimo"

        /** Fecha anterior a cualquier turno: el salario aplica a todo el historial. */
        val SINCE_ALWAYS: LocalDate = LocalDate.of(2000, 1, 1)

        fun fixed(monthlySalary: Long) = SalaryHistory(listOf(SalaryChange(SINCE_ALWAYS, monthlySalary)))

        /** `null` si el texto está vacío o dañado. */
        fun decode(text: String): SalaryHistory? = runCatching {
            text.split(';')
                .filter(String::isNotBlank)
                .map { entry ->
                    val (date, salary) = entry.split('=').map(String::trim)
                    val from = LocalDate.parse(date)
                    if (salary == MINIMUM_WAGE) SalaryChange.minimumWage(from) else SalaryChange(from, salary.toLong())
                }
                .takeIf { it.isNotEmpty() }
                ?.let(::SalaryHistory)
        }.getOrNull()
    }
}
