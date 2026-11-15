package com.kmanu.turnos.domain

import java.time.LocalDate

/** Salario mensual vigente desde [from]. */
data class SalaryChange(val from: LocalDate, val monthlySalary: Long)

/**
 * Salarios con su fecha de vigencia. Así, un aumento no cambia lo que ya se liquidó: cada hora
 * se valora con el salario vigente el día en que se trabajó.
 */
class SalaryHistory(changes: List<SalaryChange>) {
    val changes: List<SalaryChange> = changes.sortedBy { it.from }

    init {
        require(this.changes.isNotEmpty()) { "El historial de salarios no puede estar vacío." }
    }

    /** Salario vigente en [date]. Antes del primer cambio se usa el primero conocido. */
    fun at(date: LocalDate): Long = (changes.lastOrNull { it.from <= date } ?: changes.first()).monthlySalary

    /** Registra [salary] desde [from]; reemplaza los cambios que empezaban ese día o después. */
    fun withChange(from: LocalDate, salary: Long): SalaryHistory =
        SalaryHistory(changes.filter { it.from < from } + SalaryChange(from, salary))

    /** Texto para guardar: `2026-01-01=1750905;2027-01-01=1900000`. */
    fun encode(): String = changes.joinToString(";") { "${it.from}=${it.monthlySalary}" }

    override fun equals(other: Any?): Boolean = other is SalaryHistory && other.changes == changes
    override fun hashCode(): Int = changes.hashCode()
    override fun toString(): String = "SalaryHistory(${encode()})"

    companion object {
        /** Fecha anterior a cualquier turno: el salario aplica a todo el historial. */
        val SINCE_ALWAYS: LocalDate = LocalDate.of(2000, 1, 1)

        fun fixed(monthlySalary: Long) = SalaryHistory(listOf(SalaryChange(SINCE_ALWAYS, monthlySalary)))

        /** `null` si el texto está vacío o dañado. */
        fun decode(text: String): SalaryHistory? = runCatching {
            text.split(';')
                .filter(String::isNotBlank)
                .map { entry ->
                    val (date, salary) = entry.split('=')
                    SalaryChange(LocalDate.parse(date.trim()), salary.trim().toLong())
                }
                .takeIf { it.isNotEmpty() }
                ?.let(::SalaryHistory)
        }.getOrNull()
    }
}
