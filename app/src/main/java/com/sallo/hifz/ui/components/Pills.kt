@file:OptIn(ExperimentalLayoutApi::class)

package com.sallo.hifz.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** Rounded, outlined chip used to build the plan sentence. */
@Composable
fun Pill(
    text: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    dropdown: Boolean = false,
    color: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
) {
    val active = enabled && onClick != null
    Surface(
        onClick = { onClick?.invoke() },
        enabled = active,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = if (enabled) 0.10f else 0.04f),
        border = BorderStroke(1.dp, color.copy(alpha = if (enabled) 0.55f else 0.2f)),
        modifier = modifier,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                color = if (enabled) color else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (dropdown) {
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** A pill that opens a menu of [options]. */
@Composable
fun <T> DropdownPill(
    text: String,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    color: Color = MaterialTheme.colorScheme.primary,
    selected: T? = null,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Pill(text, onClick = { open = true }, dropdown = true, color = color, enabled = enabled)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    trailingIcon = if (option == selected) ({ Icon(Icons.Default.Check, contentDescription = null) }) else null,
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/** «Label: pills…» row of the sentence builder. */
@Composable
fun LabeledRow(label: String, content: @Composable FlowRowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(78.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) { content() }
    }
}

/** Plain word between pills, centered on them. */
@Composable
fun FlowRowScope.Word(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.align(Alignment.CenterVertically))
}

private fun fieldText(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)

/** Number picker with + and − and a text field. */
@Composable
fun NumberDialog(
    title: String,
    value: Double,
    min: Double,
    max: Double,
    step: Double,
    onDismiss: () -> Unit,
    hint: ((Double) -> String)? = null,
    onConfirm: (Double) -> Unit,
) {
    var current by remember { mutableDoubleStateOf(value) }
    var text by remember { mutableStateOf(fieldText(value)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalIconButton(onClick = {
                        current = (current + step).coerceAtMost(max)
                        text = fieldText(current)
                    }) { Icon(Icons.Default.Add, contentDescription = "زيادة") }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { input ->
                            text = input.filter { it.isDigit() || it == '.' }.take(6)
                            text.toDoubleOrNull()?.let { if (it in min..max) current = it }
                        },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 22.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(120.dp),
                    )
                    FilledTonalIconButton(onClick = {
                        current = (current - step).coerceAtLeast(min)
                        text = fieldText(current)
                    }) { Text("−", style = MaterialTheme.typography.titleLarge) }
                }
                hint?.let {
                    Spacer(Modifier.padding(4.dp))
                    Text(it(current), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(current) }) { Text("تم") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

/** Grid of numbers, for juz and hizb. */
@Composable
fun NumberGridDialog(title: String, max: Int, selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyVerticalGrid(columns = GridCells.Fixed(5), modifier = Modifier.heightIn(max = 380.dp)) {
                items((1..max).toList()) { n ->
                    val chosen = n == selected
                    Box(
                        Modifier
                            .padding(4.dp)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { onPick(n) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$n",
                            color = if (chosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

/** Selectable option with a title and a description. */
@Composable
fun OptionRow(title: String, hint: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (hint.isNotBlank()) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}
