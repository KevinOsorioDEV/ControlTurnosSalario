package com.kmanu.turnos.domain

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.util.zip.ZipInputStream
import org.junit.Test

class ScheduledShiftTest {
    private val salary = 2_800_000L

    private fun at(text: String) = LocalDateTime.parse(text)

    // Martes ordinario, turno de 6:00 a 13:30 con margen de 15 minutos.
    private fun tuesday(
        entry: String,
        exit: String?,
        authorized: Boolean = false,
        date: String = "2026-11-10",
    ) = Shift(
        id = "t",
        start = at("${date}T$entry"),
        end = exit?.let { at("${date}T$it") },
        scheduledEnd = at("${date}T13:30"),
        scheduledStart = at("${date}T06:00"),
        marginMinutes = 15,
        extrasAuthorized = authorized,
    )

    private fun worked(shift: Shift) = PayrollCalculator.breakdown(shift, salary).workedMinutes

    @Test
    fun `entrar tres minutos antes no genera recargo`() {
        val result = PayrollCalculator.breakdown(tuesday("05:57", "13:30"), salary)
        assertThat(result.lines).isEmpty()
        assertThat(result.workedMinutes).isEqualTo(450)
    }

    @Test
    fun `entrar veinte minutos antes tampoco cuenta`() {
        val result = PayrollCalculator.breakdown(tuesday("05:40", "13:30"), salary)
        assertThat(result.lines).isEmpty()
        assertThat(result.workedMinutes).isEqualTo(450)
    }

    @Test
    fun `llegar tarde dentro del margen se paga completo`() {
        assertThat(worked(tuesday("06:05", "13:30"))).isEqualTo(450)
    }

    @Test
    fun `llegar tarde fuera del margen se paga desde la entrada real`() {
        assertThat(worked(tuesday("06:40", "13:30"))).isEqualTo(410)
    }

    @Test
    fun `salir dentro del margen no genera extra`() {
        val result = PayrollCalculator.breakdown(tuesday("06:00", "13:35"), salary)
        assertThat(result.lines).isEmpty()
        assertThat(result.workedMinutes).isEqualTo(450)
    }

    @Test
    fun `salir antes del margen se paga hasta la salida real`() {
        assertThat(worked(tuesday("06:00", "12:00"))).isEqualTo(360)
    }

    @Test
    fun `extra no autorizada se paga hasta la salida programada`() {
        val result = PayrollCalculator.breakdown(tuesday("06:00", "15:00"), salary)
        assertThat(result.lines).isEmpty()
        assertThat(result.workedMinutes).isEqualTo(450)
    }

    @Test
    fun `extra autorizada se paga completa desde la salida programada`() {
        val result = PayrollCalculator.breakdown(tuesday("06:00", "15:00", authorized = true), salary)
        assertThat(result.minutesOf(Concept.EXTRA_DAY)).isEqualTo(90)
        assertThat(result.total).isEqualTo(25_000)
    }

    @Test
    fun `los turnos anteriores conservan su calculo`() {
        val legacy = Shift("l", at("2026-11-10T05:57"), at("2026-11-10T13:30"), at("2026-11-10T13:30"))
        val result = PayrollCalculator.breakdown(legacy, salary)
        assertThat(result.minutesOf(Concept.NIGHT)).isEqualTo(3)
        assertThat(result.workedMinutes).isEqualTo(453)
    }

    @Test
    fun `en curso muestra el tiempo hasta ahora incluida la extra`() {
        val open = tuesday("06:00", null)
        val result = PayrollCalculator.breakdown(open, salary, until = at("2026-11-10T14:30"))
        assertThat(result.minutesOf(Concept.EXTRA_DAY)).isEqualTo(60)
    }

    @Test
    fun `antes de la hora programada no acumula tiempo`() {
        val open = tuesday("05:57", null)
        assertThat(PayrollCalculator.breakdown(open, salary, until = at("2026-11-10T05:59")).workedMinutes).isEqualTo(0)
    }

    @Test
    fun `marcar antes de medianoche toma el turno que empieza a medianoche`() {
        val (start, end) = ShiftPlanner.planFor(at("2026-11-10T23:55"), LocalTime.MIDNIGHT, LocalTime.of(7, 0))
        assertThat(start).isEqualTo(at("2026-11-11T00:00"))
        assertThat(end).isEqualTo(at("2026-11-11T07:00"))
    }

    @Test
    fun `llegar en la madrugada toma el turno que empezo la noche anterior`() {
        val (start, end) = ShiftPlanner.planFor(at("2026-11-11T01:00"), LocalTime.of(22, 0), LocalTime.of(5, 0))
        assertThat(start).isEqualTo(at("2026-11-10T22:00"))
        assertThat(end).isEqualTo(at("2026-11-11T05:00"))
    }

    @Test
    fun `no toma un turno que ya termino`() {
        val (start, _) = ShiftPlanner.planFor(at("2026-11-10T14:00"), LocalTime.of(6, 0), LocalTime.of(13, 30))
        assertThat(start).isEqualTo(at("2026-11-11T06:00"))
    }

    @Test
    fun `turno que cruza la noche termina al dia siguiente`() {
        assertThat(ShiftPlanner.endAfter(at("2026-11-15T18:00"), LocalTime.of(1, 0))).isEqualTo(at("2026-11-16T01:00"))
    }

    @Test
    fun `solo pregunta por extras fuera del margen`() {
        val open = tuesday("06:00", null)
        assertThat(ShiftPlanner.needsExtraConfirmation(open, at("2026-11-10T13:45"))).isFalse()
        assertThat(ShiftPlanner.needsExtraConfirmation(open, at("2026-11-10T13:46"))).isTrue()
    }

    @Test
    fun `redondea la sugerencia a la media hora mas cercana`() {
        assertThat(ShiftPlanner.roundedToHalfHour(at("2026-11-10T05:57"))).isEqualTo(at("2026-11-10T06:00"))
        assertThat(ShiftPlanner.roundedToHalfHour(at("2026-11-10T23:50"))).isEqualTo(at("2026-11-11T00:00"))
    }

    @Test
    fun `genera el excel del corte con nombre y bitacora`() {
        val period = PayPeriod(YearMonth.of(2026, 11))
        val shifts = listOf(
            tuesday("05:57", "13:30"),
            tuesday("06:00", "15:00", authorized = true, date = "2026-11-11").copy(id = "b", authorizedBy = "Monitor <Ana> & Co", extraNote = "Cubrí incidente"),
            tuesday("06:00", "14:30", authorized = true, date = "2026-11-12").copy(id = "c"),
            Shift("d", at("2026-11-08T10:00"), at("2026-11-08T14:00"), at("2026-11-08T14:00"), compensatoryRest = false),
        )
        val bytes = ByteArrayOutputStream()
        ExcelReport.write(bytes, "Persona de prueba", shifts, PaySettings(salary, 249_095), period)

        File("build/reports").mkdirs()
        File("build/reports/turnos-ejemplo.xlsx").writeBytes(bytes.toByteArray())

        val entries = HashMap<String, String>()
        ZipInputStream(bytes.toByteArray().inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entries[it.name] = zip.readBytes().decodeToString() }
        }
        val turnos = entries.getValue("xl/worksheets/sheet1.xml")
        val extras = entries.getValue("xl/worksheets/sheet2.xml")
        assertThat(turnos).contains("Persona de prueba")
        assertThat(turnos).contains("Neto a recibir")
        assertThat(extras).contains("Monitor &lt;Ana&gt; &amp; Co")
        assertThat(extras).contains(ExcelReport.MISSING_AUTHORIZER)
        assertThat(turnos).contains("Sin descanso compensatorio")
    }
}
