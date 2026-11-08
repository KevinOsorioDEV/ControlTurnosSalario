package com.kmanu.turnos.domain

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import org.junit.Assert.assertThrows
import org.junit.Test

class PayPeriodTest {
    @Test
    fun `por defecto el corte es el 15`() {
        val period = PayPeriod(YearMonth.of(2026, 11))
        assertThat(period.noveltiesFrom).isEqualTo(LocalDate.of(2026, 10, 16))
        assertThat(period.noveltiesTo).isEqualTo(LocalDate.of(2026, 11, 15))
    }

    @Test
    fun `respeta otro dia de corte y sigue pagando a fin de mes`() {
        val period = PayPeriod(YearMonth.of(2026, 11), cutoffDay = 20)
        assertThat(period.noveltiesFrom).isEqualTo(LocalDate.of(2026, 10, 21))
        assertThat(period.noveltiesTo).isEqualTo(LocalDate.of(2026, 11, 20))
        assertThat(period.payday).isEqualTo(LocalDate.of(2026, 11, 30))
    }

    @Test
    fun `el corte 31 toma el mes calendario`() {
        val february = PayPeriod(YearMonth.of(2026, 2), cutoffDay = 31)
        assertThat(february.noveltiesFrom).isEqualTo(LocalDate.of(2026, 2, 1))
        assertThat(february.noveltiesTo).isEqualTo(LocalDate.of(2026, 2, 28))
        val march = PayPeriod(YearMonth.of(2026, 3), cutoffDay = 31)
        assertThat(march.noveltiesFrom).isEqualTo(LocalDate.of(2026, 3, 1))
        assertThat(march.noveltiesTo).isEqualTo(LocalDate.of(2026, 3, 31))
    }

    @Test
    fun `el corte 30 no deja huecos alrededor de febrero`() {
        val march = PayPeriod(YearMonth.of(2026, 3), cutoffDay = 30)
        assertThat(march.noveltiesFrom).isEqualTo(LocalDate.of(2026, 3, 1))
        assertThat(march.noveltiesTo).isEqualTo(LocalDate.of(2026, 3, 30))
    }

    @Test
    fun `cada dia pertenece a exactamente un corte con cualquier dia de corte`() {
        for (cutoff in PayPeriod.MIN_CUTOFF_DAY..PayPeriod.MAX_CUTOFF_DAY) {
            var date = LocalDate.of(2027, 12, 1)
            val end = LocalDate.of(2028, 4, 1)
            while (date < end) {
                val period = PayPeriod.containing(date, cutoff)
                assertThat(date in period).isTrue()
                assertThat(date in period.next()).isFalse()
                assertThat(date in period.previous()).isFalse()
                date = date.plusDays(1)
            }
        }
    }

    @Test
    fun `la liquidacion usa el corte elegido`() {
        val shift = Shift(
            id = "domingo",
            start = LocalDateTime.parse("2026-11-22T08:00"),
            end = LocalDateTime.parse("2026-11-22T12:00"),
            scheduledEnd = LocalDateTime.parse("2026-11-22T12:00"),
        )
        val settings = PaySettings(2_800_000, 249_095)
        val with15 = PayrollCalculator.liquidate(listOf(shift), settings, PayPeriod(YearMonth.of(2026, 11)))
        val with25 = PayrollCalculator.liquidate(listOf(shift), settings, PayPeriod(YearMonth.of(2026, 11), cutoffDay = 25))
        assertThat(with15.noveltiesTotal).isEqualTo(0)
        assertThat(with25.noveltiesTotal).isEqualTo(48_000)
    }

    @Test
    fun `rechaza un dia de corte fuera de rango`() {
        assertThrows(IllegalArgumentException::class.java) { PayPeriod(YearMonth.of(2026, 11), cutoffDay = 0) }
        assertThrows(IllegalArgumentException::class.java) { PayPeriod(YearMonth.of(2026, 11), cutoffDay = 32) }
    }
}
