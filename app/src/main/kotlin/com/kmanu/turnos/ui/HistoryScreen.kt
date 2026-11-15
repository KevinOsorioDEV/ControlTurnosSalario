package com.kmanu.turnos.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.kmanu.turnos.domain.Concept
import com.kmanu.turnos.domain.ExcelReport
import com.kmanu.turnos.domain.PayrollCalculator
import com.kmanu.turnos.domain.SalaryHistory
import com.kmanu.turnos.domain.Shift
import com.kmanu.turnos.domain.ShiftBreakdown
import com.kmanu.turnos.ui.theme.Motion
import com.kmanu.turnos.ui.theme.Radius
import com.kmanu.turnos.ui.theme.Spacing
import com.kmanu.turnos.ui.theme.StatusStyle
import com.kmanu.turnos.ui.theme.TurnosColors
import com.kmanu.turnos.ui.theme.TurnosTheme
import com.kmanu.turnos.ui.theme.tabular
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var askingName by remember { mutableStateOf(false) }
    val export: (String) -> Unit = { name ->
        val period = periodAt(pager.currentPage, state.settings.cutoffDay)
        scope.launch {
            viewModel.writeExcel(File(context.cacheDir, "exports"), period, name)?.let(context::shareExcel)
        }
    }

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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Historial", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                                PillChip(
                                    "Exportar a Excel",
                                    selected = false,
                                    icon = Icons.Rounded.Share,
                                    onClick = {
                                        if (state.settings.workerName.isBlank()) askingName = true else export(state.settings.workerName)
                                    },
                                )
                            }
                            PeriodSelector(pager, state.settings.cutoffDay)
                        }
                        HorizontalPager(pager, Modifier.weight(1f)) { page ->
                            val pageState = remember(state, page) { state.copy(period = periodAt(page, state.settings.cutoffDay)) }
                            LazyColumn(
                                Modifier.fillMaxSize().topFade(),
                                state = listStates.getOrPut(page) { LazyListState() },
                                contentPadding = PaddingValues(
                                    start = Spacing.md,
                                    top = Spacing.sm,
                                    end = Spacing.md,
                                    bottom = Spacing.fabClearance,
                                ),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                if (pageState.periodShifts.any { it.end != null }) {
                                    item(key = "resumen") { PeriodSummary(pageState) }
                                }
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
                                        state.salary,
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
                    ) { Icon(Icons.Rounded.Add, contentDescription = "Agregar turno") }
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
                    breakPolicy = state.settings.breakPolicy,
                    salary = state.salary,
                    frequentShifts = state.frequentShifts,
                    knownAuthorizers = state.knownAuthorizers,
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

    if (askingName) {
        WorkerNameSheet(
            onDismiss = { askingName = false },
            onDone = { name ->
                askingName = false
                viewModel.saveWorkerName(name)
                export(name)
            },
        )
    }
}

/** Totales del corte, para no tener que ir a Liquidación a verlos. */
@Composable
private fun PeriodSummary(state: UiState) {
    val liquidation = state.liquidation
    val closed = state.periodShifts.count { it.end != null }
    TurnosCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SummaryValue(if (closed == 1) "Turno" else "Turnos", closed.toString())
            SummaryValue("Horas", hours(liquidation.workedMinutes))
            SummaryValue("Recargos y extras", money(liquidation.noveltiesTotal), alignEnd = true)
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: String, alignEnd: Boolean = false) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium.tabular())
    }
}

/** Se pide una sola vez: el nombre encabeza el Excel que se exporta. */
@Composable
private fun WorkerNameSheet(onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    TurnosSheet(onDismiss) {
        Text("¿Qué nombre va en el Excel?", style = MaterialTheme.typography.titleLarge)
        FilledField(
            value = name,
            onValueChange = {
                name = it
                showError = false
            },
            label = "Tu nombre",
            isError = showError,
            keyboard = KeyboardType.Text,
        )
        if (showError) {
            Text("Escribe tu nombre.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        PrimaryAction("Exportar", onClick = { if (name.isBlank()) showError = true else onDone(name.trim()) })
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
internal fun ShiftBreakdown.tags(colors: TurnosColors): List<Pair<String, StatusStyle>> = buildList {
    val concepts = lines.map { it.concept }.toSet()
    if (concepts.any { it in HOLIDAY_CONCEPTS }) add("Domingo o festivo" to colors.holiday)
    if (concepts.any { it in NIGHT_CONCEPTS }) add("De noche" to colors.night)
    val extraMinutes = lines.filter { it.concept in EXTRA_CONCEPTS }.sumOf { it.minutes }
    if (extraMinutes > 0) add("${hours(extraMinutes)} extra" to colors.extra)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShiftRow(shift: Shift, salary: SalaryHistory, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = TurnosTheme.colors
    val breakdown = shift.end?.let { PayrollCalculator.breakdown(shift, salary) }
    val missingAuthorizer = breakdown != null && shift.authorizedBy.isNullOrBlank() &&
        breakdown.lines.any { it.concept in EXTRA_CONCEPTS }
    val tags = (breakdown?.tags(colors) ?: listOf("En curso" to colors.active)) +
        if (missingAuthorizer) listOf(ExcelReport.MISSING_AUTHORIZER to colors.extra) else emptyList()
    // Solo se muestra si difiere de lo marcado, para no repetir el mismo horario.
    val paid = shift.end?.let { PayrollCalculator.paidInterval(shift) }?.takeIf { it.start != shift.start || it.end != shift.end }
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
                paid?.let {
                    Text(
                        "Se paga ${it.start.timeLabel()} – ${it.end.timeLabel()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
