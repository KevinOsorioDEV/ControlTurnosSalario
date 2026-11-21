package com.kmanu.turnos.domain

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import org.junit.Test

class SalaryHistoryTest {
    private val january = LocalDate.of(2027, 1, 1)
    private val history = SalaryHistory.fixed(2_000_000).withChange(january, 2_200_000)

    private fun at(text: String) = LocalDateTime.parse(text)

    @Test
    fun `cada fecha usa el salario vigente`() {
        assertThat(history.at(LocalDate.of(2026, 12, 31))).isEqualTo(2_000_000)
        assertThat(history.at(january)).isEqualTo(2_200_000)
        assertThat(history.at(LocalDate.of(1990, 1, 1))).isEqualTo(2_000_000)
    }

    @Test
    fun `un cambio el mismo dia reemplaza al anterior`() {
        val corrected = history.withChange(january, 2_300_000)
        assertThat(corrected.changes).hasSize(2)
        assertThat(corrected.at(january)).isEqualTo(2_300_000)
    }

    @Test
    fun `se guarda y se lee igual`() {
        assertThat(SalaryHistory.decode(history.encode())).isEqualTo(history)
        assertThat(SalaryHistory.decode("")).isNull()
        assertThat(SalaryHistory.decode("basura")).isNull()
    }

    @Test
    fun `un aumento no cambia los cortes anteriores`() {
        // Domingo 20 de diciembre de 2026 de día: recargo dominical.
        val shift = Shift("a", at("2026-12-20T08:00"), at("2026-12-20T10:00"), at("2026-12-20T10:00"))
        val before = PayrollCalculator.liquidate(listOf(shift), PaySettings(SalaryHistory.fixed(2_000_000), 0), PayPeriod(YearMonth.of(2027, 1)))
        val after = PayrollCalculator.liquidate(listOf(shift), PaySettings(history, 0), PayPeriod(YearMonth.of(2027, 1)))

        // Las horas de diciembre se valoran con el salario de diciembre…
        assertThat(after.noveltiesTotal).isEqualTo(before.noveltiesTotal)
        // …y el salario del mes es el de enero, mes del pago.
        assertThat(after.salary).isEqualTo(2_200_000)
    }

    @Test
    fun `el auxilio de ley sigue el salario de cada fecha`() {
        val settings = PaySettings(SalaryHistory.fixed(1_750_905))
        val liquidation = PayrollCalculator.liquidate(emptyList(), settings, PayPeriod(YearMonth.of(2026, 11)))
        assertThat(liquidation.allowance).isEqualTo(249_095)

        val custom = PaySettings(SalaryHistory.fixed(1_750_905), monthlyAllowance = 100_000)
        assertThat(PayrollCalculator.liquidate(emptyList(), custom, PayPeriod(YearMonth.of(2026, 11))).allowance).isEqualTo(100_000)
    }

    @Test
    fun `solo se recorren los turnos cercanos al corte`() {
        val old = Shift("viejo", at("2025-01-10T08:00"), at("2025-01-10T16:00"), at("2025-01-10T16:00"))
        val near = Shift("cerca", at("2026-10-15T22:00"), at("2026-10-16T06:00"), at("2026-10-16T06:00"))
        val open = Shift("abierto", at("2026-11-10T08:00"), null, at("2026-11-10T16:00"))
        assertThat(PayrollCalculator.shiftsIn(listOf(old, near, open), PayPeriod(YearMonth.of(2026, 11)))).containsExactly(near)
    }

    @Test
    fun `el salario minimo sube solo con el decreto de cada año`() {
        val minimum = SalaryHistory(listOf(SalaryChange.minimumWage(SalaryHistory.SINCE_ALWAYS)))
        assertThat(minimum.at(LocalDate.of(2025, 6, 1))).isEqualTo(1_423_500)
        assertThat(minimum.at(LocalDate.of(2026, 6, 1))).isEqualTo(1_750_905)
    }

    @Test
    fun `corte que cruza el año con el minimo usa cada valor en su fecha`() {
        // Corte del 16 dic 2025 al 15 ene 2026, pagado en enero de 2026.
        val minimum = SalaryHistory(listOf(SalaryChange.minimumWage(SalaryHistory.SINCE_ALWAYS)))
        val december = Shift("dic", at("2025-12-21T08:00"), at("2025-12-21T10:00"), at("2025-12-21T10:00"))
        val january = Shift("ene", at("2026-01-11T08:00"), at("2026-01-11T10:00"), at("2026-01-11T10:00"))
        val period = PayPeriod(YearMonth.of(2026, 1))

        val both = PayrollCalculator.liquidate(listOf(december, january), PaySettings(minimum), period)
        val onlyDecember = PayrollCalculator.liquidate(listOf(december), PaySettings(SalaryHistory.fixed(1_423_500), 0), period)
        val onlyJanuary = PayrollCalculator.liquidate(listOf(january), PaySettings(SalaryHistory.fixed(1_750_905), 0), period)

        assertThat(both.noveltiesTotal).isEqualTo(onlyDecember.noveltiesTotal + onlyJanuary.noveltiesTotal)
        assertThat(both.salary).isEqualTo(1_750_905)
        assertThat(both.allowance).isEqualTo(249_095)
    }

    @Test
    fun `se puede pasar de monto fijo al minimo y quitar cambios`() {
        val mixed = history.withChange(SalaryChange.minimumWage(LocalDate.of(2027, 6, 1)))
        assertThat(mixed.at(LocalDate.of(2027, 7, 1))).isEqualTo(1_750_905)
        assertThat(SalaryHistory.decode(mixed.encode())).isEqualTo(mixed)

        val removed = mixed.without(january)
        assertThat(removed?.changes?.map { it.from }).containsExactly(SalaryHistory.SINCE_ALWAYS, LocalDate.of(2027, 6, 1)).inOrder()
        assertThat(SalaryHistory.fixed(1).without(SalaryHistory.SINCE_ALWAYS)).isNull()
    }
}
