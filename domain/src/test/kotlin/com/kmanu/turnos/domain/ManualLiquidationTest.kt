package com.kmanu.turnos.domain

import com.google.common.truth.Truth.assertThat
import java.math.BigDecimal
import java.time.YearMonth
import org.junit.Test

class ManualLiquidationTest {
    private val period = PayPeriod(YearMonth.of(2026, 11))

    private fun input(
        hours: Map<Concept, String>,
        days: Int = 30,
        sundayRate: String = "0.90",
        monthlyHours: Int = 210,
    ) = ManualInput(
        monthlySalary = 2_800_000,
        monthlyAllowance = 249_095,
        daysWorked = days,
        monthlyHours = monthlyHours,
        sundayRate = BigDecimal(sundayRate),
        hours = hours.mapValues { BigDecimal(it.value) },
    )

    @Test
    fun `da lo mismo que el corte real de octubre-noviembre`() {
        val result = PayrollCalculator.liquidateManual(
            input(mapOf(Concept.SUNDAY_DAY to "17.5", Concept.SUNDAY_NIGHT to "6")),
            period,
        )
        assertThat(result.noveltiesTotal).isEqualTo(310_000)
        assertThat(result.earned).isEqualTo(3_110_000)
        assertThat(result.healthAndPension).isEqualTo(248_800)
        assertThat(result.net).isEqualTo(3_110_295)
    }

    @Test
    fun `reproduce el Excel original usando sus porcentajes`() {
        // La celda B15 del Excel usa 115 %: con recargo dominical de 80 % da ese mismo factor.
        val result = PayrollCalculator.liquidateManual(
            input(mapOf(Concept.SUNDAY_NIGHT to "6"), sundayRate = "0.80"),
            period,
        )
        assertThat(result.noveltiesTotal).isEqualTo(92_000)
    }

    @Test
    fun `paga cada concepto con su factor`() {
        val all = Concept.entries.associateWith { "1" }
        val result = PayrollCalculator.liquidateManual(input(all), period)
        val amounts = result.lines.associate { it.concept to it.amount }
        assertThat(amounts[Concept.EXTRA_DAY]).isEqualTo(16_667)
        assertThat(amounts[Concept.EXTRA_NIGHT]).isEqualTo(23_333)
        assertThat(amounts[Concept.EXTRA_DAY_SUNDAY]).isEqualTo(28_667)
        assertThat(amounts[Concept.EXTRA_NIGHT_SUNDAY]).isEqualTo(35_333)
        assertThat(amounts[Concept.NIGHT]).isEqualTo(4_667)
        assertThat(amounts[Concept.SUNDAY_DAY]).isEqualTo(12_000)
        assertThat(amounts[Concept.SUNDAY_NIGHT]).isEqualTo(16_667)
    }

    @Test
    fun `prorratea salario y auxilio por dias pero no el valor hora`() {
        val result = PayrollCalculator.liquidateManual(
            input(mapOf(Concept.SUNDAY_DAY to "1"), days = 15),
            period,
        )
        assertThat(result.salary).isEqualTo(1_400_000)
        assertThat(result.allowance).isEqualTo(124_548)
        assertThat(result.noveltiesTotal).isEqualTo(12_000)
    }

    @Test
    fun `respeta el divisor indicado`() {
        val result = PayrollCalculator.liquidateManual(
            input(mapOf(Concept.SUNDAY_DAY to "1"), monthlyHours = 220),
            period,
        )
        assertThat(result.noveltiesTotal).isEqualTo(11_455)
    }

    @Test
    fun `sin horas solo liquida salario y auxilio`() {
        val result = PayrollCalculator.liquidateManual(input(emptyMap()), period)
        assertThat(result.lines).isEmpty()
        assertThat(result.net).isEqualTo(2_800_000 + 249_095 - 224_000)
    }

    @Test
    fun `calcula el valor de una hora`() {
        assertThat(PayrollCalculator.hourValue(2_800_000, 210, Concept.SUNDAY_NIGHT, BigDecimal("0.90"))).isEqualTo(16_667)
    }
}
