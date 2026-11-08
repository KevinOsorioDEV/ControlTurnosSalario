package com.kmanu.turnos.domain

import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.YearMonth
import org.junit.Test

class RealCutoffTest {
    private fun shift(start: String, end: String, scheduledEnd: String = end) = Shift(
        id = start,
        start = LocalDateTime.parse(start),
        end = LocalDateTime.parse(end),
        scheduledEnd = LocalDateTime.parse(scheduledEnd),
    )

    @Test
    fun `corte octubre-noviembre 2026 con el domingo 8 de 4 horas`() {
        val shifts = listOf(
            shift("2026-10-16T06:00", "2026-10-16T14:30"), // viernes ordinario: sin novedad
            shift("2026-10-18T07:00", "2026-10-18T11:00"), // domingo: 4 h
            shift("2026-11-01T17:00", "2026-11-02T01:00"), // domingo a festivo: 2 h diurnas, 6 nocturnas
            shift("2026-11-02T06:00", "2026-11-02T13:30"), // festivo: 7,5 h
            shift("2026-11-08T10:00", "2026-11-08T14:00"), // domingo: 4 h
        )

        val result = PayrollCalculator.liquidate(
            shifts,
            PaySettings(2_800_000, 249_095),
            PayPeriod(YearMonth.of(2026, 11)),
        )

        assertThat(result.lines.first { it.concept == Concept.SUNDAY_DAY }.minutes).isEqualTo(17 * 60 + 30)
        assertThat(result.lines.first { it.concept == Concept.SUNDAY_NIGHT }.minutes).isEqualTo(6 * 60)
        assertThat(result.noveltiesTotal).isEqualTo(310_000)
        assertThat(result.earned).isEqualTo(3_110_000)
        assertThat(result.healthAndPension).isEqualTo(248_800)
        assertThat(result.net).isEqualTo(3_110_295)
    }

    @Test
    fun `sin almuerzo configurado no se descuenta nada`() {
        assertThat(BreakPolicy().minutesFor(9 * 60)).isEqualTo(0)
    }

    @Test
    fun `el almuerzo solo aplica a turnos largos`() {
        val policy = BreakPolicy(minutes = 60, appliesOverMinutes = 8 * 60)
        assertThat(policy.minutesFor(8 * 60)).isEqualTo(0)
        assertThat(policy.minutesFor(9 * 60)).isEqualTo(60)
    }

    @Test
    fun `un turno que se cruza con otro no es valido`() {
        val first = shift("2026-11-09T06:00", "2026-11-09T13:30")
        val second = shift("2026-11-09T13:00", "2026-11-09T15:00")
        val now = LocalDateTime.parse("2026-11-10T00:00")
        assertThat(ShiftValidation.validate(second, listOf(first), now)).isNotNull()
        assertThat(ShiftValidation.validate(first, listOf(first), now)).isNull()
    }
}
