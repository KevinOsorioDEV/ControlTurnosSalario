package com.kmanu.turnos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.ExpandMore
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
import com.kmanu.turnos.domain.LegalPay
import com.kmanu.turnos.domain.Liquidation
import com.kmanu.turnos.domain.PayPeriod
import com.kmanu.turnos.domain.PayrollCalculator
import com.kmanu.turnos.domain.Shift
import com.kmanu.turnos.ui.theme.Motion
import com.kmanu.turnos.ui.theme.Radius
import com.kmanu.turnos.ui.theme.Sizes
import com.kmanu.turnos.ui.theme.Spacing
import com.kmanu.turnos.ui.theme.StatusStyle
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
            LiquidationModeSelector(viewModel.manualMode, onChange = { viewModel.manualMode = it })
            if (!viewModel.manualMode) PeriodSelector(pager, state.settings.cutoffDay)
        }
        if (viewModel.manualMode) {
            // La simulación es una calculadora: sin fechas que recorrer. Sugiere los valores del
            // corte de hoy y la persona los ajusta en "Datos para el cálculo" si necesita otros.
            val today = LocalDate.now()
            val period = remember(today, state.settings.cutoffDay) { PayPeriod.containing(today, state.settings.cutoffDay) }
            ManualLiquidation(
                settings = state.settings,
                period = period,
                draft = viewModel.manualDrafts[period.month] ?: ManualDraft(),
                onDraftChange = { viewModel.updateDraft(period.month, it) },
                detailOpen = detailOpen,
                onToggleDetail = { detailOpen = !detailOpen },
                modifier = Modifier.weight(1f),
            )
        } else {
            HorizontalPager(pager, Modifier.weight(1f)) { page ->
                val pagePeriod = periodAt(page, state.settings.cutoffDay)
                val result = remember(state, page) { state.copy(period = pagePeriod).liquidation }
                Column(
                    Modifier.fillMaxSize().topFade().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.ms),
                ) {
                    NetCard(result)
                    state.openShift?.let { OpenShiftNotice(it, state, pagePeriod) }
                    LegalValuesNotice(state, pagePeriod)
                    CalculationCard(result, open = detailOpen, onToggle = { detailOpen = !detailOpen })
                }
            }
        }
    }
}

/** Si el año del pago aún no tiene decreto en la app, el auxilio puede estar desactualizado. */
@Composable
private fun LegalValuesNotice(state: UiState, period: PayPeriod) {
    val year = period.payday.year
    if (LegalPay.isKnown(year) || year < LegalPay.lastKnownYear) return
    val usesMinimum = state.settings.salaryHistory.changeAt(period.payday).minimumWage
    val legalAllowance = state.settings.allowanceHistory.changeAt(period.payday).isLegal
    val affected = listOfNotNull("el salario mínimo".takeIf { usesMinimum }, "el auxilio".takeIf { legalAllowance })
    if (affected.isEmpty()) return
    TurnosCard {
        Text("Valores de ley de $year pendientes", style = MaterialTheme.typography.titleSmall)
        Text(
            "${affected.joinToString(" y ").replaceFirstChar { it.uppercase() }} usa${if (affected.size > 1) "n" else ""} " +
                "los valores de ${LegalPay.lastKnownYear}. Cuando salga el decreto de $year, actualiza la app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Lo que va del turno abierto todavía no está en el neto: se muestra aparte para que no parezca perdido. */
@Composable
private fun OpenShiftNotice(open: Shift, state: UiState, period: PayPeriod) {
    val now = rememberNow()
    val breakdown = PayrollCalculator.breakdown(open, state.salary, until = now, includeDate = period::contains)
    if (breakdown.workedMinutes == 0L) return
    TurnosCard {
        AmountRow(
            "Turno en curso",
            "+${money(breakdown.total)}",
            "${hours(breakdown.workedMinutes)} · se suma al marcar la salida",
        )
    }
}

/**
 * Neto del corte. El verde sólido se reserva para la cifra real, la que sale de tus turnos.
 * Con [simulated] la tarjeta es neutra y con borde: es un cálculo de prueba, no lo que te van a pagar.
 */
@Composable
fun NetCard(result: Liquidation, simulated: Boolean = false) {
    val today = LocalDate.now()
    val payday = result.period.payday.shortLabel()
    val style = if (simulated) {
        StatusStyle(TurnosTheme.colors.card, MaterialTheme.colorScheme.onSurface)
    } else {
        TurnosTheme.colors.closed
    }
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.hero),
        color = style.container,
        contentColor = style.content,
        border = if (simulated) BorderStroke(Sizes.focusBorder, MaterialTheme.colorScheme.outlineVariant) else null,
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Text(
                when {
                    simulated -> "Con las horas que ingresaste"
                    today <= result.period.payday -> "Te van a pagar el $payday"
                    else -> "Pago del $payday"
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(money(result.net), style = MaterialTheme.typography.displaySmall.tabular())
            when {
                simulated -> Text(
                    "Es una simulación: no cambia tus turnos ni tu liquidación.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                today <= result.period.noveltiesTo -> Text(
                    "Corte en curso: suma hasta el último turno cerrado.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

@Composable
fun CalculationCard(
    result: Liquidation,
    open: Boolean,
    onToggle: () -> Unit,
    workedLabel: String = "Horas trabajadas en el corte",
    netLabel: String = "Neto a recibir",
) {
    val rotation by animateFloatAsState(if (open) 180f else 0f, Motion.standard(), label = "flecha del detalle")
    // El resumen siempre está a la vista; solo se pliega el detalle por concepto.
    TurnosCard {
        Text("Cómo se calculó", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = Spacing.xs))
        AmountRow("Salario del mes", money(result.salary))
        AmountRow("Recargos y extras", money(result.noveltiesTotal))
        AmountRow("Devengado", money(result.earned))
        AmountRow("Auxilio de transporte o conectividad", money(result.allowance))
        AmountRow("Salud y pensión (8%)", money(-result.healthAndPension))
        HorizontalDivider(Modifier.padding(vertical = Spacing.sm))
        AmountRow(netLabel, money(result.net), emphasized = true)
    }
    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        color = TurnosTheme.colors.card,
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Detalle de recargos y extras", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.rotate(rotation))
            }
            AnimatedVisibility(
                visible = open,
                enter = expandVertically(Motion.standard()) + fadeIn(Motion.standard()),
                exit = shrinkVertically(Motion.standard()) + fadeOut(Motion.standard(Motion.SHORT)),
            ) {
                Column(Modifier.padding(top = Spacing.sm)) {
                    AmountRow(workedLabel, hours(result.workedMinutes))
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
                }
            }
        }
    }
}
