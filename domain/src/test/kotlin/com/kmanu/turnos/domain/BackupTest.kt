package com.kmanu.turnos.domain

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupTest {
    private fun at(text: String) = LocalDateTime.parse(text)

    private val data = BackupData(
        shifts = listOf(
            Shift(
                id = "1",
                start = at("2026-11-10T05:57"),
                end = at("2026-11-10T15:00"),
                scheduledEnd = at("2026-11-10T13:30"),
                breakMinutes = 30,
                compensatoryRest = false,
                scheduledStart = at("2026-11-10T06:00"),
                marginMinutes = 15,
                extrasAuthorized = true,
                authorizedBy = "Jefe \"Ana\" \\ turno",
                extraNote = "Inventario\nde fin de mes ñ",
            ),
            Shift("2", at("2026-11-11T08:00"), null, at("2026-11-11T16:00")),
        ),
        salary = SalaryHistory.fixed(1_750_905).withChange(LocalDate.of(2027, 1, 1), 1_900_000),
        settings = mapOf("cutoff_day" to "25", "worker_name" to "Persona de prueba"),
    )

    @Test
    fun `la copia se restaura igual`() {
        assertThat(Backup.decode(Backup.encode(data))).isEqualTo(data)
    }

    @Test
    fun `rechaza archivos que no son copias`() {
        val notJson = assertThrows(IllegalArgumentException::class.java) { Backup.decode("hola") }
        assertThat(notJson).hasMessageThat().contains("no es una copia")

        val other = assertThrows(IllegalArgumentException::class.java) { Backup.decode("""{"formato":"otro"}""") }
        assertThat(other).hasMessageThat().contains("no es una copia")
    }

    @Test
    fun `rechaza copias de una version mas nueva`() {
        val newer = Backup.encode(data).replace("\"version\":1", "\"version\":99")
        val error = assertThrows(IllegalArgumentException::class.java) { Backup.decode(newer) }
        assertThat(error).hasMessageThat().contains("más nueva")
    }

    @Test
    fun `avisa si la copia esta danada`() {
        val broken = Backup.encode(data).replace("\"salidaProgramada\"", "\"x\"")
        val error = assertThrows(IllegalArgumentException::class.java) { Backup.decode(broken) }
        assertThat(error).hasMessageThat().contains("dañada")
    }

    @Test
    fun `json lee escapes, numeros y anidados`() {
        val parsed = Json.parse("""{"a":[1,-2.5,true,null,"xñ\n"],"b":{}}""")
        assertThat(parsed).isEqualTo(mapOf("a" to listOf(1L, -2.5, true, null, "xñ\n"), "b" to emptyMap<String, Any?>()))
    }
}
