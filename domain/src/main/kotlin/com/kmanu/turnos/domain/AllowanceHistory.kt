package com.kmanu.turnos.domain

import java.time.LocalDate

/**
 * Auxilio de transporte o conectividad vigente desde [from].
 *
 * @property amount monto fijo que paga la empresa; `null` sigue el valor de ley de cada año.
 */
data class AllowanceChange(val from: LocalDate, val amount: Long?) {
    val isLegal: Boolean get() = amount == null

    /** Auxilio en [date] para quien gana [monthlySalary] ese día. */
    fun allowanceAt(date: LocalDate, monthlySalary: Long): Long = amount ?: LegalPay.allowanceFor(monthlySalary, date)

    companion object {
        fun legal(from: LocalDate) = AllowanceChange(from, null)
    }
}

/**
 * Auxilios con su fecha de vigencia, igual que el salario: cambiar el auxilio desde enero no
 * altera lo que ya se liquidó. Cada corte usa el auxilio vigente el día de su pago.
 */
class AllowanceHistory(changes: List<AllowanceChange>) {
    val changes: List<AllowanceChange> = changes.sortedBy { it.from }

    init {
        require(this.changes.isNotEmpty()) { "El historial del auxilio no puede estar vacío." }
    }

    /** Tramo vigente en [date]. Antes del primero se usa el primero conocido. */
    fun changeAt(date: LocalDate): AllowanceChange = changes.lastOrNull { it.from <= date } ?: changes.first()

    fun at(date: LocalDate, monthlySalary: Long): Long = changeAt(date).allowanceAt(date, monthlySalary)

    /** Registra [change]; reemplaza el que empezaba ese mismo día. */
    fun withChange(change: AllowanceChange): AllowanceHistory =
        AllowanceHistory(changes.filter { it.from != change.from } + change)

    /** Quita el tramo que empieza en [from]. `null` si es el único: siempre debe quedar uno. */
    fun without(from: LocalDate): AllowanceHistory? =
        changes.filter { it.from != from }.takeIf { it.isNotEmpty() }?.let(::AllowanceHistory)

    /** Texto para guardar: `2000-01-01=ley;2027-01-01=300000`. */
    fun encode(): String = changes.joinToString(";") { "${it.from}=${it.amount ?: LEGAL_TEXT}" }

    override fun equals(other: Any?): Boolean = other is AllowanceHistory && other.changes == changes
    override fun hashCode(): Int = changes.hashCode()
    override fun toString(): String = "AllowanceHistory(${encode()})"

    companion object {
        private const val LEGAL_TEXT = "ley"

        /** Sigue la ley en todas las fechas: lo normal para quien no lo ha cambiado. */
        val LEGAL = AllowanceHistory(listOf(AllowanceChange.legal(SalaryHistory.SINCE_ALWAYS)))

        fun fixed(amount: Long) = AllowanceHistory(listOf(AllowanceChange(SalaryHistory.SINCE_ALWAYS, amount)))

        /** `null` si el texto está vacío o dañado. */
        fun decode(text: String): AllowanceHistory? = runCatching {
            text.split(';')
                .filter(String::isNotBlank)
                .map { entry ->
                    val parts = entry.split('=').map(String::trim)
                    require(parts.size == 2) { "Tramo dañado: $entry" }
                    val (date, amount) = parts
                    val value = if (amount == LEGAL_TEXT) null else amount.toLong().also { require(it >= 0) { "Auxilio negativo: $entry" } }
                    AllowanceChange(LocalDate.parse(date), value)
                }
                .takeIf { it.isNotEmpty() && it.distinctBy(AllowanceChange::from).size == it.size }
                ?.let(::AllowanceHistory)
        }.getOrNull()
    }
}
