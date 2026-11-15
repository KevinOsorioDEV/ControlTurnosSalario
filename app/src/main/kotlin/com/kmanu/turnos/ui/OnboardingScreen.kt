package com.kmanu.turnos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.kmanu.turnos.domain.LegalPay
import com.kmanu.turnos.domain.PayPeriod
import com.kmanu.turnos.ui.theme.Radius
import com.kmanu.turnos.ui.theme.Spacing
import com.kmanu.turnos.ui.theme.TurnosTheme
import java.time.LocalDate

/** Primera pantalla al instalar: pide el salario y el día de corte; el auxilio sale de la ley. */
@Composable
fun OnboardingScreen(onDone: (salary: Long, cutoffDay: Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var cutoffText by remember { mutableStateOf(PayPeriod.DEFAULT_CUTOFF_DAY.toString()) }
    val cutoffDay = cutoffText.toIntOrNull()?.takeIf { it in PayPeriod.MIN_CUTOFF_DAY..PayPeriod.MAX_CUTOFF_DAY }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val salary = text.toLongOrNull()?.takeIf { it > 0 }
    val today = LocalDate.now()
    val legal = LegalPay.at(today)
    val allowance = LegalPay.allowanceFor(salary ?: 0, today)
    val allowanceDetail = if (salary != null && allowance == 0L) {
        "No aplica: la ley lo da a quienes ganan hasta ${money(legal.allowanceSalaryLimit)}."
    } else {
        "Valor de ley para ${today.year}. Si tu empresa paga otro, cámbialo en Ajustes."
    }
    val style = TurnosTheme.colors.closed

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.ms),
            ) {
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.hero),
                    color = style.container,
                    contentColor = style.content,
                ) {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text("Bienvenido a Turnos", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Marca tu entrada y tu salida, y mira cuánto te van a pagar.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                TurnosCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text("¿Cuánto ganas al mes?", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Con tu salario calculamos el valor de cada hora. Solo se guarda en este teléfono.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilledField(
                            value = text,
                            onValueChange = {
                                text = it.filter(Char::isDigit)
                                showError = false
                            },
                            label = "Salario mensual",
                            modifier = Modifier.focusRequester(focus),
                            isError = showError,
                            prefix = "$",
                            transformation = ThousandsTransformation,
                        )
                        if (showError) {
                            Text(
                                "Escribe un salario mayor que cero.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        AmountRow("Auxilio de transporte o conectividad", money(allowance), allowanceDetail)
                    }
                }
                TurnosCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text("¿Hasta qué día van las horas de cada pago?", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Las horas extra y recargos hasta este día se pagan ese mes; las siguientes, el mes que viene. " +
                                "Si no lo sabes, deja ${PayPeriod.DEFAULT_CUTOFF_DAY} y cámbialo luego en Ajustes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilledField(
                            value = cutoffText,
                            onValueChange = { cutoffText = it.filter(Char::isDigit).take(2) },
                            label = "Día de corte",
                            isError = cutoffDay == null,
                        )
                        if (cutoffDay == null) {
                            Text(
                                "Escribe un día entre ${PayPeriod.MIN_CUTOFF_DAY} y ${PayPeriod.MAX_CUTOFF_DAY}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            PrimaryAction(
                text = "Empezar",
                onClick = {
                    when {
                        salary == null -> showError = true
                        cutoffDay != null -> onDone(salary, cutoffDay)
                    }
                },
                modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md),
            )
        }
    }
}
