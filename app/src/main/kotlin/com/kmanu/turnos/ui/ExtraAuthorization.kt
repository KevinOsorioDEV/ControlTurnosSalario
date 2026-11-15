package com.kmanu.turnos.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.FileProvider
import com.kmanu.turnos.domain.Shift
import com.kmanu.turnos.ui.theme.Spacing
import java.io.File
import java.time.Duration
import java.time.LocalDateTime

private const val XLSX_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/** Abre el selector de Android para enviar el archivo por WhatsApp, correo o Drive. */
fun Context.shareExcel(file: File) {
    val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = XLSX_TYPE
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(send, "Compartir Excel"))
}

/** Quién autorizó las horas extra y una nota opcional, para la bitácora. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthorizationFields(
    authorizedBy: String,
    onAuthorizedByChange: (String) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    known: List<String>,
) {
    FilledField(value = authorizedBy, onValueChange = onAuthorizedByChange, label = "¿Quién la autorizó?", keyboard = KeyboardType.Text)
    if (known.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            known.forEach { name ->
                PillChip(name, selected = name == authorizedBy, onClick = { onAuthorizedByChange(name) })
            }
        }
    }
    FilledField(value = note, onValueChange = onNoteChange, label = "Nota (opcional)", keyboard = KeyboardType.Text)
}

/** Se muestra al salir después del margen: decide si esas horas se pagan como extra. */
@Composable
fun ExtraAuthorizationSheet(
    shift: Shift,
    exit: LocalDateTime,
    known: List<String>,
    onDismiss: () -> Unit,
    onDecide: (authorized: Boolean, authorizedBy: String?, note: String?) -> Unit,
) {
    var authorizedBy by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val extra = Duration.between(shift.scheduledEnd, exit).toMinutes()

    TurnosSheet(onDismiss) {
        Text("Saliste ${hours(extra)} después", style = MaterialTheme.typography.titleLarge)
        Text(
            "¿Te autorizaron estas horas extra?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AuthorizationFields(authorizedBy, { authorizedBy = it }, note, { note = it }, known)
        PrimaryAction("Sí, fueron autorizadas", onClick = { onDecide(true, authorizedBy, note) })
        TextButton(onClick = { onDecide(false, null, null) }, Modifier.fillMaxWidth()) {
            Text("No, pagar hasta las ${shift.scheduledEnd.timeLabel()}")
        }
    }
}
