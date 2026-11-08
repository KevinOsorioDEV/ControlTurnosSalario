package com.kmanu.turnos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import com.kmanu.turnos.ui.theme.Motion
import com.kmanu.turnos.ui.theme.Radius
import com.kmanu.turnos.ui.theme.Sizes
import com.kmanu.turnos.ui.theme.Spacing
import com.kmanu.turnos.ui.theme.StatusStyle
import com.kmanu.turnos.ui.theme.TurnosTheme
import java.time.LocalTime
import kotlin.math.roundToInt

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

/** Campo de texto relleno, sin borde; el borde solo aparece al escribir o si hay un error. */
@Composable
fun FilledField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Number,
    prefix: String? = null,
    transformation: VisualTransformation = VisualTransformation.None,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(Radius.control)
    val scheme = MaterialTheme.colorScheme
    val outline = when {
        isError -> scheme.error
        focused -> scheme.primary
        else -> Color.Transparent
    }
    // El cursor empieza al final, listo para borrar o seguir escribiendo.
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    TextField(
        value = field,
        onValueChange = {
            field = it
            if (it.text != value) onValueChange(it.text)
        },
        modifier = modifier.fillMaxWidth().border(Sizes.focusBorder, outline, shape),
        label = { Text(label) },
        prefix = prefix?.let { text -> { Text(text) } },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = transformation,
        interactionSource = interaction,
        shape = shape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = scheme.surfaceContainer,
            unfocusedContainerColor = scheme.surfaceContainer,
            errorContainerColor = scheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
        ),
    )
}

/** Muestra `2800000` como `2.800.000` sin alterar el valor guardado. */
object ThousandsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val grouped = buildString {
            digits.forEachIndexed { index, char ->
                if (index > 0 && (digits.length - index) % 3 == 0) append('.')
                append(char)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                offset + (1 until offset).count { (digits.length - it) % 3 == 0 }

            override fun transformedToOriginal(offset: Int): Int =
                (0..digits.length).first { originalToTransformed(it) >= offset }
        }
        return TransformedText(AnnotatedString(grouped), mapping)
    }
}

/** Opción en forma de píldora. */
@Composable
fun PillChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = Sizes.chip).semantics { this.selected = selected },
        shape = CircleShape,
        color = if (selected) scheme.primary else scheme.surfaceContainerHigh,
        contentColor = if (selected) scheme.onPrimary else scheme.onSurface,
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            icon?.let { Icon(it, contentDescription = null, Modifier.size(Sizes.chipIcon)) }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Selector de opciones excluyentes con un fondo que se desliza a la opción elegida. */
@Composable
fun PillTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val position by animateFloatAsState(selected.toFloat(), Motion.standard(), label = "selector")
    Surface(modifier, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        BoxWithConstraints(Modifier.padding(Spacing.xs)) {
            val itemWidth = maxWidth / options.size
            Box(Modifier.matchParentSize()) {
                Surface(
                    modifier = Modifier
                        .offset { IntOffset((itemWidth.toPx() * position).roundToInt(), 0) }
                        .width(itemWidth)
                        .fillMaxHeight(),
                    shape = CircleShape,
                    color = TurnosTheme.colors.card,
                ) {}
            }
            Row(Modifier.fillMaxWidth().selectableGroup()) {
                options.forEachIndexed { index, option ->
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = Sizes.chip)
                            .clip(CircleShape)
                            .selectable(selected = index == selected, role = Role.Tab) { onSelect(index) },
                        contentAlignment = Alignment.Center,
                    ) { Text(option, style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
    }
}

/** Contador con botones − y +; [center] muestra o permite escribir el valor. */
@Composable
fun Stepper(
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseLabel: String,
    increaseLabel: String,
    modifier: Modifier = Modifier,
    center: @Composable () -> Unit,
) {
    val tint = MaterialTheme.colorScheme.primary
    Surface(modifier, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDecrease) { Icon(Icons.Rounded.Remove, decreaseLabel, tint = tint) }
            center()
            IconButton(onClick = onIncrease) { Icon(Icons.Rounded.Add, increaseLabel, tint = tint) }
        }
    }
}

/** Tarjeta con encabezado que despliega su contenido. Solo el encabezado responde al toque. */
@Composable
fun AccordionCard(
    title: String,
    open: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val rotation by animateFloatAsState(if (open) 180f else 0f, Motion.standard(), label = "flecha")
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.card), color = TurnosTheme.colors.card) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onToggle).padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (summary != null) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = Spacing.sm),
                    )
                }
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.rotate(rotation))
            }
            AnimatedVisibility(
                visible = open,
                enter = expandVertically(Motion.standard()) + fadeIn(Motion.standard()),
                exit = shrinkVertically(Motion.standard()) + fadeOut(Motion.standard(Motion.SHORT)),
            ) {
                Column(
                    Modifier.padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    content = content,
                )
            }
        }
    }
}

/** Atenúa el borde superior de una zona con scroll para que el contenido no se corte en seco. */
fun Modifier.topFade(): Modifier = graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val height = Spacing.sm.toPx()
        drawRect(
            brush = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = height),
            size = Size(size.width, height),
            blendMode = BlendMode.DstIn,
        )
    }

/** Hoja inferior con el estilo de la app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurnosSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = Radius.hero, topEnd = Radius.hero),
        containerColor = TurnosTheme.colors.card,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.ms),
            content = content,
        )
    }
}

private const val GRID_COLUMNS = 6

@Composable
private fun SelectionGrid(options: List<Int>, selected: Int, onSelect: (Int) -> Unit, label: (Int) -> String) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        options.chunked(GRID_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                row.forEach { option ->
                    Surface(
                        onClick = { onSelect(option) },
                        modifier = Modifier.weight(1f).height(Sizes.gridCell).semantics { this.selected = option == selected },
                        shape = RoundedCornerShape(Radius.control),
                        color = if (option == selected) scheme.primary else scheme.surfaceContainer,
                        contentColor = if (option == selected) scheme.onPrimary else scheme.onSurface,
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text(label(option), style = MaterialTheme.typography.titleSmall) }
                    }
                }
                repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Elegir una hora con dos toques: sin reloj circular ni teclado. */
@Composable
fun TimeGridSheet(title: String, initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    var isPm by remember { mutableStateOf(initial.hour >= 12) }
    var hour by remember { mutableIntStateOf((initial.hour % 12).let { if (it == 0) 12 else it }) }
    var minute by remember { mutableIntStateOf(initial.minute) }
    val minutes = remember { ((0..55 step 5).toList() + initial.minute).distinct().sorted() }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    TurnosSheet(onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        PillTabs(listOf("AM", "PM"), selected = if (isPm) 1 else 0, onSelect = { isPm = it == 1 })
        Text("Hora", style = MaterialTheme.typography.labelMedium, color = muted)
        SelectionGrid((1..12).toList(), hour, { hour = it }) { it.toString() }
        Text("Minutos", style = MaterialTheme.typography.labelMedium, color = muted)
        SelectionGrid(minutes, minute, { minute = it }) { it.toString().padStart(2, '0') }
        PrimaryAction("Listo", onClick = { onConfirm(LocalTime.of(hour % 12 + if (isPm) 12 else 0, minute)) })
    }
}
