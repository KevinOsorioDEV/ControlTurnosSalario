package com.kmanu.turnos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.kmanu.turnos.domain.BreakPolicy
import com.kmanu.turnos.domain.ColombiaHolidays
import com.kmanu.turnos.domain.PayrollCalculator
import com.kmanu.turnos.domain.Shift
import com.kmanu.turnos.ui.theme.Radius
import com.kmanu.turnos.ui.theme.Sizes
import com.kmanu.turnos.ui.theme.Spacing
import com.kmanu.turnos.ui.theme.TurnosTheme
import com.kmanu.turnos.ui.theme.tabular
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

private const val MINUTES_STEP = 15

private enum class TimeTarget { START, END }

/**
 * Registrar o corregir un turno: un día y dos horas. La fecha de salida no se pide: si la hora
 * de salida no es posterior a la de entrada, el turno terminó al día siguiente.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShiftEditor(
    original: Shift,
    isNew: Boolean,
    breakPolicy: BreakPolicy,
    salary: Long,
    frequentShifts: List<Pair<LocalTime, LocalTime>>,
    onDismiss: () -> Unit,
    onSave: (Shift) -> String?,
    onDelete: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOpenShift = original.end == null
    val initialExtra = original.end?.let { Duration.between(original.scheduledEnd, it).toMinutes().toInt().coerceAtLeast(0) } ?: 0

    var day by remember { mutableStateOf(original.start.toLocalDate()) }
    var startTime by remember { mutableStateOf(original.start.toLocalTime()) }
    var endTime by remember { mutableStateOf((original.end ?: original.scheduledEnd).toLocalTime()) }
    var extraMinutes by remember { mutableIntStateOf(initialExtra) }
    var breakOverride by remember { mutableStateOf(if (isNew) null else original.breakMinutes) }
    var compensatoryRest by remember { mutableStateOf(original.compensatoryRest) }
    var optionsOpen by remember { mutableStateOf(!isNew && (original.breakMinutes > 0 || initialExtra > 0 || !original.compensatoryRest)) }
    var picking by remember { mutableStateOf<TimeTarget?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmingDelete by remember { mutableStateOf(false) }

    val startAt = day.atTime(startTime)
    val endsNextDay = endTime <= startTime
    val endAt = (if (endsNextDay) day.plusDays(1) else day).atTime(endTime)
    val duration = Duration.between(startAt, endAt).toMinutes().toInt()
    val extra = extraMinutes.coerceIn(0, duration - 1)
    val breakMinutes = breakOverride ?: breakPolicy.minutesFor((duration - extra).toLong())
    val draft = if (isOpenShift) {
        original.copy(start = startAt, scheduledEnd = endAt, breakMinutes = breakMinutes, compensatoryRest = compensatoryRest)
    } else {
        original.copy(
            start = startAt,
            end = endAt,
            scheduledEnd = endAt.minusMinutes(extra.toLong()),
            breakMinutes = breakMinutes,
            compensatoryRest = compensatoryRest,
        )
    }
    val touchesSundayOrHoliday = generateSequence(day) { it.plusDays(1) }
        .takeWhile { it <= endAt.toLocalDate() }
        .any(ColombiaHolidays::isSundayOrHoliday)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    BackHandler(onBack = onDismiss)

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(Modifier.padding(start = Spacing.xs, top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver sin guardar")
                }
                Text(if (isNew) "Registrar turno" else "Editar turno", style = MaterialTheme.typography.titleLarge)
            }
            Column(
                Modifier
                    .weight(1f)
                    .topFade()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.ms),
            ) {
                DayChips(day, onSelect = { day = it }, onPickOther = { pickingDate = true })

                if (isNew && frequentShifts.isNotEmpty()) {
                    Text("Tus turnos de siempre", style = MaterialTheme.typography.labelMedium, color = muted)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        frequentShifts.forEach { (start, end) ->
                            PillChip(
                                "${start.label()} – ${end.label()}",
                                selected = start == startTime && end == endTime,
                                onClick = {
                                    startTime = start
                                    endTime = end
                                    extraMinutes = 0
                                },
                            )
                        }
                    }
                }

                TurnosCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        TimeTile("Entré", startTime, null, Modifier.weight(1f)) { picking = TimeTarget.START }
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = muted)
                        TimeTile(
                            if (isOpenShift) "Salgo a las" else "Salí",
                            endTime,
                            if (endsNextDay) "día siguiente" else null,
                            Modifier.weight(1f),
                        ) { picking = TimeTarget.END }
                    }
                    if (!isOpenShift) {
                        val breakdown = PayrollCalculator.breakdown(draft, salary)
                        Row(Modifier.padding(top = Spacing.ms), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                hours(breakdown.workedMinutes),
                                style = MaterialTheme.typography.titleMedium.tabular(),
                                modifier = Modifier.weight(1f),
                            )
                            if (breakdown.total > 0) {
                                Text("+${money(breakdown.total)}", style = MaterialTheme.typography.titleMedium.tabular())
                            }
                        }
                        val tags = breakdown.tags(TurnosTheme.colors)
                        if (tags.isNotEmpty()) {
                            FlowRow(
                                Modifier.padding(top = Spacing.xs),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                            ) { tags.forEach { (text, style) -> StatusPill(text, style) } }
                        }
                    }
                }

                AccordionCard(
                    title = if (isOpenShift) "Almuerzo" else "Almuerzo y horas extra",
                    open = optionsOpen,
                    onToggle = { optionsOpen = !optionsOpen },
                ) {
                    OptionRow("Almuerzo sin pago", null) {
                        MinutesStepper(
                            value = breakMinutes,
                            zeroLabel = "No",
                            name = "almuerzo",
                            onChange = { breakOverride = it.coerceAtLeast(0) },
                        )
                    }
                    if (!isOpenShift) {
                        OptionRow("Te quedaste más tiempo", "Cuenta como hora extra") {
                            MinutesStepper(
                                value = extra,
                                zeroLabel = "No",
                                name = "tiempo extra",
                                onChange = { extraMinutes = it.coerceIn(0, duration - 1) },
                            )
                        }
                    }
                    if (touchesSundayOrHoliday) {
                        OptionRow("Me dan un día de descanso por este turno", "Si no te lo dan, también se paga la hora trabajada.") {
                            Switch(checked = compensatoryRest, onCheckedChange = { compensatoryRest = it })
                        }
                    }
                }

                if (!isNew) {
                    TextButton(onClick = { confirmingDelete = true }, Modifier.fillMaxWidth()) {
                        Text("Eliminar turno", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Column(Modifier.padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md)) {
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = Spacing.sm),
                    )
                }
                PrimaryAction(
                    if (isNew) "Guardar turno" else "Guardar cambios",
                    onClick = {
                        val problem = onSave(draft)
                        if (problem == null) onSaved() else error = problem
                    },
                )
            }
        }
    }

    picking?.let { target ->
        TimeGridSheet(
            title = if (target == TimeTarget.START) "¿A qué hora entraste?" else if (isOpenShift) "¿A qué hora sales?" else "¿A qué hora saliste?",
            initial = if (target == TimeTarget.START) startTime else endTime,
            onDismiss = { picking = null },
            onConfirm = {
                if (target == TimeTarget.START) startTime = it else endTime = it
                error = null
                picking = null
            },
        )
    }

    if (pickingDate) {
        DatePickerHost(day, { pickingDate = false }) {
            day = it
            error = null
            pickingDate = false
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("¿Eliminar este turno?") },
            text = { Text("Sus horas dejarán de contar en la liquidación. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = onDelete) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancelar") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayChips(day: LocalDate, onSelect: (LocalDate) -> Unit, onPickOther: () -> Unit) {
    val today = remember { LocalDate.now() }
    val recent = remember(today) { listOf(today, today.minusDays(1), today.minusDays(2)) }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        recent.forEachIndexed { index, date ->
            val text = when (index) {
                0 -> "Hoy"
                1 -> "Ayer"
                else -> date.dayLabel()
            }
            PillChip(text, selected = date == day, onClick = { onSelect(date) })
        }
        PillChip(
            if (day in recent) "Otro día" else day.dayLabel(),
            selected = day !in recent,
            onClick = onPickOther,
            icon = Icons.Rounded.CalendarMonth,
        )
    }
}

@Composable
private fun TimeTile(label: String, time: LocalTime, note: String?, modifier: Modifier, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(Radius.control),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(Spacing.ms)) {
            Text(if (note == null) label else "$label · $note", style = MaterialTheme.typography.labelMedium, color = muted, maxLines = 1)
            Text(time.label(), style = MaterialTheme.typography.titleLarge.tabular())
        }
    }
}

@Composable
private fun OptionRow(label: String, detail: String?, control: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        control()
    }
}

@Composable
private fun MinutesStepper(value: Int, zeroLabel: String, name: String, onChange: (Int) -> Unit) {
    Stepper(
        onDecrease = { onChange(value - MINUTES_STEP) },
        onIncrease = { onChange(value + MINUTES_STEP) },
        decreaseLabel = "Menos $name",
        increaseLabel = "Más $name",
    ) {
        Text(
            if (value == 0) zeroLabel else hours(value.toLong()),
            style = MaterialTheme.typography.labelLarge.tabular(),
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.widthIn(min = Sizes.counterValue),
        )
    }
}
