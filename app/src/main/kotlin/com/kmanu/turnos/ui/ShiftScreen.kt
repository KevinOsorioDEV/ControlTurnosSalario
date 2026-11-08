package com.kmanu.turnos.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kmanu.turnos.domain.PayrollCalculator
import com.kmanu.turnos.domain.Shift
import com.kmanu.turnos.domain.ShiftBreakdown
import com.kmanu.turnos.ui.theme.Motion
import com.kmanu.turnos.ui.theme.Radius
import com.kmanu.turnos.ui.theme.Spacing
import com.kmanu.turnos.ui.theme.StatusStyle
import com.kmanu.turnos.ui.theme.TurnosTheme
import com.kmanu.turnos.ui.theme.tabular
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Lo que muestra la tarjeta principal. Conserva el turno para poder animar su salida. */
private sealed interface Hero {
    val key: String

    data object Idle : Hero {
        override val key = "sin turno"
    }

    data class Open(val shift: Shift, val isExtra: Boolean) : Hero {
        override val key = if (isExtra) "extra" else "en turno"
    }

    data class Closed(val shift: Shift) : Hero {
        override val key = "cerrado"
    }
}

@Composable
private fun Hero.style(): StatusStyle {
    val colors = TurnosTheme.colors
    return when (this) {
        Hero.Idle -> StatusStyle(
            container = colors.card,
            content = MaterialTheme.colorScheme.onSurface,
            actionContainer = MaterialTheme.colorScheme.primary,
            actionContent = MaterialTheme.colorScheme.onPrimary,
        )
        is Hero.Open -> if (isExtra) colors.extra else colors.active
        is Hero.Closed -> colors.closed
    }
}

@Composable
fun ShiftScreen(state: UiState, viewModel: AppViewModel) {
    val now = rememberNow()
    // El turno recién cerrado se muestra hasta que la persona toca "Listo".
    var closingId by rememberSaveable { mutableStateOf<String?>(null) }
    val open = state.openShift
    val closed = state.lastClosedShift?.takeIf { it.id == closingId }
    val hero = when {
        open != null -> Hero.Open(open, isExtra = Duration.between(open.scheduledEnd, now).toMinutes() > 0)
        closed != null -> Hero.Closed(closed)
        else -> Hero.Idle
    }
    val style = hero.style()
    val container by animateColorAsState(style.container, Motion.standard(), label = "fondo del turno")
    val content by animateColorAsState(style.content, Motion.standard(), label = "texto del turno")

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.ms),
    ) {
        Text("Turno", style = MaterialTheme.typography.headlineMedium)
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.hero),
            color = container,
            contentColor = content,
        ) {
            AnimatedContent(
                targetState = hero,
                contentKey = { it.key },
                transitionSpec = {
                    (fadeIn(Motion.standard()) + slideInVertically(Motion.standard()) { it / 8 })
                        .togetherWith(fadeOut(Motion.standard(Motion.SHORT)))
                        .using(SizeTransform(clip = false))
                },
                label = "tarjeta del turno",
            ) { target ->
                Column(Modifier.padding(Spacing.md)) {
                    when (target) {
                        Hero.Idle -> CheckInContent(state, now, viewModel::checkIn)
                        is Hero.Open -> OpenShiftContent(target, state.settings.monthlySalary, now) {
                            closingId = target.shift.id
                            viewModel.checkOut()
                        }
                        is Hero.Closed -> ClosedShiftContent(target, state.settings.monthlySalary) { closingId = null }
                    }
                }
            }
        }
        if (hero !is Hero.Closed) {
            state.lastClosedShift?.let { LastShiftCard(it, state.settings.monthlySalary) }
        }
    }
}

private fun nextOccurrence(time: LocalTime, now: LocalDateTime): LocalDateTime {
    val today = now.toLocalDate().atTime(time)
    return if (today.isAfter(now)) today else today.plusDays(1)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckInContent(state: UiState, now: LocalDateTime, onCheckIn: (LocalDateTime, Int) -> Unit) {
    var chosenEnd by remember { mutableStateOf<LocalTime?>(null) }
    var breakOverride by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }

    val defaultEnd = now.plusMinutes(state.settings.defaultShiftMinutes.toLong()).toLocalTime().truncatedTo(ChronoUnit.MINUTES)
    val endTime = chosenEnd ?: defaultEnd
    val scheduledEnd = nextOccurrence(endTime, now)
    val policy = state.settings.breakPolicy
    val policyBreak = policy.minutesFor(Duration.between(now, scheduledEnd).toMinutes())
    val breakText = breakOverride ?: policyBreak.toString()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ms)) {
        Text("Sin turno activo", style = MaterialTheme.typography.titleMedium)
        Surface(
            onClick = { picking = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.control),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val day = if (scheduledEnd.toLocalDate() == now.toLocalDate()) "hoy" else "mañana"
                    Text("Sales a las", style = MaterialTheme.typography.labelMedium, color = muted)
                    Text(endTime.label(), style = MaterialTheme.typography.headlineLarge.tabular())
                    Text(day, style = MaterialTheme.typography.bodyMedium, color = muted)
                }
                Icon(Icons.Rounded.Edit, contentDescription = "Cambiar hora de salida", tint = muted)
            }
        }
        if (state.frequentEndTimes.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                state.frequentEndTimes.forEach { time ->
                    PillChip(time.label(), selected = time == endTime, onClick = { chosenEnd = time })
                }
            }
        }
        if (policy.minutes > 0) {
            FilledField(
                value = breakText,
                onValueChange = { breakOverride = it.filter(Char::isDigit) },
                label = "Almuerzo no pagado (minutos)",
            )
        }
        PrimaryAction("Marcar entrada", onClick = { onCheckIn(scheduledEnd, breakText.toIntOrNull() ?: 0) })
    }

    if (picking) {
        TimeGridSheet("¿A qué hora sales?", endTime, { picking = false }) {
            chosenEnd = it
            picking = false
        }
    }
}

@Composable
private fun OpenShiftContent(hero: Hero.Open, salary: Long, now: LocalDateTime, onCheckOut: () -> Unit) {
    val shift = hero.shift
    val breakdown = PayrollCalculator.breakdown(shift, salary, until = now)
    val extraMinutes = Duration.between(shift.scheduledEnd, now).toMinutes()
    val style = hero.style()
    var confirming by remember { mutableStateOf(false) }
    if (confirming) {
        // Hora del momento de confirmar: la de la pantalla puede tener hasta 15 segundos de atraso.
        val exit = remember { LocalDateTime.now() }
        val workedUntilExit = PayrollCalculator.breakdown(shift, salary, until = exit).workedMinutes
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("¿Marcar salida?") },
            text = { Text("Cerrarás el turno a las ${exit.timeLabel()} con ${hours(workedUntilExit)} trabajadas.") },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onCheckOut()
                }) { Text("Marcar salida") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Seguir en turno") } },
        )
    }
    Text(
        if (hero.isExtra) "Estás en hora extra" else "En turno desde las ${shift.start.timeLabel()}",
        style = MaterialTheme.typography.titleSmall,
    )
    Text(hours(breakdown.workedMinutes), style = MaterialTheme.typography.displaySmall.tabular())
    Text(
        if (hero.isExtra) "${hours(extraMinutes)} después de tu salida" else "Sales a las ${shift.scheduledEnd.timeLabel()}",
        style = MaterialTheme.typography.bodyMedium,
    )
    if (breakdown.lines.isEmpty()) {
        Text(
            "Sin recargos por ahora",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Spacing.ms),
        )
    } else {
        Column(Modifier.padding(top = Spacing.ms)) { BreakdownLines(breakdown, detailColor = LocalContentColor.current) }
    }
    PrimaryAction(
        "Marcar salida",
        onClick = { confirming = true },
        modifier = Modifier.padding(top = Spacing.md),
        container = style.actionContainer,
        content = style.actionContent,
    )
}

@Composable
private fun ClosedShiftContent(hero: Hero.Closed, salary: Long, onDone: () -> Unit) {
    val shift = hero.shift
    val breakdown = PayrollCalculator.breakdown(shift, salary)
    val style = hero.style()
    Text("Turno cerrado", style = MaterialTheme.typography.titleSmall)
    Text(hours(breakdown.workedMinutes), style = MaterialTheme.typography.displaySmall.tabular())
    Text(
        "${shift.start.timeLabel()} – ${shift.end?.timeLabel().orEmpty()}",
        style = MaterialTheme.typography.bodyMedium,
    )
    if (breakdown.lines.isNotEmpty()) {
        Column(Modifier.padding(top = Spacing.ms)) { BreakdownLines(breakdown, detailColor = LocalContentColor.current) }
    }
    PrimaryAction(
        "Listo",
        onClick = onDone,
        modifier = Modifier.padding(top = Spacing.md),
        container = style.actionContainer,
        content = style.actionContent,
    )
}

@Composable
private fun LastShiftCard(shift: Shift, salary: Long) {
    val breakdown = PayrollCalculator.breakdown(shift, salary)
    TurnosCard {
        Text("Último turno", style = MaterialTheme.typography.titleMedium)
        Text(
            "${shift.start.toLocalDate().label()} · ${shift.start.timeLabel()} – ${shift.end?.timeLabel().orEmpty()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AmountRow("Horas trabajadas", hours(breakdown.workedMinutes))
        BreakdownLines(breakdown)
    }
}

@Composable
fun BreakdownLines(breakdown: ShiftBreakdown, detailColor: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    breakdown.lines.forEach { line ->
        AmountRow(line.concept.label, money(line.amount), hours(line.minutes), detailColor = detailColor)
    }
    if (breakdown.lines.isNotEmpty()) {
        AmountRow("Recargos y extras", money(breakdown.total), emphasized = true)
    }
}
