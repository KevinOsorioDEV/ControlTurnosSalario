package com.kmanu.turnos.domain

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.zip.ZipInputStream
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Fallos aceptados en la revisión cruzada. Cada test falla con el código actual y debe pasar
 * cuando se corrija el fallo. Los IDs (F1, F2…) corresponden al informe del juez.
 */
class FallosRevisionTest {
    private fun at(text: String) = LocalDateTime.parse(text)
    private val noviembre = PayPeriod(YearMonth.of(2026, 11))

    private fun hojas(shifts: List<Shift>): Map<String, String> {
        val bytes = ByteArrayOutputStream()
        ExcelReport.write(bytes, "Prueba", shifts, PaySettings(2_800_000, 249_095), noviembre)
        val entries = HashMap<String, String>()
        ZipInputStream(bytes.toByteArray().inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entries[it.name] = zip.readBytes().decodeToString() }
        }
        return entries
    }

    // F1. La regla del almuerzo compara contra el tiempo real y bloquea la salida.
    @Test
    fun `F1 salir antes de cumplir el almuerzo no bloquea el cierre del turno`() {
        val open = Shift(
            "a", at("2026-11-10T06:00"), null, at("2026-11-10T13:30"),
            breakMinutes = 60, scheduledStart = at("2026-11-10T06:00"), marginMinutes = 15,
        )
        assertThat(ShiftValidation.validateCheckOut(open, at("2026-11-10T06:50"), listOf(open), at("2026-11-10T06:50")))
            .isNull()
    }

    @Test
    fun `F1 entrada tarde con almuerzo se puede cerrar en la salida programada`() {
        val open = Shift(
            "a", at("2026-11-10T16:40"), null, at("2026-11-10T17:00"),
            breakMinutes = 60, scheduledStart = at("2026-11-10T08:00"),
        )
        assertThat(ShiftValidation.validateCheckOut(open, at("2026-11-10T17:00"), listOf(open), at("2026-11-10T17:00")))
            .isNull()
    }

    // F2. La entrada no valida el almuerzo, pero la salida sí: el turno queda abierto sin salida.
    @Test
    fun `F2 si se permite marcar la entrada, se puede cerrar en la salida programada`() {
        val entrada = ShiftValidation.validateCheckIn(
            at("2026-11-10T13:30"), emptyList(), at("2026-11-10T06:00"),
            scheduledStart = at("2026-11-10T06:00"), breakMinutes = 600,
        )
        val open = Shift(
            "a", at("2026-11-10T06:00"), null, at("2026-11-10T13:30"),
            breakMinutes = 600, scheduledStart = at("2026-11-10T06:00"),
        )
        val salida = ShiftValidation.validateCheckOut(open, at("2026-11-10T13:30"), listOf(open), at("2026-11-10T13:30"))
        assertWithMessage("entrada=%s, salida=%s", entrada, salida).that(entrada != null || salida == null).isTrue()
    }

    // F3. validate acepta un turno cerrado cuya salida aún no llega.
    @Test
    fun `F3 no se guarda un turno cerrado con salida en el futuro`() {
        val futuro = Shift("x", at("2026-11-10T22:00"), at("2026-11-11T06:00"), at("2026-11-11T06:00"))
        assertThat(ShiftValidation.validate(futuro, emptyList(), at("2026-11-10T09:00"))).isNotNull()
    }

    // F4. Divisor de horas de la Ley 2101 antes del 15-07-2024.
    @Test
    fun `F4 divisor de 235 y 240 horas antes de julio de 2024 y de 2023`() {
        assertThat(LaborRules.at(LocalDate.of(2024, 3, 1)).monthlyHours).isEqualTo(235)
        assertThat(LaborRules.at(LocalDate.of(2023, 3, 1)).monthlyHours).isEqualTo(240)
    }

    // F5. Los años anteriores al primer decreto registrado usan los valores de 2025.
    @Test
    fun `F5 valores legales de 2024 no son los de 2025`() {
        val fecha = LocalDate.of(2024, 6, 1)
        assertThat(LegalPay.at(fecha).minimumWage).isEqualTo(1_300_000)
        assertThat(LegalPay.at(fecha).allowance).isEqualTo(162_000)
        assertThat(LegalPay.allowanceFor(2_700_000, fecha)).isEqualTo(0)
    }

    // F6. Se pide autorizar extras por unos segundos que después no se pagan.
    @Test
    fun `F6 preguntar por extras coincide con pagarlas`() {
        val shift = Shift(
            "t", at("2026-11-10T06:00"), null, at("2026-11-10T13:30"),
            scheduledStart = at("2026-11-10T06:00"), marginMinutes = 15, extrasAuthorized = false,
        )
        val exit = at("2026-11-10T13:45:30")
        val pregunta = ShiftPlanner.needsExtraConfirmation(shift, exit)
        val pagado = PayrollCalculator.paidInterval(shift.copy(end = exit, extrasAuthorized = true))!!
        assertWithMessage("pregunta=%s, salida pagada=%s", pregunta, pagado.end)
            .that(pregunta).isEqualTo(pagado.end > shift.scheduledEnd)
    }

    // F7. El Excel omite un turno cuyos minutos sí entran en la liquidación del corte.
    @Test
    fun `F7 el excel muestra los turnos que suman en la liquidacion`() {
        val shift = Shift(
            "n", at("2026-11-16T00:05"), at("2026-11-16T08:00"), at("2026-11-16T07:55"),
            scheduledStart = at("2026-11-15T23:55"), marginMinutes = 15,
        )
        val liquidacion = PayrollCalculator.liquidate(listOf(shift), PaySettings(2_800_000, 249_095), noviembre)
        assertThat(liquidacion.workedMinutes).isEqualTo(5)
        assertThat(hojas(listOf(shift)).getValue("xl/worksheets/sheet1.xml")).contains("16/11/2026")
    }

    // F8. Con la columna "sin compensatorio" hay 17 columnas, pero solo 16 anchos.
    @Test
    fun `F8 observaciones conserva su ancho cuando aparece la columna sin compensatorio`() {
        val domingo = Shift("d", at("2026-11-08T10:00"), at("2026-11-08T14:00"), at("2026-11-08T14:00"), compensatoryRest = false)
        assertThat(hojas(listOf(domingo)).getValue("xl/worksheets/sheet1.xml"))
            .contains("""<col min="17" max="17" width="40" customWidth="1"/>""")
    }

    // F9. Una copia sin versión o con versión 1.0 se reporta como "de una versión más nueva".
    @Test
    fun `F9 una copia sin version o con version decimal no se reporta como mas nueva`() {
        val sinVersion = assertThrows(IllegalArgumentException::class.java) {
            Backup.decode("""{"formato":"turnos-respaldo"}""")
        }
        assertThat(sinVersion).hasMessageThat().doesNotContain("más nueva")

        val decimal = runCatching { Backup.decode(copia(version = "1.0")) }.exceptionOrNull()
        assertThat(decimal?.message.orEmpty()).doesNotContain("más nueva")
    }

    // F10. Un ajuste nulo en la copia se restaura como el texto "null".
    @Test
    fun `F10 un ajuste nulo no se restaura como el texto null`() {
        val data = Backup.decode(copia(ajustes = """{"worker_name":null}"""))
        assertThat(data.settings["worker_name"]).isNotEqualTo("null")
    }

    // F11. Restaurar no valida los turnos de la copia.
    @Test
    fun `F11 restaurar rechaza turnos con salida anterior a la entrada o ids repetidos`() {
        val alReves = turno("a", entrada = "2026-11-10T14:00", salida = "2026-11-10T06:00")
        assertThrows(IllegalArgumentException::class.java) { Backup.decode(copia(turnos = "[$alReves]")) }

        val uno = turno("a", "2026-11-10T06:00", "2026-11-10T14:00")
        val otro = turno("a", "2026-11-11T06:00", "2026-11-11T14:00")
        assertThrows(IllegalArgumentException::class.java) { Backup.decode(copia(turnos = "[$uno,$otro]")) }
    }

    // F12. El historial guardado como texto acepta valores dañados.
    @Test
    fun `F12 el historial guardado rechaza montos negativos, campos de mas y fechas repetidas`() {
        assertThat(SalaryHistory.decode("2026-01-01=-5")).isNull()
        assertThat(SalaryHistory.decode("2026-01-01=100=200")).isNull()
        assertThat(SalaryHistory.decode("2026-01-01=1;2026-01-01=2")).isNull()
        assertThat(AllowanceHistory.decode("2026-01-01=-5")).isNull()
    }

    private fun turno(id: String, entrada: String, salida: String) =
        """{"id":"$id","entrada":"$entrada","salida":"$salida","entradaProgramada":null,""" +
            """"salidaProgramada":"$salida","almuerzo":0,"compensatorio":true,"margen":0,""" +
            """"extrasAutorizadas":true,"autorizo":null,"nota":null}"""

    private fun copia(version: String = "1", ajustes: String = "{}", turnos: String = "[]") =
        """{"formato":"turnos-respaldo","version":$version,""" +
            """"salarios":[{"desde":"2000-01-01","salario":2800000,"minimo":false}],""" +
            """"ajustes":$ajustes,"turnos":$turnos}"""
}
