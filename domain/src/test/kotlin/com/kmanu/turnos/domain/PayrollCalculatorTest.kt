package com.kmanu.turnos.domain

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import org.junit.Test

class PayrollCalculatorTest {
    private val salary = 2_800_000L
    private val settings = PaySettings(salary, 249_095L)

    private fun at(text: String) = LocalDateTime.parse(text)

    private fun shift(
        start: String,
        end: String,
        scheduledEnd: String = end,
        breakMinutes: Int = 0,
        compensatoryRest: Boolean = true,
    ) = Shift("id-$start", at(start), at(end), at(scheduledEnd), breakMinutes, compensatoryRest)

    private fun breakdown(shift: Shift) = PayrollCalculator.breakdown(shift, salary)

    @Test
    fun `reproduce el Excel con el recargo nocturno DF corregido`() {
        // Cuatro domingos del corte de noviembre 2026 → 21,5 h diurnas DF y 6 h nocturnas DF.
        val shifts = listOf(
            shift("2026-10-18T07:00", "2026-10-18T14:30"), // 7,5 h diurnas DF
            shift("2026-11-01T08:00", "2026-11-01T15:00"), // 7 h diurnas DF
            shift("2026-11-08T08:00", "2026-11-08T15:00"), // 7 h diurnas DF
            shift("2026-11-08T19:00", "2026-11-09T00:00"), // 5 h nocturnas DF
            shift("2026-10-25T23:00", "2026-10-26T00:00"), // 1 h nocturna DF
        )

        val result = PayrollCalculator.liquidate(shifts, settings, PayPeriod(YearMonth.of(2026, 11)))

        assertThat(result.workedMinutes).isEqualTo(27 * 60 + 30)
        assertThat(result.noveltiesTotal).isEqualTo(358_000)
        assertThat(result.earned).isEqualTo(3_158_000)
        assertThat(result.healthAndPension).isEqualTo(252_640)
        assertThat(result.net).isEqualTo(3_154_455)
    }

    @Test
    fun `dia ordinario sin horas nocturnas no genera recargos`() {
        val result = breakdown(shift("2026-11-10T08:00", "2026-11-10T15:00"))
        assertThat(result.workedMinutes).isEqualTo(420)
        assertThat(result.lines).isEmpty()
    }

    @Test
    fun `la jornada nocturna empieza a las siete de la noche`() {
        val result = breakdown(shift("2026-11-10T17:00", "2026-11-10T21:00"))
        assertThat(result.minutesOf(Concept.NIGHT)).isEqualTo(120)
        assertThat(result.total).isEqualTo(9_333) // 2 h × 4.666,67
    }

    @Test
    fun `lo trabajado despues de la salida programada es extra`() {
        val result = breakdown(shift("2026-11-10T09:00", "2026-11-10T20:00", "2026-11-10T17:00"))
        assertThat(result.minutesOf(Concept.EXTRA_DAY)).isEqualTo(120)
        assertThat(result.minutesOf(Concept.EXTRA_NIGHT)).isEqualTo(60)
        assertThat(result.total).isEqualTo(33_333 + 23_333)
    }

    @Test
    fun `extras en domingo usan los porcentajes vigentes`() {
        val result = breakdown(shift("2026-11-08T15:00", "2026-11-08T20:00", "2026-11-08T17:00"))
        assertThat(result.minutesOf(Concept.SUNDAY_DAY)).isEqualTo(120)
        assertThat(result.minutesOf(Concept.EXTRA_DAY_SUNDAY)).isEqualTo(120)
        assertThat(result.minutesOf(Concept.EXTRA_NIGHT_SUNDAY)).isEqualTo(60)
        assertThat(result.lines.first { it.concept == Concept.EXTRA_NIGHT_SUNDAY }.amount).isEqualTo(35_333)
        assertThat(result.lines.first { it.concept == Concept.EXTRA_DAY_SUNDAY }.amount).isEqualTo(57_333)
    }

    @Test
    fun `turno que cruza del sabado al domingo reparte por dia`() {
        val result = breakdown(shift("2026-11-07T22:00", "2026-11-08T02:00"))
        assertThat(result.minutesOf(Concept.NIGHT)).isEqualTo(120)
        assertThat(result.minutesOf(Concept.SUNDAY_NIGHT)).isEqualTo(120)
    }

    @Test
    fun `festivo cuenta como dominical`() {
        // Lunes 16 nov 2026: Independencia de Cartagena trasladado desde el 11.
        val result = breakdown(shift("2026-11-16T08:00", "2026-11-16T12:00"))
        assertThat(result.minutesOf(Concept.SUNDAY_DAY)).isEqualTo(240)
    }

    @Test
    fun `sin compensatorio se paga la hora ademas del recargo`() {
        val result = breakdown(shift("2026-11-08T08:00", "2026-11-08T10:00", compensatoryRest = false))
        assertThat(result.minutesOf(Concept.SUNDAY_DAY)).isEqualTo(120)
        assertThat(result.minutesOf(Concept.SUNDAY_NO_REST)).isEqualTo(120)
        assertThat(result.total).isEqualTo(24_000 + 26_667)
    }

    @Test
    fun `el descanso no pagado se descuenta del turno`() {
        val result = breakdown(shift("2026-11-08T08:00", "2026-11-08T16:00", breakMinutes = 60))
        assertThat(result.workedMinutes).isEqualTo(420)
        assertThat(result.minutesOf(Concept.SUNDAY_DAY)).isEqualTo(420)
    }

    @Test
    fun `turno abierto se calcula hasta el momento indicado`() {
        val open = Shift("o", at("2026-11-08T08:00"), null, at("2026-11-08T15:00"))
        val result = PayrollCalculator.breakdown(open, salary, until = at("2026-11-08T09:00"))
        assertThat(result.minutesOf(Concept.SUNDAY_DAY)).isEqualTo(60)
    }

    @Test
    fun `el corte va del 16 del mes anterior al 15 y paga a fin de mes`() {
        val period = PayPeriod(YearMonth.of(2026, 11))
        assertThat(period.noveltiesFrom).isEqualTo(LocalDate.of(2026, 10, 16))
        assertThat(period.noveltiesTo).isEqualTo(LocalDate.of(2026, 11, 15))
        assertThat(period.payday).isEqualTo(LocalDate.of(2026, 11, 30))
        assertThat(PayPeriod.containing(LocalDate.of(2026, 11, 15))).isEqualTo(period)
        assertThat(PayPeriod.containing(LocalDate.of(2026, 10, 16))).isEqualTo(period)
        assertThat(PayPeriod.containing(LocalDate.of(2026, 12, 20)).month).isEqualTo(YearMonth.of(2027, 1))
    }

    @Test
    fun `turno que cruza el corte reparte las horas entre liquidaciones`() {
        // Domingo 15 nov 22:00 a lunes festivo 16 nov 02:00.
        val cross = listOf(shift("2026-11-15T22:00", "2026-11-16T02:00"))
        val november = PayrollCalculator.liquidate(cross, settings, PayPeriod(YearMonth.of(2026, 11)))
        val december = PayrollCalculator.liquidate(cross, settings, PayPeriod(YearMonth.of(2026, 12)))
        assertThat(november.workedMinutes).isEqualTo(120)
        assertThat(december.workedMinutes).isEqualTo(120)
    }

    @Test
    fun `los turnos abiertos no entran a la liquidacion`() {
        val open = Shift("o", at("2026-11-08T08:00"), null, at("2026-11-08T15:00"))
        val result = PayrollCalculator.liquidate(listOf(open), settings, PayPeriod(YearMonth.of(2026, 11)))
        assertThat(result.workedMinutes).isEqualTo(0)
        assertThat(result.net).isEqualTo(2_800_000 + 249_095 - 224_000)
    }

    @Test
    fun `aplica las tarifas de la fecha trabajada`() {
        assertThat(LaborRules.at(LocalDate.of(2026, 6, 30)).sundayRate.toPlainString()).isEqualTo("0.80")
        assertThat(LaborRules.at(LocalDate.of(2026, 7, 1)).sundayRate.toPlainString()).isEqualTo("0.90")
        assertThat(LaborRules.at(LocalDate.of(2027, 7, 1)).sundayRate.toPlainString()).isEqualTo("1.00")
        assertThat(LaborRules.at(LocalDate.of(2026, 7, 14)).monthlyHours).isEqualTo(220)
        assertThat(LaborRules.at(LocalDate.of(2026, 7, 15)).monthlyHours).isEqualTo(210)
    }

    @Test
    fun `calcula los festivos de 2026`() {
        val expected = listOf(
            "2026-01-01", "2026-01-12", "2026-03-23", "2026-04-02", "2026-04-03", "2026-05-01",
            "2026-05-18", "2026-06-08", "2026-06-15", "2026-06-29", "2026-07-20", "2026-08-07",
            "2026-08-17", "2026-10-12", "2026-11-02", "2026-11-16", "2026-12-08", "2026-12-25",
        ).map(LocalDate::parse)
        assertThat(ColombiaHolidays.holidays(2026)).containsExactlyElementsIn(expected)
    }
}
