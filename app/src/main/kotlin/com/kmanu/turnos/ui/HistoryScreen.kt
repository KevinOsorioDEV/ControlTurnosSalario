package com.kmanu.turnos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.kmanu.turnos.domain.ColombiaHolidays
import com.kmanu.turnos.domain.Concept
import com.kmanu.turnos.domain.PayrollCalculator
import com.kmanu.turnos.domain.Shift
import com.kmanu.turnos.domain.ShiftBreakdown
import com.kmanu.turnos.ui.theme.Motion
import com.kmanu.turnos.ui.theme.Radius
import com.kmanu.turnos.ui.theme.Spacing
import com.kmanu.turnos.ui.theme.StatusStyle
import com.kmanu.turnos.ui.theme.TurnosColors
import com.kmanu.turnos.ui.theme.TurnosTheme
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

private data class Editing(val shift: Shift, val isNew: Boolean) {
    val transitionKey: String get() = if (isNew) NEW_SHIFT_KEY else shift.transitionKey
}

private const val NEW_SHIFT_KEY = "turno-nuevo"
private val Shift.transitionKey: String get() = "turno-$id"

private val cardBounds = BoundsTransform { _, _ -> Motion.standard() }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HistoryScreen(state: UiState, viewModel: AppViewModel, onEditorOpenChange: (Boolean) -> Unit) {
    var editing by remember { mutableStateOf<Editing?>(null) }
    val pager = rememberPeriodPager(state.period, viewModel::selectPeriod)
    // Al volver del editor cada corte conserva su posición en la lista.
    val listStates = remember { mutableMapOf<Int, LazyListState>() }

    LaunchedEffect(editing != null) { onEditorOpenChange(editing != null) }

    SharedTransitionLayout(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = editing,
            contentKey = { it?.transitionKey },
            transitionSpec = { fadeIn(Motion.standard()) togetherWith fadeOut(Motion.standard(Motion.SHORT)) },
            label = "historial y editor",
        ) { current ->
            if (current == null) {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        Column(Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.md)) {
                            Text("Historial", style = MaterialTheme.typography.headlineMedium)
                            PeriodSelector(pager, state.settings.cutoffDay)
                        }
                        HorizontalPager(pager, Modifier.weight(1f)) { page ->
                            val pageState = remember(state, page) { state.copy(period = periodAt(page, state.settings.cutoffDay)) }
                            LazyColumn(
                                Modifier.fillMaxSize(),
                                state = listStates.getOrPut(page) { LazyListState() },
                                contentPadding = PaddingValues(
                                    start = Spacing.md,
                                    top = Spacing.sm,
                                    end = Spacing.md,
                                    bottom = Spacing.fabClearance,
                                ),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                if (pageState.periodShifts.isEmpty()) {
                                    item {
                                        Text(
                                            "No hay turnos en este corte.",
                                            Modifier.padding(vertical = Spacing.lg),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                items(pageState.periodShifts, key = { it.id }) { shift ->
                                    ShiftRow(
                                        shift,
                                        state.settings.monthlySalary,
                                        Modifier.sharedBounds(
                                            rememberSharedContentState(shift.transitionKey),
                                            this@AnimatedContent,
                                            boundsTransform = cardBounds,
                                            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(Radius.card)),
                                        ),
                                    ) { editing = Editing(shift, isNew = false) }
                                }
                            }
                        }
                    }
                    FloatingActionButton(
                        onClick = {
                            val start = LocalDate.now().atTime(LocalTime.of(7, 0))
                            val end = start.plusMinutes(state.settings.defaultShiftMinutes.toLong())
                            val breakMinutes = state.settings.breakPolicy.minutesFor(Duration.between(start, end).toMinutes())
                            editing = Editing(Shift(UUID.randomUUID().toString(), start, end, end, breakMinutes), isNew = true)
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(Spacing.md)
                            .sharedBounds(
                                rememberSharedContentState(NEW_SHIFT_KEY),
                                this@AnimatedContent,
                                boundsTransform = cardBounds,
                            ),
                        shape = RoundedCornerShape(Radius.control),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) { Icon(Icons.Filled.Add, contentDescription = "Agregar turno") }
                }
            } else {
                ShiftEditor(
                    modifier = Modifier.sharedBounds(
                        rememberSharedContentState(current.transitionKey),
                        this@AnimatedContent,
                        boundsTransform = cardBounds,
                        clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(Radius.card)),
                    ),
                    original = current.shift,
                    isNew = current.isNew,
                    onDismiss = { editing = null },
                    onSave = viewModel::save,
                    onDelete = {
                        viewModel.delete(current.shift.id)
                        editing = null
                    },
                    onSaved = { editing = null },
                )
            }
        }
    }
}

private val EXTRA_CONCEPTS = setOf(Concept.EXTRA_DAY, Concept.EXTRA_NIGHT, Concept.EXTRA_DAY_SUNDAY, Concept.EXTRA_NIGHT_SUNDAY)
private val NIGHT_CONCEPTS = setOf(Concept.NIGHT, Concept.SUNDAY_NIGHT, Concept.EXTRA_NIGHT, Concept.EXTRA_NIGHT_SUNDAY)
private val HOLIDAY_CONCEPTS = setOf(
    Concept.SUNDAY_DAY,
    Concept.SUNDAY_NIGHT,
    Concept.SUNDAY_NO_REST,
    Concept.EXTRA_DAY_SUNDAY,
    Concept.EXTRA_NIGHT_SUNDAY,
)

/** Etiquetas que resumen qué tuvo de especial un turno. */
private fun ShiftBreakdown.tags(colors: TurnosColors): List<Pair<String, StatusStyle>> = buildList {
    val concepts = lines.map { it.concept }.toSet()
    if (concepts.any { it in HOLIDAY_CONCEPTS }) add("Domingo o festivo" to colors.holiday)
    if (concepts.any { it in NIGHT_CONCEPTS }) add("De noche" to colors.night)
    val extraMinutes = lines.filter { it.concept in EXTRA_CONCEPTS }.sumOf { it.minutes }
    if (extraMinutes > 0) add("${hours(extraMinutes)} extra" to colors.extra)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShiftRow(shift: Shift, salary: Long, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = TurnosTheme.colors
    val breakdown = shift.end?.let { PayrollCalculator.breakdown(shift, salary) }
    val tags = breakdown?.tags(colors) ?: listOf("En curso" to colors.active)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        color = colors.card,
    ) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(shift.start.toLocalDate().label(), style = MaterialTheme.typography.titleSmall)
                Text(
                    "${shift.start.timeLabel()} – ${shift.end?.timeLabel() ?: "en curso"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) { tags.forEach { (text, style) -> StatusPill(text, style) } }
                }
            }
            if (breakdown != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(hours(breakdown.workedMinutes), style = MaterialTheme.typography.bodyMedium)
                    if (breakdown.total > 0) {
                        Text(money(breakdown.total), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShiftEditor(
    original: Shift,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Shift) -> String?,
    onDelete: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var start by remember { mutableStateOf(original.start) }
    var end by remember { mutableStateOf(original.end) }
    var scheduledEnd by remember { mutableStateOf(original.scheduledEnd) }
    var breakText by remember { mutableStateOf(original.breakMinutes.toString()) }
    var compensatoryRest by remember { mutableStateOf(original.compensatoryRest) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmingDelete by remember { mutableStateOf(false) }

    val touchesSundayOrHoliday = generateSequence(start.toLocalDate()) { it.plusDays(1) }
        .takeWhile { it <= (end ?: start).toLocalDate() }
        .any(ColombiaHolidays::isSundayOrHoliday)

    BackHandler(onBack = onDismiss)

    Surface(modifier.fillMaxSize(), color = TurnosTheme.colors.card) {
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.ms),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver sin guardar")
                }
                Text(
                    if (isNew) "Agregar turno" else "Editar turno",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            DateTimeField("Entrada", start) { start = it }
            end?.let { current -> DateTimeField("Salida", current) { end = it } }
            DateTimeField("Salida programada (después es extra)", scheduledEnd) { scheduledEnd = it }
            OutlinedTextField(
                value = breakText,
                onValueChange = { breakText = it.filter(Char::isDigit) },
                label = { Text("Almuerzo no pagado (minutos)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            if (touchesSundayOrHoliday) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Me dan un día de descanso por este turno", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Si no te lo dan, también se paga la hora trabajada.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = compensatoryRest, onCheckedChange = { compensatoryRest = it })
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            PrimaryAction(
                "Guardar",
                onClick = {
                    val updated = original.copy(
                        start = start,
                        end = end,
                        scheduledEnd = scheduledEnd,
                        breakMinutes = breakText.toIntOrNull() ?: 0,
                        compensatoryRest = compensatoryRest,
                    )
                    val problem = onSave(updated)
                    if (problem == null) onSaved() else error = problem
                },
            )
            if (!isNew) {
                TextButton(onClick = { confirmingDelete = true }, Modifier.fillMaxWidth()) {
                    Text("Eliminar turno", color = MaterialTheme.colorScheme.error)
                }
            }
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
