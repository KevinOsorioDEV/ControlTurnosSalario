package com.kmanu.turnos.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.kmanu.turnos.domain.PayPeriod
import com.kmanu.turnos.domain.PayrollCalculator
import com.kmanu.turnos.domain.SalaryHistory
import com.kmanu.turnos.domain.Shift
import com.kmanu.turnos.domain.ShiftBreakdown
import com.kmanu.turnos.domain.ShiftPlanner
import com.kmanu.turnos.ui.theme.Motion
import com.kmanu.turnos.ui.theme.Radius
import com.kmanu.turnos.ui.theme.Spacing
import com.kmanu.turnos.ui.theme.StatusStyle
import com.kmanu.turnos.ui.theme.TurnosTheme
import com.kmanu.turnos.ui.theme.tabular
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

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
fun ShiftScreen(state: UiState, viewModel: AppViewModel, onOpenLiquidation: () -> Unit) {
    val now = rememberNow()
    // El turno recién cerrado se muestra hasta que la persona toca "Listo".
    var closingId by rememberSaveable { mutableStateOf<String?>(null) }
    val open = state.openShift
    val closed = state.lastClosedShift?.takeIf { it.id == closingId }
    val hero = when {
        open != null -> Hero.Open(open, isExtra = Duration.between(open.scheduledEnd, now).toMinutes() > open.marginMinutes)
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
                        is Hero.Open -> OpenShiftContent(target, state.salary, state.knownAuthorizers, now) { exit, authorized, by, note ->
                            closingId = target.shift.id
                            viewModel.checkOut(exit, authorized, by, note)
                        }
                        is Hero.Closed -> ClosedShiftContent(target, state.salary) { closingId = null }
                    }
                }
            }
        }
        CutSummaryCard(state, now.toLocalDate()) { period ->
            viewModel.selectPeriod(period)
            onOpenLiquidation()
        }
        if (hero !is Hero.Closed) {
            state.lastClosedShift?.let { LastShiftCard(it, state.salary) }
        }
    }
}

private enum class PlanField { START, END }

private fun dayNote(date: LocalDate, today: LocalDate): String = when (date) {
    today.minusDays(1) -> "ayer"
    today -> "hoy"
    else -> "mañana"
}

@Composable
private fun CheckInContent(state: UiState, now: LocalDateTime, onCheckIn: (LocalDateTime, LocalDateTime, Int) -> Unit) {
    var chosen by remember { mutableStateOf(state.lastSchedule) }
    var breakOverride by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf<PlanField?>(null) }
    // El recordatorio de salida necesita permiso de notificaciones desde Android 13.
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val askNotifications = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val suggested = ShiftPlanner.roundedToHalfHour(now)
    val startTime = chosen?.first ?: suggested.toLocalTime()
    val endTime = chosen?.second ?: suggested.plusMinutes(state.settings.defaultShiftMinutes.toLong()).toLocalTime()
    val (scheduledStart, scheduledEnd) = ShiftPlanner.planFor(now, startTime, endTime)
    val policy = state.settings.breakPolicy
    val policyBreak = policy.minutesFor(Duration.between(scheduledStart, scheduledEnd).toMinutes())
    val breakText = breakOverride ?: policyBreak.toString()

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ms)) {
        Text("Sin turno activo", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            TimeTile("Entras", startTime, dayNote(scheduledStart.toLocalDate(), now.toLocalDate()), Modifier.weight(1f)) {
                picking = PlanField.START
            }
            TimeTile(
                "Sales",
                endTime,
                if (scheduledEnd.toLocalDate() != scheduledStart.toLocalDate()) "día siguiente" else null,
                Modifier.weight(1f),
            ) { picking = PlanField.END }
        }
        if (state.frequentShifts.isNotEmpty()) {
            ScheduleGrid(state.frequentShifts, selected = startTime to endTime) { start, end -> chosen = start to end }
        }
        if (policy.minutes > 0) {
            FilledField(
                value = breakText,
                // Cuatro cifras bastan; así un número enorme no se vuelve 0 sin avisar.
                onValueChange = { breakOverride = it.filter(Char::isDigit).take(4) },
                label = "Almuerzo no pagado (minutos)",
            )
        }
        PrimaryAction("Marcar entrada", onClick = {
            onCheckIn(scheduledStart, scheduledEnd, breakText.toIntOrNull() ?: 0)
            askNotifications()
        })
    }

    picking?.let { field ->
        TimeGridSheet(
            title = if (field == PlanField.START) "¿A qué hora entras?" else "¿A qué hora sales?",
            initial = if (field == PlanField.START) startTime else endTime,
            onDismiss = { picking = null },
            onConfirm = {
                chosen = if (field == PlanField.START) it to endTime else startTime to it
                picking = null
            },
        )
    }
}

@Composable
private fun OpenShiftContent(
    hero: Hero.Open,
    salary: SalaryHistory,
    knownAuthorizers: List<String>,
    now: LocalDateTime,
    onCheckOut: (exit: LocalDateTime?, authorized: Boolean?, authorizedBy: String?, note: String?) -> Unit,
) {
    val shift = hero.shift
    val breakdown = PayrollCalculator.breakdown(shift, salary, until = now)
    val extraMinutes = Duration.between(shift.scheduledEnd, now).toMinutes()
    val style = hero.style()
    var confirming by remember { mutableStateOf(false) }
    // Salida elegida a mano cuando se olvidó marcarla; `null` es ahora.
    var chosenExit by remember { mutableStateOf<LocalDateTime?>(null) }
    var forgottenAnswered by remember { mutableStateOf(false) }
    val cancel = {
        confirming = false
        chosenExit = null
        forgottenAnswered = false
    }
    if (confirming) {
        // Hora del momento de confirmar: la de la pantalla puede tener hasta 15 segundos de atraso.
        val confirmedAt = remember { LocalDateTime.now() }
        val exit = chosenExit ?: confirmedAt
        val finish = { authorized: Boolean?, by: String?, note: String? ->
            onCheckOut(chosenExit, authorized, by, note)
            cancel()
        }
        when {
            !forgottenAnswered && ShiftPlanner.looksForgotten(shift, confirmedAt) ->
                ForgottenExitSheet(shift, confirmedAt, onDismiss = cancel) { picked ->
                    chosenExit = picked
                    forgottenAnswered = true
                }
            ShiftPlanner.needsExtraConfirmation(shift, exit) ->
                ExtraAuthorizationSheet(shift, exit, knownAuthorizers, onDismiss = cancel) { authorized, by, note ->
                    finish(authorized, by, note)
                }
            else -> {
                val workedUntilExit = PayrollCalculator.breakdown(shift, salary, until = exit).workedMinutes
                AlertDialog(
                    onDismissRequest = cancel,
                    title = { Text("¿Marcar salida?") },
                    text = { Text("Cerrarás el turno a las ${exit.timeLabel()} con ${hours(workedUntilExit)} trabajadas.") },
                    confirmButton = { TextButton(onClick = { finish(null, null, null) }) { Text("Marcar salida") } },
                    dismissButton = { TextButton(onClick = cancel) { Text("Seguir en turno") } },
                )
            }
        }
    }
    val startsLater = shift.scheduledStart?.takeIf { it.isAfter(now) }
    Text(
        when {
            hero.isExtra -> "Estás en hora extra"
            startsLater != null -> "Tu turno empieza a las ${startsLater.timeLabel()}"
            else -> "En turno desde las ${shift.start.timeLabel()}"
        },
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
private fun ClosedShiftContent(hero: Hero.Closed, salary: SalaryHistory, onDone: () -> Unit) {
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
private fun LastShiftCard(shift: Shift, salary: SalaryHistory) {
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

/**
 * El turno sigue abierto mucho después de su salida: casi seguro se olvidó marcarla.
 * Se pregunta a qué hora salió en vez de cerrar con la hora actual.
 */
@Composable
private fun ForgottenExitSheet(shift: Shift, now: LocalDateTime, onDismiss: () -> Unit, onChoose: (LocalDateTime) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val end = shift.scheduledEnd
    val endDay = if (end.toLocalDate() == now.toLocalDate()) "" else " del ${end.toLocalDate().label()}"
    TurnosSheet(onDismiss) {
        Text("¿Olvidaste marcar la salida?", style = MaterialTheme.typography.titleLarge)
        Text(
            "Tu turno terminaba a las ${end.timeLabel()}$endDay y ya son las ${now.timeLabel()}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryAction("Salí a las ${end.timeLabel()}", onClick = { onChoose(end) })
        TextButton(onClick = { picking = true }, Modifier.fillMaxWidth()) { Text("Salí a otra hora") }
        // Un turno no puede durar 24 horas: después de eso, "ahora" no es una opción válida.
        if (Duration.between(shift.start, now).toHours() < 24) {
            TextButton(onClick = { onChoose(now) }, Modifier.fillMaxWidth()) { Text("Sigo aquí, salgo ahora") }
        }
    }
    if (picking) {
        TimeGridSheet(
            title = "¿A qué hora saliste?",
            initial = end.toLocalTime(),
            onDismiss = { picking = false },
            onConfirm = {
                picking = false
                onChoose(ShiftPlanner.endAfter(shift.start, it))
            },
        )
    }
}

@Composable
fun BreakdownLines(breakdown: ShiftBreakdown, detailColor: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    breakdown.lines.forEach { line ->
        AmountRow(line.concept.label, money(line.amount), hours(line.minutes), detailColor = detailColor)
    }
    // Con un solo concepto, el total repetiría la misma cifra.
    if (breakdown.lines.size > 1) {
        AmountRow("Recargos y extras", money(breakdown.total), emphasized = true)
    }
}

/** Lo acumulado en el corte en curso, a un toque de la liquidación completa. */
@Composable
private fun CutSummaryCard(state: UiState, today: LocalDate, onOpen: (PayPeriod) -> Unit) {
    val period = remember(today, state.settings.cutoffDay) { PayPeriod.containing(today, state.settings.cutoffDay) }
    val liquidation = remember(state.shifts, state.settings, period) { state.copy(period = period).liquidation }
    val variant = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = { onOpen(period) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        color = TurnosTheme.colors.card,
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Este corte", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("pago ${period.payday.shortLabel()}", style = MaterialTheme.typography.bodySmall, color = variant)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Ver liquidación", tint = variant)
            }
            AmountRow("Recargos y extras", money(liquidation.noveltiesTotal), "${hours(liquidation.workedMinutes)} en turnos cerrados")
            AmountRow("Neto estimado", money(liquidation.net), emphasized = true)
        }
    }
}
