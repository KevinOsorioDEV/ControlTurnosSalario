package com.kmanu.turnos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.kmanu.turnos.data.UserSettings
import com.kmanu.turnos.domain.Concept
import com.kmanu.turnos.domain.LaborRules
import com.kmanu.turnos.domain.ManualInput
import com.kmanu.turnos.domain.PayPeriod
import com.kmanu.turnos.domain.PayrollCalculator
import com.kmanu.turnos.ui.theme.Sizes
import com.kmanu.turnos.ui.theme.Spacing
import java.math.BigDecimal

/** Lo escrito para un corte. `null` significa que el campo sigue con su valor sugerido. */
data class ManualDraft(
    val salary: String? = null,
    val allowance: String? = null,
    val days: String? = null,
    val sundayPercent: String? = null,
    val monthlyHours: String? = null,
    val hours: Map<Concept, String> = emptyMap(),
)

private const val FULL_MONTH_DAYS = 30
private val HUNDRED = BigDecimal(100)

/** Los conceptos del Excel; el pago sin compensatorio solo aplica a turnos marcados. */
private val ENTRY_CONCEPTS = Concept.entries - Concept.SUNDAY_NO_REST

private fun String.decimalOrNull(): BigDecimal? = replace(',', '.').toBigDecimalOrNull()
private fun String.keepDecimal(): String = filter { it.isDigit() || it == ',' || it == '.' }
private fun String.keepDigits(): String = filter(Char::isDigit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidationModeSelector(manual: Boolean, onChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        listOf("Mis turnos" to false, "Ingresar horas" to true).forEachIndexed { index, (label, value) ->
            SegmentedButton(
                selected = manual == value,
                onClick = { onChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index, 2),
            ) { Text(label) }
        }
    }
}

@Composable
fun ManualLiquidation(
    settings: UserSettings,
    period: PayPeriod,
    draft: ManualDraft,
    onDraftChange: (ManualDraft) -> Unit,
    detailOpen: Boolean,
    onToggleDetail: () -> Unit,
) {
    val rules = remember(period) { LaborRules.at(period.noveltiesTo) }
    val changesInCut = remember(period) { LaborRules.at(period.noveltiesFrom) != rules }
    val suggestedPercent = remember(rules) { rules.sundayRate.multiply(HUNDRED).stripTrailingZeros().toPlainString() }

    val salaryText = draft.salary ?: settings.monthlySalary.toString()
    val allowanceText = draft.allowance ?: settings.monthlyAllowance.toString()
    val daysText = draft.days ?: FULL_MONTH_DAYS.toString()
    val percentText = draft.sundayPercent ?: suggestedPercent
    val divisorText = draft.monthlyHours ?: rules.monthlyHours.toString()

    val salary = salaryText.toLongOrNull()?.takeIf { it > 0 }
    val allowance = allowanceText.toLongOrNull()
    val days = daysText.toIntOrNull()?.takeIf { it in 0..FULL_MONTH_DAYS }
    val percent = percentText.decimalOrNull()
    val divisor = divisorText.toIntOrNull()?.takeIf { it > 0 }

    val input = ManualInput(
        monthlySalary = salary ?: settings.monthlySalary,
        monthlyAllowance = allowance ?: settings.monthlyAllowance,
        daysWorked = days ?: FULL_MONTH_DAYS,
        monthlyHours = divisor ?: rules.monthlyHours,
        sundayRate = (percent ?: rules.sundayRate.multiply(HUNDRED)).divide(HUNDRED),
        hours = draft.hours.mapNotNull { (concept, text) -> text.decimalOrNull()?.let { concept to it } }.toMap(),
    )
    val result = remember(input, period) { PayrollCalculator.liquidateManual(input, period) }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.ms),
    ) {
        NetCard(result, showProgressNote = false)

        TurnosCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Horas del corte", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (draft != ManualDraft()) {
                    TextButton(onClick = { onDraftChange(ManualDraft()) }) { Text("Limpiar") }
                }
            }
            ENTRY_CONCEPTS.forEach { concept ->
                HoursRow(
                    concept = concept,
                    hourValue = PayrollCalculator.hourValue(input.monthlySalary, input.monthlyHours, concept, input.sundayRate),
                    value = draft.hours[concept].orEmpty(),
                    onChange = { onDraftChange(draft.copy(hours = draft.hours + (concept to it.keepDecimal()))) },
                )
            }
        }

        TurnosCard {
            Text("Datos de este corte", style = MaterialTheme.typography.titleMedium)
            Column(Modifier.padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ManualField("Salario mensual", salaryText, salary == null, KeyboardType.Number) {
                    onDraftChange(draft.copy(salary = it.keepDigits()))
                }
                ManualField("Auxilio de conectividad mensual", allowanceText, allowance == null, KeyboardType.Number) {
                    onDraftChange(draft.copy(allowance = it.keepDigits()))
                }
                ManualField("Días trabajados (máximo 30)", daysText, days == null, KeyboardType.Number) {
                    onDraftChange(draft.copy(days = it.keepDigits()))
                }
                ManualField("Recargo dominical y festivo (%)", percentText, percent == null, KeyboardType.Decimal) {
                    onDraftChange(draft.copy(sundayPercent = it.keepDecimal()))
                }
                ManualField("Divisor de horas del mes", divisorText, divisor == null, KeyboardType.Number) {
                    onDraftChange(draft.copy(monthlyHours = it.keepDigits()))
                }
                if (changesInCut) {
                    Text(
                        "En este corte cambió la ley. Sugerimos los valores vigentes al ${period.noveltiesTo.shortLabel()}; " +
                            "ajústalos si tu empresa liquidó con otros.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        CalculationCard(result, open = detailOpen, onToggle = onToggleDetail, workedLabel = "Horas con recargo o extra")
    }
}

@Composable
private fun HoursRow(concept: Concept, hourValue: Long, value: String, onChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(concept.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${money(hourValue)} por hora",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text("0", Modifier.fillMaxWidth(), textAlign = TextAlign.End) },
            isError = value.isNotEmpty() && value.decimalOrNull() == null,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.End),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(Sizes.hoursField),
        )
    }
}

@Composable
private fun ManualField(
    label: String,
    value: String,
    invalid: Boolean,
    keyboard: KeyboardType,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = invalid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}
