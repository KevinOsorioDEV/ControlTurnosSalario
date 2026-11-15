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

        val custom = PaySettings(SalaryHistory.fixed(1_750_905), customAllowance = 100_000)
        assertThat(PayrollCalculator.liquidate(emptyList(), custom, PayPeriod(YearMonth.of(2026, 11))).allowance).isEqualTo(100_000)
    }

    @Test
    fun `solo se recorren los turnos cercanos al corte`() {
        val old = Shift("viejo", at("2025-01-10T08:00"), at("2025-01-10T16:00"), at("2025-01-10T16:00"))
        val near = Shift("cerca", at("2026-10-15T22:00"), at("2026-10-16T06:00"), at("2026-10-16T06:00"))
        val open = Shift("abierto", at("2026-11-10T08:00"), null, at("2026-11-10T16:00"))
        assertThat(PayrollCalculator.shiftsIn(listOf(old, near, open), PayPeriod(YearMonth.of(2026, 11)))).containsExactly(near)
    }
}
