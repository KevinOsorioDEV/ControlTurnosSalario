package com.kmanu.turnos.domain

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class LegalPayTest {
    private val today = LocalDate.of(2026, 11, 8)

    @Test
    fun `da el auxilio de 2026 a quien gana hasta dos minimos`() {
        assertThat(LegalPay.allowanceFor(2_800_000, today)).isEqualTo(249_095)
        assertThat(LegalPay.allowanceFor(3_501_810, today)).isEqualTo(249_095)
    }

    @Test
    fun `no da auxilio a quien supera dos minimos`() {
        assertThat(LegalPay.allowanceFor(3_501_811, today)).isEqualTo(0)
    }

    @Test
    fun `usa los valores del anio indicado`() {
        assertThat(LegalPay.allowanceFor(2_000_000, LocalDate.of(2025, 6, 1))).isEqualTo(200_000)
    }

    @Test
    fun `un anio sin decreto usa el ultimo conocido`() {
        assertThat(LegalPay.at(LocalDate.of(2030, 1, 1))).isEqualTo(LegalPay.at(today))
    }
}
