package com.kmanu.turnos.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import com.kmanu.turnos.data.UserSettings
import com.kmanu.turnos.domain.PayPeriod
import com.kmanu.turnos.ui.theme.Radius
import com.kmanu.turnos.ui.theme.Spacing
import com.kmanu.turnos.ui.theme.TurnosTheme
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 470 → "7,83": dos decimales bastan y al volver a leerlo da los mismos minutos. */
private fun Int.minutesAsHoursText(): String =
    java.math.BigDecimal(this).divide(java.math.BigDecimal(60), 2, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros().toPlainString().replace('.', ',')

private fun String.hoursToMinutes(): Int? = replace(',', '.').toDoubleOrNull()?.let { (it * 60).roundToInt() }

private fun String.digitsOnly(): String = filter(Char::isDigit)
private fun String.decimalInput(): String = filter { it.isDigit() || it == ',' || it == '.' }

/** Un dato de Ajustes: cómo se muestra, cómo se edita y cómo se valida. */
private class SettingItem(
    val label: String,
    val error: String,
    val help: String? = null,
    /** Nombre en la lista cuando el completo no cabe en una línea. */
    val shortLabel: String? = null,
    /** Algunos datos solo tienen sentido según otros. */
    val visible: (UserSettings) -> Boolean = { true },
    val isMoney: Boolean = false,
    val decimal: Boolean = false,
    val isText: Boolean = false,
    /** El salario y el auxilio se editan en su propia hoja, con historial y fecha. */
    val isSalary: Boolean = false,
    val isAllowance: Boolean = false,
    val shown: (UserSettings) -> String,
    val text: (UserSettings) -> String,
    val apply: (UserSettings, String) -> UserSettings?,
)

private const val MAX_MARGIN_MINUTES = 120
private const val MAX_EXIT_NOTICE_MINUTES = 120

private val PAY_ITEMS = listOf(
    SettingItem(
        label = "Tu nombre",
        error = "Escribe tu nombre.",
        help = "Aparece en el Excel que exportes.",
        isText = true,
        shown = { it.workerName.ifBlank { "Sin nombre" } },
        text = { it.workerName },
        apply = { s, t -> t.takeIf(String::isNotBlank)?.let { s.copy(workerName = it) } },
    ),
    SettingItem(
        label = "Salario mensual",
        error = "Escribe un salario mayor que cero.",
        isMoney = true,
        isSalary = true,
        shown = {
            if (it.salaryHistory.changeAt(LocalDate.now()).minimumWage) "Mínimo · ${money(it.monthlySalary)}" else money(it.monthlySalary)
        },
        text = { it.monthlySalary.toString() },
        apply = { s, t -> t.toLongOrNull()?.takeIf { it > 0 }?.let { s.copy(monthlySalary = it) } },
    ),
    SettingItem(
        label = "Auxilio de transporte o conectividad",
        shortLabel = "Auxilio de transporte",
        error = "",
        isAllowance = true,
        shown = {
            if (it.allowanceHistory.changeAt(LocalDate.now()).isLegal) "Ley · ${money(it.monthlyAllowance)}" else money(it.monthlyAllowance)
        },
        text = { it.monthlyAllowance.toString() },
        apply = { s, _ -> s },
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
        label = "Margen de puntualidad (minutos)",
        error = "Escribe los minutos, entre 0 y $MAX_MARGIN_MINUTES.",
        help = "Si llegas o sales dentro de este margen, se paga tu horario programado. Solo aplica a los turnos que marques de ahora en adelante.",
        shown = { if (it.marginMinutes == 0) "Sin margen" else "${it.marginMinutes} min" },
        text = { it.marginMinutes.toString() },
        apply = { s, t -> t.toIntOrNull()?.takeIf { it in 0..MAX_MARGIN_MINUTES }?.let { s.copy(marginMinutes = it) } },
    ),
    SettingItem(
        label = "Avisar antes de la salida (minutos)",
        error = "Escribe los minutos, entre 0 y $MAX_EXIT_NOTICE_MINUTES.",
        help = "Te llega una notificación normal a esa hora exacta. Escribe 0 para no recibirla.",
        shown = { if (it.exitNoticeMinutes == 0) "Desactivado" else "${it.exitNoticeMinutes} min antes" },
        text = { it.exitNoticeMinutes.toString() },
        apply = { s, t -> t.toIntOrNull()?.takeIf { it in 0..MAX_EXIT_NOTICE_MINUTES }?.let { s.copy(exitNoticeMinutes = it) } },
    ),
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
        apply = { s, t -> t.toIntOrNull()?.takeIf { it in 0 until 24 * 60 }?.let { s.copy(breakMinutes = it) } },
    ),
    SettingItem(
        label = "Descontar almuerzo en turnos de más de (horas)",
        shortLabel = "Solo en turnos de más de",
        visible = { it.breakMinutes > 0 },
        error = "Escribe un número de horas.",
        decimal = true,
        shown = { hours(it.breakOverMinutes.toLong()) },
        text = { it.breakOverMinutes.minutesAsHoursText() },
        apply = { s, t -> t.hoursToMinutes()?.takeIf { it in 0 until 24 * 60 }?.let { s.copy(breakOverMinutes = it) } },
    ),
)

@Composable
fun SettingsScreen(
    settings: UserSettings,
    viewModel: AppViewModel,
    lockEnabled: Boolean,
    onLockChange: (Boolean) -> Unit,
) {
    var editing by remember { mutableStateOf<SettingItem?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium)
        SettingsGroup("Tu pago", PAY_ITEMS, settings) { editing = it }
        SettingsGroup("Tu turno", SHIFT_ITEMS, settings) { editing = it }
        SecurityGroup(lockEnabled, onLockChange)
        DataGroup(viewModel)
    }

    editing?.let { item ->
        if (item.isSalary) {
            SalarySheet(
                history = settings.salaryHistory,
                suggestedStart = viewModel.salaryChangeStart(),
                today = viewModel.today(),
                onDismiss = { editing = null },
                onSave = {
                    viewModel.saveSalaryHistory(it)
                    editing = null
                },
                // Quitar un cambio deja la hoja abierta para seguir revisando el historial.
                onRemove = { viewModel.saveSalaryHistory(it, "Salario quitado") },
            )
        } else if (item.isAllowance) {
            AllowanceSheet(
                history = settings.allowanceHistory,
                salary = settings.salaryHistory,
                suggestedStart = viewModel.salaryChangeStart(),
                today = viewModel.today(),
                onDismiss = { editing = null },
                onSave = {
                    viewModel.saveAllowanceHistory(it)
                    editing = null
                },
                onRemove = { viewModel.saveAllowanceHistory(it, "Auxilio quitado") },
            )
        } else {
            SettingSheet(item, settings, onDismiss = { editing = null }) { updated ->
                viewModel.saveSettings(updated)
                editing = null
            }
        }
    }
}

/** Copia de seguridad en un archivo que la persona guarda donde quiera (Drive, correo, la memoria). */
@Composable
private fun DataGroup(viewModel: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingRestore by remember { mutableStateOf<String?>(null) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                val text = viewModel.backupText()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
            }.isSuccess
            viewModel.showMessage(if (ok) "Copia de seguridad guardada" else "No se pudo guardar la copia.")
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                }
            }.getOrNull()
            if (text == null) viewModel.showMessage("No se pudo abrir el archivo.") else pendingRestore = text
        }
    }

    GroupTitle("Tus datos")
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.card), color = TurnosTheme.colors.card) {
        Column {
            ActionRow("Guardar copia de seguridad", "Turnos, salarios y ajustes en un archivo. Guárdalo en Drive o envíatelo.") {
                save.launch("Turnos-respaldo-${LocalDate.now()}.json")
            }
            HorizontalDivider(Modifier.padding(horizontal = Spacing.md), color = MaterialTheme.colorScheme.surfaceContainer)
            ActionRow("Restaurar una copia", "Reemplaza los turnos y ajustes de este teléfono por los del archivo.") {
                open.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
            }
        }
    }

    pendingRestore?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("¿Restaurar la copia?") },
            text = { Text("Se borrarán los turnos y ajustes actuales de este teléfono y se reemplazarán por los de la copia.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch { viewModel.showMessage(viewModel.restore(text) ?: "Copia restaurada") }
                }) { Text("Restaurar") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ActionRow(title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = Spacing.md, end = Spacing.sm, top = Spacing.md, bottom = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GroupTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.xs, top = Spacing.sm),
    )
}

@Composable
private fun SecurityGroup(enabled: Boolean, onChange: (Boolean) -> Unit) {
    GroupTitle("Seguridad")
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.card), color = TurnosTheme.colors.card) {
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = enabled, role = Role.Switch, onValueChange = onChange)
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Pedir huella o PIN", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Al abrir la app y cada vez que salgas de ella.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = null)
        }
    }
}

@Composable
private fun SettingsGroup(title: String, items: List<SettingItem>, settings: UserSettings, onOpen: (SettingItem) -> Unit) {
    GroupTitle(title)
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.card), color = TurnosTheme.colors.card) {
        Column {
            items.filter { it.visible(settings) }.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = Spacing.md), color = MaterialTheme.colorScheme.surfaceContainer)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onOpen(item) }
                        .padding(start = Spacing.md, end = Spacing.sm, top = Spacing.md, bottom = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(item.shortLabel ?: item.label.substringBefore(" ("), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
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
private fun SettingSheet(
    item: SettingItem,
    settings: UserSettings,
    onDismiss: () -> Unit,
    onDone: (UserSettings) -> Unit,
) {
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
                text = when {
                    item.isText -> it
                    item.decimal -> it.decimalInput()
                    else -> it.digitsOnly()
                }
                showError = false
            },
            label = item.label,
            modifier = Modifier.focusRequester(focus),
            isError = showError,
            keyboard = when {
                item.isText -> KeyboardType.Text
                item.decimal -> KeyboardType.Decimal
                else -> KeyboardType.Number
            },
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
