package com.kmanu.turnos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun LiquidationScreen(state: UiState, viewModel: AppViewModel) {
    val result = state.liquidation
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Liquidación", style = MaterialTheme.typography.headlineMedium)
        PeriodSelector(state.period, viewModel::previousPeriod, viewModel::nextPeriod)
        if (LocalDate.now() <= result.period.noveltiesTo) {
            Text(
                "Corte en curso: muestra lo acumulado hasta el último turno cerrado.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Recargos y extras", style = MaterialTheme.typography.titleMedium)
                AmountRow("Horas trabajadas en el corte", hours(result.workedMinutes))
                if (result.lines.isEmpty()) {
                    Text(
                        "Sin recargos ni extras en este corte.",
                        Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                result.lines.forEach { line ->
                    AmountRow(line.concept.label, money(line.amount), hours(line.minutes))
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                AmountRow("Total recargos y extras", money(result.noveltiesTotal), emphasized = true)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                AmountRow("Salario del mes", money(result.salary))
                AmountRow("Recargos y extras", money(result.noveltiesTotal))
                AmountRow("Devengado", money(result.earned))
                AmountRow("Auxilio de conectividad", money(result.allowance))
                AmountRow("Salud y pensión (8%)", money(-result.healthAndPension))
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                AmountRow("Neto a recibir", money(result.net), emphasized = true)
            }
        }
    }
}
