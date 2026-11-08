package com.kmanu.turnos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import com.kmanu.turnos.domain.Liquidation
import com.kmanu.turnos.ui.theme.Motion
import com.kmanu.turnos.ui.theme.Radius
import com.kmanu.turnos.ui.theme.Spacing
import com.kmanu.turnos.ui.theme.TurnosTheme
import com.kmanu.turnos.ui.theme.tabular
import java.time.LocalDate

@Composable
fun LiquidationScreen(state: UiState, viewModel: AppViewModel) {
    val pager = rememberPeriodPager(state.period, viewModel::selectPeriod)
    var detailOpen by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.md)) {
            Text("Liquidación", style = MaterialTheme.typography.headlineMedium)
            PeriodSelector(pager)
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            val result = remember(state, page) { state.copy(period = periodAt(page)).liquidation }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.ms),
            ) {
                NetCard(result)
                CalculationCard(result, open = detailOpen, onToggle = { detailOpen = !detailOpen })
            }
        }
    }
}

@Composable
private fun NetCard(result: Liquidation) {
    val style = TurnosTheme.colors.closed
    val today = LocalDate.now()
    val payday = result.period.payday.shortLabel()
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.hero),
        color = style.container,
        contentColor = style.content,
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Text(
                if (today <= result.period.payday) "Te van a pagar el $payday" else "Pago del $payday",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(money(result.net), style = MaterialTheme.typography.displaySmall.tabular())
            if (today <= result.period.noveltiesTo) {
                Text(
                    "Corte en curso: muestra lo acumulado hasta el último turno cerrado.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun CalculationCard(result: Liquidation, open: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (open) 180f else 0f, Motion.standard(), label = "flecha del detalle")
    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        color = TurnosTheme.colors.card,
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cómo se calculó", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.rotate(rotation))
            }
            AnimatedVisibility(
                visible = open,
                enter = expandVertically(Motion.standard()) + fadeIn(Motion.standard()),
                exit = shrinkVertically(Motion.standard()) + fadeOut(Motion.standard(Motion.SHORT)),
            ) {
                Column(Modifier.padding(top = Spacing.sm)) {
                    AmountRow("Horas trabajadas en el corte", hours(result.workedMinutes))
                    if (result.lines.isEmpty()) {
                        Text(
                            "Sin recargos ni extras en este corte.",
                            Modifier.padding(vertical = Spacing.xs),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    result.lines.forEach { line ->
                        AmountRow(line.concept.label, money(line.amount), hours(line.minutes))
                    }
                    HorizontalDivider(Modifier.padding(vertical = Spacing.sm))
                    AmountRow("Total recargos y extras", money(result.noveltiesTotal), emphasized = true)
                    Column(Modifier.padding(top = Spacing.md)) {
                        AmountRow("Salario del mes", money(result.salary))
                        AmountRow("Recargos y extras", money(result.noveltiesTotal))
                        AmountRow("Devengado", money(result.earned))
                        AmountRow("Auxilio de conectividad", money(result.allowance))
                        AmountRow("Salud y pensión (8%)", money(-result.healthAndPension))
                        HorizontalDivider(Modifier.padding(vertical = Spacing.sm))
                        AmountRow("Neto a recibir", money(result.net), emphasized = true)
                    }
                }
            }
        }
    }
}
