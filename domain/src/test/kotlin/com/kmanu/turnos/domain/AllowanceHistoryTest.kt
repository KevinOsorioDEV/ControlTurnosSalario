package com.kmanu.turnos.domain

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Test

class AllowanceHistoryTest {
    private val salary = SalaryHistory.fixed(2_800_000)

    @Test
    fun `sin cambios sigue el valor de ley de cada fecha`() {
        val legal = AllowanceHistory.LEGAL
        val date = LocalDate.of(2026, 11, 30)
        assertThat(legal.at(date, 2_800_000)).isEqualTo(LegalPay.allowanceFor(2_800_000, date))
    }

    @Test
    fun `un monto fijo desde enero no cambia los cortes anteriores`() {
        val history = AllowanceHistory.LEGAL.withChange(AllowanceChange(LocalDate.of(2027, 1, 1), 300_000))
        val settings = PaySettings(salary, history)

        val november = PayrollCalculator.liquidate(emptyList(), settings, PayPeriod(YearMonth.of(2026, 11)))
        val january = PayrollCalculator.liquidate(emptyList(), settings, PayPeriod(YearMonth.of(2027, 1)))

        assertThat(november.allowance).isEqualTo(LegalPay.allowanceFor(2_800_000, LocalDate.of(2026, 11, 30)))
        assertThat(january.allowance).isEqualTo(300_000)
    }

    @Test
    fun `volver al valor de ley desde una fecha`() {
        val history = AllowanceHistory.fixed(100_000).withChange(AllowanceChange.legal(LocalDate.of(2027, 1, 1)))
        assertThat(history.at(LocalDate.of(2026, 12, 31), 2_800_000)).isEqualTo(100_000)
        assertThat(history.changeAt(LocalDate.of(2027, 2, 1)).amount).isNull()
    }

    @Test
    fun `se guarda y se lee igual`() {
        val history = AllowanceHistory.LEGAL
            .withChange(AllowanceChange(LocalDate.of(2027, 1, 1), 300_000))
            .withChange(AllowanceChange.legal(LocalDate.of(2028, 1, 1)))
        assertThat(AllowanceHistory.decode(history.encode())).isEqualTo(history)
    }

    @Test
    fun `texto danado no se lee`() {
        assertThat(AllowanceHistory.decode("2027-01-01=abc")).isNull()
        assertThat(AllowanceHistory.decode("")).isNull()
    }

    @Test
    fun `no se puede quitar el unico tramo`() {
        assertThat(AllowanceHistory.LEGAL.without(SalaryHistory.SINCE_ALWAYS)).isNull()
    }
}
