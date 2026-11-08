package com.kmanu.turnos.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import com.kmanu.turnos.data.UserSettings
import com.kmanu.turnos.domain.PayPeriod
import com.kmanu.turnos.ui.theme.Radius
import com.kmanu.turnos.ui.theme.Spacing
import com.kmanu.turnos.ui.theme.TurnosTheme
import kotlin.math.roundToInt

private fun Int.minutesAsHoursText(): String =
    if (this % 60 == 0) (this / 60).toString() else (this / 60.0).toString().replace('.', ',')

private fun String.hoursToMinutes(): Int? = replace(',', '.').toDoubleOrNull()?.let { (it * 60).roundToInt() }

private fun String.digitsOnly(): String = filter(Char::isDigit)
private fun String.decimalInput(): String = filter { it.isDigit() || it == ',' || it == '.' }

/** Un dato de Ajustes: cómo se muestra, cómo se edita y cómo se valida. */
private class SettingItem(
    val label: String,
    val error: String,
    val help: String? = null,
    val isMoney: Boolean = false,
    val decimal: Boolean = false,
    val shown: (UserSettings) -> String,
    val text: (UserSettings) -> String,
    val apply: (UserSettings, String) -> UserSettings?,
)

private val PAY_ITEMS = listOf(
    SettingItem(
        label = "Salario mensual",
        error = "Escribe un salario mayor que cero.",
        isMoney = true,
        shown = { money(it.monthlySalary) },
        text = { it.monthlySalary.toString() },
        apply = { s, t -> t.toLongOrNull()?.takeIf { it > 0 }?.let { s.copy(monthlySalary = it) } },
    ),
    SettingItem(
        label = "Auxilio de conectividad",
        error = "Escribe el valor del auxilio, o 0 si no tienes.",
        isMoney = true,
        shown = { money(it.monthlyAllowance) },
        text = { it.monthlyAllowance.toString() },
        apply = { s, t -> t.toLongOrNull()?.let { s.copy(monthlyAllowance = it) } },
    ),
    SettingItem(
        label = "Día de corte",
        error = "Escribe un día entre ${PayPeriod.MIN_CUTOFF_DAY} y ${PayPeriod.MAX_CUTOFF_DAY}.",
        help = "Último día de horas que entra en el pago de cada mes. Al cambiarlo se reorganizan también los cortes anteriores.",
        shown = { "Día ${it.cutoffDay}" },
        text = { it.cutoffDay.toString() },
        apply = { s, t -> t.toIntOrNull()?.takeIf { it in PayPeriod.MIN_CUTOFF_DAY..PayPeriod.MAX_CUTOFF_DAY }?.let { s.copy(cutoffDay = it) } },
    ),
)

private val SHIFT_ITEMS = listOf(
    SettingItem(
        label = "Duración habitual (horas)",
        error = "Escribe una duración menor de 24 horas.",
        decimal = true,
        shown = { hours(it.defaultShiftMinutes.toLong()) },
        text = { it.defaultShiftMinutes.minutesAsHoursText() },
        apply = { s, t -> t.hoursToMinutes()?.takeIf { it in 1 until 24 * 60 }?.let { s.copy(defaultShiftMinutes = it) } },
    ),
    SettingItem(
        label = "Almuerzo sin pago (minutos)",
        error = "Escribe los minutos, o 0 si tu turno no incluye almuerzo.",
        help = "Déjalo en 0 si tu turno no incluye almuerzo.",
        shown = { if (it.breakMinutes == 0) "Sin almuerzo" else hours(it.breakMinutes.toLong()) },
        text = { it.breakMinutes.toString() },
        apply = { s, t -> t.toIntOrNull()?.let { s.copy(breakMinutes = it) } },
    ),
    SettingItem(
        label = "Aplicar en turnos de más de (horas)",
        error = "Escribe un número de horas.",
        decimal = true,
        shown = { hours(it.breakOverMinutes.toLong()) },
        text = { it.breakOverMinutes.minutesAsHoursText() },
        apply = { s, t -> t.hoursToMinutes()?.takeIf { it >= 0 }?.let { s.copy(breakOverMinutes = it) } },
    ),
)

@Composable
fun SettingsScreen(settings: UserSettings, onSave: (UserSettings) -> Unit) {
    var editing by remember { mutableStateOf<SettingItem?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium)
        SettingsGroup("Tu pago", PAY_ITEMS, settings) { editing = it }
        SettingsGroup("Tu turno", SHIFT_ITEMS, settings) { editing = it }
    }

    editing?.let { item ->
        SettingSheet(item, settings, onDismiss = { editing = null }) {
            onSave(it)
            editing = null
        }
    }
}

@Composable
private fun SettingsGroup(title: String, items: List<SettingItem>, settings: UserSettings, onOpen: (SettingItem) -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.xs, top = Spacing.sm),
    )
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.card), color = TurnosTheme.colors.card) {
        Column {
            items.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = Spacing.md), color = MaterialTheme.colorScheme.surfaceContainer)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onOpen(item) }
                        .padding(start = Spacing.md, end = Spacing.sm, top = Spacing.md, bottom = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(item.label.substringBefore(" ("), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(item.shown(settings), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSheet(item: SettingItem, settings: UserSettings, onDismiss: () -> Unit, onDone: (UserSettings) -> Unit) {
    var text by remember { mutableStateOf(item.text(settings)) }
    var showError by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    TurnosSheet(onDismiss) {
        Text(item.label.substringBefore(" ("), style = MaterialTheme.typography.titleLarge)
        item.help?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledField(
            value = text,
            onValueChange = {
                text = if (item.decimal) it.decimalInput() else it.digitsOnly()
                showError = false
            },
            label = item.label,
            modifier = Modifier.focusRequester(focus),
            isError = showError,
            keyboard = if (item.decimal) KeyboardType.Decimal else KeyboardType.Number,
            prefix = if (item.isMoney) "$" else null,
            transformation = if (item.isMoney) ThousandsTransformation else VisualTransformation.None,
        )
        if (showError) {
            Text(item.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        PrimaryAction("Listo", onClick = {
            val updated = item.apply(settings, text.trim())
            if (updated == null) showError = true else onDone(updated)
        })
    }
}
