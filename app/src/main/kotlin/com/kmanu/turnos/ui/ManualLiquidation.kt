package com.kmanu.turnos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.kmanu.turnos.data.UserSettings
import com.kmanu.turnos.domain.Concept
import com.kmanu.turnos.domain.Json
import com.kmanu.turnos.domain.LaborRules
import com.kmanu.turnos.domain.ManualInput
import com.kmanu.turnos.domain.PayPeriod
import com.kmanu.turnos.domain.PayrollCalculator
import com.kmanu.turnos.ui.theme.Sizes
import com.kmanu.turnos.ui.theme.Spacing
import com.kmanu.turnos.ui.theme.tabular
import java.math.BigDecimal
import java.time.YearMonth

/** Lo escrito para un corte. `null` significa que el campo sigue con su valor sugerido. */
data class ManualDraft(
    val salary: String? = null,
    val allowance: String? = null,
    val days: String? = null,
    val sundayPercent: String? = null,
    val monthlyHours: String? = null,
    val hours: Map<Concept, String> = emptyMap(),
)

/** Borradores como JSON, para que sobrevivan al cerrar la app. Se omiten los vacíos. */
fun encodeDrafts(drafts: Map<YearMonth, ManualDraft>): String = Json.write(
    drafts.filterValues { it != ManualDraft() }.map { (month, draft) ->
        mapOf(
            "mes" to month.toString(),
            "salario" to draft.salary,
            "auxilio" to draft.allowance,
            "dias" to draft.days,
            "dominical" to draft.sundayPercent,
            "divisor" to draft.monthlyHours,
            "horas" to draft.hours.mapKeys { it.key.name },
        )
    },
)

/** Lo que no se pueda leer se descarta: es un borrador, no un dato que haya que conservar. */
fun decodeDrafts(text: String): Map<YearMonth, ManualDraft> = runCatching {
    (Json.parse(text) as List<*>).mapNotNull { entry ->
        val map = entry as? Map<*, *> ?: return@mapNotNull null
        val month = runCatching { YearMonth.parse(map["mes"] as String) }.getOrNull() ?: return@mapNotNull null
        val hours = (map["horas"] as? Map<*, *>).orEmpty().mapNotNull { (key, value) ->
            val concept = Concept.entries.firstOrNull { it.name == key } ?: return@mapNotNull null
            (value as? String)?.let { concept to it }
        }.toMap()
        month to ManualDraft(
            salary = map["salario"] as? String,
            allowance = map["auxilio"] as? String,
            days = map["dias"] as? String,
            sundayPercent = map["dominical"] as? String,
            monthlyHours = map["divisor"] as? String,
            hours = hours,
        )
    }.toMap()
}.getOrDefault(emptyMap())

private const val FULL_MONTH_DAYS = 30
private val HUNDRED = BigDecimal(100)
private val HALF_HOUR = BigDecimal("0.5")

private class HoursGroup(val title: String, val entries: List<Pair<Concept, String>>)

/** Los conceptos del Excel por grupos; el pago sin compensatorio solo aplica a turnos marcados. */
private val HOUR_GROUPS = listOf(
    HoursGroup("Horas extra", listOf(Concept.EXTRA_DAY to "Extra de día", Concept.EXTRA_NIGHT to "Extra de noche")),
    HoursGroup(
        "Domingo o festivo",
        listOf(
            Concept.SUNDAY_DAY to "Recargo de día",
            Concept.SUNDAY_NIGHT to "Recargo de noche",
            Concept.EXTRA_DAY_SUNDAY to "Extra de día",
            Concept.EXTRA_NIGHT_SUNDAY to "Extra de noche",
        ),
    ),
    HoursGroup("Recargo de noche", listOf(Concept.NIGHT to "Noche normal")),
)

private fun String.decimalOrNull(): BigDecimal? = replace(',', '.').toBigDecimalOrNull()
/** Deja dígitos y un solo separador decimal: "1,5,5" queda "1,55". */
private fun String.keepDecimal(): String {
    var separator = false
    return filter { char ->
        when {
            char.isDigit() -> true
            (char == ',' || char == '.') && !separator -> true.also { separator = true }
            else -> false
        }
    }
}
private fun String.keepDigits(): String = filter(Char::isDigit)

private fun BigDecimal.hoursText(): String = stripTrailingZeros().toPlainString().replace('.', ',')

/** Suma [delta] a las horas escritas; en cero el campo queda vacío y vuelve a mostrar el 0 tenue. */
private fun String.stepped(delta: BigDecimal): String {
    // Un valor que no se entiende se deja como está para no borrar lo escrito.
    val current = if (isEmpty()) BigDecimal.ZERO else decimalOrNull() ?: return this
    val next = (current + delta).max(BigDecimal.ZERO)
    return if (next.signum() == 0) "" else next.hoursText()
}

@Composable
fun LiquidationModeSelector(manual: Boolean, onChange: (Boolean) -> Unit) {
    PillTabs(
        options = listOf("Mis turnos", "Simular"),
        selected = if (manual) 1 else 0,
        onSelect = { onChange(it == 1) },
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
    )
}

@Composable
fun ManualLiquidation(
    settings: UserSettings,
    period: PayPeriod,
    draft: ManualDraft,
    onDraftChange: (ManualDraft) -> Unit,
    detailOpen: Boolean,
    onToggleDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rules = remember(period) { LaborRules.at(period.noveltiesTo) }
    val changesInCut = remember(period) { LaborRules.at(period.noveltiesFrom) != rules }
    val suggestedPercent = remember(rules) { rules.sundayRate.multiply(HUNDRED).stripTrailingZeros().toPlainString() }
    var openGroup by rememberSaveable { mutableStateOf<String?>(HOUR_GROUPS.first().title) }
    var dataOpen by rememberSaveable { mutableStateOf(false) }

    // Valores sugeridos: los vigentes el día de pago de este corte.
    val periodSalary = settings.salaryHistory.at(period.payday)
    val periodAllowance = settings.paySettings.allowanceAt(period.payday)
    val salaryText = draft.salary ?: periodSalary.toString()
    val allowanceText = draft.allowance ?: periodAllowance.toString()
    val daysText = draft.days ?: FULL_MONTH_DAYS.toString()
    val percentText = draft.sundayPercent ?: suggestedPercent
    val divisorText = draft.monthlyHours ?: rules.monthlyHours.toString()

    val salary = salaryText.toLongOrNull()?.takeIf { it > 0 }
    val allowance = allowanceText.toLongOrNull()
    val days = daysText.toIntOrNull()?.takeIf { it in 0..FULL_MONTH_DAYS }
    val percent = percentText.decimalOrNull()
    val divisor = divisorText.toIntOrNull()?.takeIf { it > 0 }
    val anyDataInvalid = salary == null || allowance == null || days == null || percent == null || divisor == null
    val anyHoursInvalid = draft.hours.values.any { it.isNotEmpty() && it.decimalOrNull() == null }

    val input = ManualInput(
        monthlySalary = salary ?: periodSalary,
        monthlyAllowance = allowance ?: periodAllowance,
        daysWorked = days ?: FULL_MONTH_DAYS,
        monthlyHours = divisor ?: rules.monthlyHours,
        sundayRate = (percent ?: rules.sundayRate.multiply(HUNDRED)).divide(HUNDRED),
        hours = draft.hours.mapNotNull { (concept, text) -> text.decimalOrNull()?.let { concept to it } }.toMap(),
    )
    val result = remember(input, period) { PayrollCalculator.liquidateManual(input, period) }

    fun setHours(concept: Concept, text: String) {
        val hours = if (text.isEmpty()) draft.hours - concept else draft.hours + (concept to text)
        onDraftChange(draft.copy(hours = hours))
    }

    Column(modifier.fillMaxSize().imePadding()) {
        // El neto se queda arriba: se ve cambiar mientras se ingresan las horas.
        Box(Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.sm)) {
            NetCard(result, simulated = true, invalidData = anyDataInvalid || anyHoursInvalid)
        }
        Column(
            Modifier
                .weight(1f)
                .topFade()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (draft != ManualDraft()) {
                TextButton(onClick = { onDraftChange(ManualDraft()) }, Modifier.align(Alignment.End)) { Text("Limpiar todo") }
            }

            HOUR_GROUPS.forEach { group ->
                val total = group.entries.fold(BigDecimal.ZERO) { sum, (concept, _) ->
                    sum + (draft.hours[concept]?.decimalOrNull() ?: BigDecimal.ZERO)
                }
                AccordionCard(
                    title = group.title,
                    summary = if (total.signum() > 0) "${total.hoursText()} h" else null,
                    open = openGroup == group.title,
                    onToggle = { openGroup = if (openGroup == group.title) null else group.title },
                ) {
                    group.entries.forEach { (concept, label) ->
                        HoursRow(
                            label = label,
                            hourValue = PayrollCalculator.hourValue(input.monthlySalary, input.monthlyHours, concept, input.sundayRate),
                            value = draft.hours[concept].orEmpty(),
                            onChange = { setHours(concept, it.keepDecimal()) },
                        )
                    }
                }
            }

            AccordionCard(
                title = "Datos para el cálculo",
                open = dataOpen || anyDataInvalid,
                onToggle = { dataOpen = !dataOpen },
            ) {
                FilledField(
                    salaryText,
                    { onDraftChange(draft.copy(salary = it.keepDigits())) },
                    "Salario mensual",
                    isError = salary == null,
                    prefix = "$",
                    transformation = ThousandsTransformation,
                )
                FilledField(
                    allowanceText,
                    { onDraftChange(draft.copy(allowance = it.keepDigits())) },
                    "Auxilio de transporte o conectividad",
                    isError = allowance == null,
                    prefix = "$",
                    transformation = ThousandsTransformation,
                )
                FilledField(daysText, { onDraftChange(draft.copy(days = it.keepDigits())) }, "Días trabajados (máximo 30)", isError = days == null)
                FilledField(
                    percentText,
                    { onDraftChange(draft.copy(sundayPercent = it.keepDecimal())) },
                    "Recargo dominical y festivo (%)",
                    isError = percent == null,
                    keyboard = KeyboardType.Decimal,
                )
                FilledField(divisorText, { onDraftChange(draft.copy(monthlyHours = it.keepDigits())) }, "Divisor de horas del mes", isError = divisor == null)
                if (changesInCut) {
                    Text(
                        "Este mes cambió la ley. Sugerimos los valores vigentes al ${period.noveltiesTo.shortLabel()}; " +
                            "ajústalos si tu empresa liquidó con otros.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!anyDataInvalid && !anyHoursInvalid) CalculationCard(result, open = detailOpen, onToggle = onToggleDetail, workedLabel = "Horas con recargo o extra", netLabel = "Neto simulado")
        }
    }
}

@Composable
private fun HoursRow(label: String, hourValue: Long, value: String, onChange: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val invalid = value.isNotEmpty() && value.decimalOrNull() == null
    val style = MaterialTheme.typography.titleMedium.tabular().copy(
        textAlign = TextAlign.Center,
        color = if (invalid) scheme.error else scheme.onSurface,
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("${money(hourValue)} por hora", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Stepper(
            onDecrease = { onChange(value.stepped(-HALF_HOUR)) },
            onIncrease = { onChange(value.stepped(HALF_HOUR)) },
            decreaseLabel = "Media hora menos de $label",
            increaseLabel = "Media hora más de $label",
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.width(Sizes.counterValue),
                singleLine = true,
                textStyle = style,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(scheme.primary),
                decorationBox = { field ->
                    Box(contentAlignment = Alignment.Center) {
                        if (value.isEmpty()) Text("0", style = style.copy(color = scheme.onSurfaceVariant))
                        field()
                    }
                },
            )
        }
    }
}
