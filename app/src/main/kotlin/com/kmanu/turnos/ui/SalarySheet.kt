package com.kmanu.turnos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kmanu.turnos.domain.LegalPay
import com.kmanu.turnos.domain.SalaryChange
import com.kmanu.turnos.domain.SalaryHistory
import com.kmanu.turnos.ui.theme.Spacing
import com.kmanu.turnos.ui.theme.TurnosTheme
import java.time.LocalDate

private fun SalaryChange.sinceLabel(): String =
    if (from == SalaryHistory.SINCE_ALWAYS) "Desde el inicio" else "Desde el ${from.longLabel()}"

private fun SalaryChange.amountLabel(today: LocalDate): String =
    if (minimumWage) "Salario mínimo · hoy ${money(salaryAt(today))}" else money(monthlySalary)

/**
 * Salario con su historial. Cada cambio tiene fecha porque cada hora se paga con el salario
 * vigente el día en que se trabajó: un aumento en enero no cambia lo que se trabajó en diciembre.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SalarySheet(
    history: SalaryHistory,
    suggestedStart: LocalDate,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (SalaryHistory) -> Unit,
    onRemove: (SalaryHistory) -> Unit,
) {
    var minimum by remember { mutableStateOf(history.changeAt(today).minimumWage) }
    var text by remember { mutableStateOf("") }
    var since by remember { mutableStateOf(suggestedStart) }
    var fromStart by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<SalaryChange?>(null) }
    val current = history.changeAt(today)
    val variant = MaterialTheme.colorScheme.onSurfaceVariant

    TurnosSheet(onDismiss) {
        Text("Salario mensual", style = MaterialTheme.typography.titleLarge)
        Text(
            "Cada hora se paga con el salario vigente el día en que la trabajaste. Por eso cada cambio lleva fecha.",
            style = MaterialTheme.typography.bodySmall,
            color = variant,
        )

        history.changes.asReversed().forEach { change ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Column(Modifier.weight(1f)) {
                    Text(change.amountLabel(today), style = MaterialTheme.typography.bodyLarge)
                    Text(change.sinceLabel(), style = MaterialTheme.typography.bodySmall, color = variant)
                }
                when {
                    change == current -> StatusPill("Vigente", TurnosTheme.colors.closed)
                    change.from > today -> StatusPill("Próximo", TurnosTheme.colors.active)
                }
                if (history.changes.size > 1) {
                    IconButton(onClick = { removing = change }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Quitar el salario ${change.sinceLabel().lowercase()}")
                    }
                }
            }
        }

        HorizontalDivider()
        Text("Registrar un cambio", style = MaterialTheme.typography.titleSmall)
        PillTabs(
            options = listOf("Monto fijo", "Salario mínimo"),
            selected = if (minimum) 1 else 0,
            onSelect = {
                minimum = it == 1
                showError = false
            },
        )
        if (minimum) {
            Text(
                "Sigue el decreto de cada año, hoy ${money(LegalPay.at(today).minimumWage)}. No tienes que cambiarlo en enero: " +
                    "basta con actualizar la app cuando salga el decreto.",
                style = MaterialTheme.typography.bodySmall,
                color = variant,
            )
        } else {
            FilledField(
                value = text,
                onValueChange = {
                    text = it.filter(Char::isDigit)
                    showError = false
                },
                label = "Nuevo salario",
                isError = showError,
                prefix = "$",
                transformation = ThousandsTransformation,
            )
            if (showError) {
                Text("Escribe un salario mayor que cero.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        Text("¿Desde cuándo aplica?", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PillChip(since.longLabel(), selected = !fromStart, icon = Icons.Rounded.CalendarMonth, onClick = { pickingDate = true })
            PillChip("Desde el inicio", selected = fromStart, onClick = { fromStart = !fromStart })
        }
        Text(
            if (fromStart) {
                "Reemplaza el salario inicial y recalcula todos los cortes. Úsalo si lo escribiste mal al empezar."
            } else {
                "Las horas desde esa fecha se pagan con este salario; las anteriores conservan el que tenían."
            },
            style = MaterialTheme.typography.bodySmall,
            color = variant,
        )
        PrimaryAction("Guardar", onClick = {
            val start = if (fromStart) SalaryHistory.SINCE_ALWAYS else since
            val change = if (minimum) {
                SalaryChange.minimumWage(start)
            } else {
                text.toLongOrNull()?.takeIf { it > 0 }?.let { SalaryChange(start, it) }
            }
            if (change == null) showError = true else onSave(history.withChange(change))
        })
    }

    if (pickingDate) {
        DatePickerHost(since, onDismiss = { pickingDate = false }) {
            since = it
            fromStart = false
            pickingDate = false
        }
    }

    removing?.let { change ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("¿Quitar este salario?") },
            text = {
                // El tramo más antiguo no tiene uno anterior: sus fechas pasan al siguiente.
                val replacement = if (change == history.changes.first()) "siguiente" else "anterior"
                Text("${change.sinceLabel()}: las horas de esas fechas se pagarán con el salario $replacement.")
            },
            confirmButton = {
                TextButton(onClick = {
                    history.without(change.from)?.let(onRemove)
                    removing = null
                }) { Text("Quitar") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancelar") } },
        )
    }
}
