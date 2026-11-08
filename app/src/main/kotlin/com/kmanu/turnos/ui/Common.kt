package com.kmanu.turnos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.kmanu.turnos.domain.PayPeriod
import com.kmanu.turnos.ui.theme.Motion
import com.kmanu.turnos.ui.theme.Spacing
import com.kmanu.turnos.ui.theme.tabular
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val locale = Locale.forLanguageTag("es-CO")
private val symbols = DecimalFormatSymbols(locale).apply {
    groupingSeparator = '.'
    decimalSeparator = ','
}
private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM", locale)
private val shortDateFormat = DateTimeFormatter.ofPattern("d MMM", locale)
private val dayFormat = DateTimeFormatter.ofPattern("EEE d", locale)
private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", locale)

fun money(value: Long): String = (if (value < 0) "-$" else "$") + DecimalFormat("#,##0", symbols).format(abs(value))
fun hours(minutes: Long): String {
    val wholeHours = minutes / 60
    val rest = minutes % 60
    return when {
        wholeHours == 0L -> "$rest min"
        rest == 0L -> "$wholeHours h"
        else -> "$wholeHours h $rest min"
    }
}
fun LocalTime.label(): String = format(timeFormat)
fun LocalDate.label(): String = format(dateFormat)
fun LocalDate.shortLabel(): String = format(shortDateFormat)
fun LocalDate.dayLabel(): String = format(dayFormat).replaceFirstChar { it.uppercase(locale) }
fun LocalDateTime.timeLabel(): String = toLocalTime().label()

fun PayPeriod.title(): String = month.format(monthFormat).replaceFirstChar { it.uppercase(locale) }
fun PayPeriod.subtitle(): String =
    "Corte ${noveltiesFrom.format(shortDateFormat)} – ${noveltiesTo.format(shortDateFormat)} · pago ${payday.format(shortDateFormat)}"

/** Hora actual que se refresca sola, para el contador del turno abierto. */
@Composable
fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = LocalDateTime.now()
        }
    }
    return now
}

/** Cada corte es una página del paginador: 80 años desde [PERIOD_ORIGIN]. */
private val PERIOD_ORIGIN = YearMonth.of(2020, 1)
private const val PERIOD_PAGES = 80 * 12

fun periodAt(page: Int, cutoffDay: Int): PayPeriod = PayPeriod(PERIOD_ORIGIN.plusMonths(page.toLong()), cutoffDay)

/** Paginador de cortes que mantiene el corte elegido sincronizado entre pantallas. */
@Composable
fun rememberPeriodPager(period: PayPeriod, onSettled: (PayPeriod) -> Unit): PagerState {
    val pager = rememberPagerState(initialPage = ChronoUnit.MONTHS.between(PERIOD_ORIGIN, period.month).toInt()) { PERIOD_PAGES }
    val currentOnSettled by rememberUpdatedState(onSettled)
    val currentCutoffDay by rememberUpdatedState(period.cutoffDay)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { currentOnSettled(periodAt(it, currentCutoffDay)) }
    }
    return pager
}

@Composable
fun PeriodSelector(pager: PagerState, cutoffDay: Int) {
    val scope = rememberCoroutineScope()
    val period = periodAt(pager.currentPage, cutoffDay)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1, animationSpec = Motion.standard()) } }) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Corte anterior")
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(period.title(), style = MaterialTheme.typography.titleMedium)
            Text(
                period.subtitle(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1, animationSpec = Motion.standard()) } }) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Corte siguiente")
        }
    }
}

@Composable
fun AmountRow(
    label: String,
    value: String,
    detail: String? = null,
    emphasized: Boolean = false,
    detailColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                fontWeight = if (emphasized) FontWeight.SemiBold else null,
            )
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = detailColor)
            }
        }
        Text(
            value,
            style = (if (emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium).tabular(),
            fontWeight = if (emphasized) FontWeight.SemiBold else null,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerHost(initial: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                } ?: onDismiss()
            }) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    ) { DatePicker(state = state) }
}
