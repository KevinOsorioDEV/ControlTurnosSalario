package com.kmanu.turnos.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.kmanu.turnos.ui.theme.Radius
import com.kmanu.turnos.ui.theme.Sizes
import com.kmanu.turnos.ui.theme.Spacing
import com.kmanu.turnos.ui.theme.StatusStyle
import com.kmanu.turnos.ui.theme.TurnosTheme

/** Tarjeta neutra con el relleno estándar. */
@Composable
fun TurnosCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.card), color = TurnosTheme.colors.card) {
        Column(Modifier.padding(Spacing.md), content = content)
    }
}

/** Botón principal de una pantalla o tarjeta; todos tienen la misma altura y esquinas. */
@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(Sizes.action),
        shape = RoundedCornerShape(Radius.control),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}

/** Etiqueta sólida que resume una característica del turno ("De noche", "1 h extra"). */
@Composable
fun StatusPill(text: String, style: StatusStyle, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(Radius.pill), color = style.container, contentColor = style.content) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        )
    }
}
