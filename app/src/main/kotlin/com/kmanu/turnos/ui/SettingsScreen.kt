package com.kmanu.turnos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kmanu.turnos.data.UserSettings
import kotlin.math.roundToInt

private fun Int.minutesAsHoursText(): String =
    if (this % 60 == 0) (this / 60).toString() else (this / 60.0).toString().replace('.', ',')

private fun String.hoursToMinutes(): Int? = replace(',', '.').toDoubleOrNull()?.let { (it * 60).roundToInt() }

@Composable
fun SettingsScreen(settings: UserSettings, onSave: (UserSettings) -> Unit) {
    var salary by remember(settings) { mutableStateOf(settings.monthlySalary.toString()) }
    var allowance by remember(settings) { mutableStateOf(settings.monthlyAllowance.toString()) }
    var shiftHours by remember(settings) { mutableStateOf(settings.defaultShiftMinutes.minutesAsHoursText()) }
    var breakMinutes by remember(settings) { mutableStateOf(settings.breakMinutes.toString()) }
    var breakOverHours by remember(settings) { mutableStateOf(settings.breakOverMinutes.minutesAsHoursText()) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium)
        NumberField("Salario mensual", salary, KeyboardType.Number) { salary = it.filter(Char::isDigit) }
        NumberField("Auxilio de conectividad mensual", allowance, KeyboardType.Number) { allowance = it.filter(Char::isDigit) }
        NumberField("Duración habitual del turno (horas)", shiftHours, KeyboardType.Decimal) { shiftHours = it }

        Text("Almuerzo no pagado", style = MaterialTheme.typography.titleMedium)
        Text(
            "Déjalo en 0 si tu turno no incluye almuerzo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NumberField("Minutos de almuerzo", breakMinutes, KeyboardType.Number) { breakMinutes = it.filter(Char::isDigit) }
        NumberField("Aplicar en turnos de más de (horas)", breakOverHours, KeyboardType.Decimal) { breakOverHours = it }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                val parsedSalary = salary.toLongOrNull()
                val parsedAllowance = allowance.toLongOrNull()
                val parsedShift = shiftHours.hoursToMinutes()
                val parsedBreak = breakMinutes.toIntOrNull()
                val parsedBreakOver = breakOverHours.hoursToMinutes()
                if (parsedSalary == null || parsedSalary <= 0 || parsedAllowance == null ||
                    parsedShift == null || parsedShift !in 1 until 24 * 60 ||
                    parsedBreak == null || parsedBreakOver == null || parsedBreakOver < 0
                ) {
                    error = "Revisa los valores: hay alguno vacío o fuera de rango."
                } else {
                    error = null
                    onSave(UserSettings(parsedSalary, parsedAllowance, parsedShift, parsedBreak, parsedBreakOver))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Guardar") }
    }
}

@Composable
private fun NumberField(label: String, value: String, keyboard: KeyboardType, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}
