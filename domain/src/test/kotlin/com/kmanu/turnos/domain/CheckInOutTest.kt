package com.kmanu.turnos.domain

import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import org.junit.Test

class CheckInOutTest {
    private fun at(text: String) = LocalDateTime.parse(text)

    private val now = at("2026-11-10T08:00")
    private val open = Shift(
        id = "abierto",
        start = at("2026-11-10T06:00"),
        end = null,
        scheduledEnd = at("2026-11-10T13:30"),
        scheduledStart = at("2026-11-10T06:00"),
        marginMinutes = 15,
    )

    @Test
    fun `no deja marcar entrada con un turno abierto`() {
        assertThat(ShiftValidation.validateCheckIn(at("2026-11-10T16:00"), listOf(open), now)).contains("turno abierto")
    }

    @Test
    fun `no deja marcar entrada que se cruce con un turno futuro`() {
        val later = Shift("luego", at("2026-11-10T12:00"), at("2026-11-10T18:00"), at("2026-11-10T18:00"))
        assertThat(ShiftValidation.validateCheckIn(at("2026-11-10T16:00"), listOf(later), now)).contains("Se cruza")
        assertThat(ShiftValidation.validateCheckIn(at("2026-11-10T11:00"), listOf(later), now)).isNull()
    }

    @Test
    fun `la salida no puede ser futura ni de 24 horas o mas`() {
        assertThat(ShiftValidation.validateCheckOut(open, at("2026-11-10T09:00"), emptyList(), now)).contains("aún no llega")
        val late = at("2026-11-11T07:00")
        assertThat(ShiftValidation.validateCheckOut(open, late, emptyList(), late)).contains("24 horas")
        assertThat(ShiftValidation.validateCheckOut(open, at("2026-11-10T13:30"), emptyList(), late)).isNull()
    }

    @Test
    fun `detecta la salida olvidada despues de tres horas`() {
        // Salida 1:30 p. m. + 15 min de margen + 3 h = 4:45 p. m.
        assertThat(ShiftPlanner.looksForgotten(open, at("2026-11-10T16:45"))).isFalse()
        assertThat(ShiftPlanner.looksForgotten(open, at("2026-11-10T16:46"))).isTrue()
        assertThat(ShiftPlanner.looksForgotten(open.copy(end = at("2026-11-10T13:30")), at("2026-11-11T08:00"))).isFalse()
    }

    @Test
    fun `recuerda la salida media hora despues del margen`() {
        assertThat(ShiftPlanner.reminderAt(open)).isEqualTo(at("2026-11-10T14:15"))
    }
}
