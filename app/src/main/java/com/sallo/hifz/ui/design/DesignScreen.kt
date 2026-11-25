@file:OptIn(ExperimentalMaterial3Api::class)

package com.sallo.hifz.ui.design

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import com.sallo.hifz.data.Prefs
import com.sallo.hifz.design.HeroStyle
import com.sallo.hifz.design.Palettes
import com.sallo.hifz.design.Tones
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sallo.hifz.HifzApp
import com.sallo.hifz.data.isDark
import com.sallo.hifz.design.AppDesign
import com.sallo.hifz.design.AppFont
import com.sallo.hifz.design.PhoneDesigns
import com.sallo.hifz.design.WidgetLook
import com.sallo.hifz.ui.components.BackButton
import com.sallo.hifz.ui.components.Hint
import com.sallo.hifz.ui.editor.Choices
import com.sallo.hifz.ui.theme.HifzTheme
import com.sallo.hifz.ui.theme.LocalDesign
import com.sallo.hifz.ui.theme.fontFamily
import com.sallo.hifz.ui.theme.heroBackground
import com.sallo.hifz.widget.WidgetKind
import com.sallo.hifz.widget.WidgetRenderer
import com.sallo.hifz.widget.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** «التصاميم والودجت»: the app's design and font, and the home-screen widgets with their look. */
@Composable
fun DesignScreen(app: HifzApp, onBack: () -> Unit) {
    val prefs by app.prefs.values.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val dark = prefs.theme.isDark(isSystemInDarkTheme())
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editingColors by remember { mutableStateOf(false) }

    if (editingColors) CustomColorsSheet(app, prefs, dark) { editingColors = false }

    Scaffold(
        topBar = { TopAppBar(title = { Text("التصاميم والودجت") }, navigationIcon = { BackButton(onBack) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Heading("التصميم")
            Hint("يغيّر ألوان التطبيق كله وشكل البطاقة الرئيسية، بالفاتح والداكن.")
            PhoneDesigns.offered.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { design ->
                        val palette = remember(design, dark, prefs.customPrimary, prefs.customAccent, prefs.customHero) {
                            PhoneDesigns.palette(context, design, dark, prefs)
                        }
                        DesignTile(
                            design = design,
                            selected = prefs.design == design,
                            preview = { HifzTheme(palette, prefs.font) { MiniScreen() } },
                            modifier = Modifier.weight(1f),
                        ) {
                            app.prefs.update { it.copy(design = design) }
                            Widgets.refresh(app)
                            if (design == AppDesign.CUSTOM) editingColors = true
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            if (prefs.design == AppDesign.CUSTOM) {
                FilledTonalButton(onClick = { editingColors = true }, modifier = Modifier.fillMaxWidth()) { Text("عدّل ألوانك") }
            }

            Heading("الخط")
            AppFont.entries.forEach { font ->
                FontRow(font, prefs.font == font) { app.prefs.update { it.copy(font = font) } }
            }

            Heading("الودجت")
            Hint("ضعه على الشاشة الرئيسية من هنا، أو اضغط مطوّلاً على الشاشة الرئيسية ثم «الأدوات» ثم «جدول الحفظ».")
            Choices(
                options = WidgetLook.entries.map { it.label },
                selected = prefs.widgetLook.ordinal,
                onSelect = { i ->
                    app.prefs.update { it.copy(widgetLook = WidgetLook.entries[i]) }
                    Widgets.refresh(app)
                },
            )
            Hint(prefs.widgetLook.hint)
            val canPin = remember { Widgets.canPin(context) }
            WidgetKind.entries.forEach { kind ->
                WidgetCard(app, kind, key = prefs, canPin = canPin) {
                    if (!Widgets.pin(context, kind)) {
                        scope.launch { snackbar.showSnackbar("أضفه من الشاشة الرئيسية: اضغط مطوّلاً ثم «الأدوات»") }
                    }
                }
            }
        }
    }
}

private val PRIMARY_SWATCHES = listOf(
    0xFF1E6B52, 0xFF2E7D32, 0xFF5E6B3A, 0xFF00677D, 0xFF006D77, 0xFF2F5D8A,
    0xFF1565C0, 0xFF1F3A5F, 0xFF3D4C5C, 0xFF37474F, 0xFF5B3A8C, 0xFF4A148C,
    0xFF7B1E3A, 0xFFAD1457, 0xFFB5462F, 0xFF8A5A2B, 0xFF6D4C41, 0xFF795548,
).map { it.toInt() }

private val ACCENT_SWATCHES = listOf(
    0xFFE8C15A, 0xFFF4D35E, 0xFFFFCC80, 0xFFF2A65A, 0xFFE76F51, 0xFFF48FB1,
    0xFFCE93D8, 0xFF90CAF9, 0xFF80DEEA, 0xFF8BD6B6, 0xFFA5D6A7, 0xFFB0BEC5,
).map { it.toInt() }

/** The swatches «ألوانك» offers; [DesignTest] checks every pair reads well. */
internal val CUSTOM_SWATCHES: Pair<List<Int>, List<Int>> get() = PRIMARY_SWATCHES to ACCENT_SWATCHES

/** «ألوانك»: the main color, the accent and the hero card, with the design shown as they change. */
@Composable
private fun CustomColorsSheet(app: HifzApp, prefs: Prefs, dark: Boolean, onDismiss: () -> Unit) {
    val palette = remember(prefs.customPrimary, prefs.customAccent, prefs.customHero, dark) {
        Palettes.build(Palettes.customSeeds(prefs.customPrimary, prefs.customAccent, prefs.customHero), dark)
    }
    fun change(transform: (Prefs) -> Prefs) {
        app.prefs.update { transform(it).copy(design = AppDesign.CUSTOM) }
        Widgets.refresh(app)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("ألوانك", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Box(Modifier.fillMaxWidth(0.6f).align(Alignment.CenterHorizontally)) { HifzTheme(palette, prefs.font) { MiniScreen() } }
            Text("اللون الأساسي", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Swatches(PRIMARY_SWATCHES, prefs.customPrimary) { c -> change { it.copy(customPrimary = c) } }
            Text("لون اللمسة", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Swatches(ACCENT_SWATCHES, prefs.customAccent) { c -> change { it.copy(customAccent = c) } }
            Text("البطاقة الرئيسية", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Choices(
                options = listOf("تدرّج", "زخرفة", "إطار", "سادة"),
                selected = prefs.customHero.ordinal,
                onSelect = { i -> change { it.copy(customHero = HeroStyle.entries[i]) } },
            )
            Hint("لو احتاج لون يتعدّل شوي عشان يبقى النص واضح في الفاتح والداكن.")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Swatches(colors: List<Int>, selected: Int, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { c ->
            val on = c == selected
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable { onPick(c) },
                contentAlignment = Alignment.Center,
            ) {
                if (on) Icon(Icons.Default.Check, contentDescription = "مختار", tint = if (Tones.lightness(c) > 60) Color.Black else Color.White)
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
}

/** A design to choose: a small screen in its colors, its name, and what it is. */
@Composable
private fun DesignTile(design: AppDesign, selected: Boolean, preview: @Composable () -> Unit, modifier: Modifier, onClick: () -> Unit) {
    val ring = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) ring else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        preview()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(design.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (selected) Icon(Icons.Default.Check, contentDescription = "المختار", tint = ring, modifier = Modifier.size(18.dp))
        }
        Text(design.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The table screen in small: its hero card, two items, and a button, in the design around it. */
@Composable
private fun MiniScreen() {
    val design = LocalDesign.current
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.background)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .heroBackground(design, 12.dp)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                "استمر",
                color = design.onHero,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Canvas(Modifier.size(28.dp).align(Alignment.CenterEnd)) {
                val stroke = 4.dp.toPx()
                val box = Size(size.width - stroke, size.height - stroke)
                drawArc(design.onHero.copy(alpha = 0.22f), 0f, 360f, false, Offset(stroke / 2, stroke / 2), box, style = Stroke(stroke))
                drawArc(design.accent, -90f, 130f, false, Offset(stroke / 2, stroke / 2), box, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("البقرة", colors.primaryContainer, colors.onPrimaryContainer, Modifier.weight(1f))
            Chip("اليوم", design.accent.copy(alpha = 0.18f), colors.onSurface, Modifier.weight(1f))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.primary),
            contentAlignment = Alignment.Center,
        ) { Text("تم", color = colors.onPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun Chip(text: String, background: Color, color: Color, modifier: Modifier) {
    Box(
        modifier
            .height(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = color, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
}

/** A font to choose, its name and a sample written in it. */
@Composable
private fun FontRow(font: AppFont, selected: Boolean, onClick: () -> Unit) {
    val family = fontFamily(font) ?: FontFamily.Default
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(font.label, style = MaterialTheme.typography.titleMedium.copy(fontFamily = family), fontWeight = FontWeight.Bold)
            Text("درس اليوم: البقرة 1-10، والمراجعة: الفاتحة", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = family))
            Text(font.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A kind of widget: its name, how it looks now with your plans, and a button to place it. */
@Composable
private fun WidgetCard(app: HifzApp, kind: WidgetKind, key: Any, canPin: Boolean, onPin: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                Text(kind.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(kind.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            WidgetPreview(app, kind, key)
            if (canPin) {
                FilledTonalButton(onClick = onPin, modifier = Modifier.fillMaxWidth()) { Text("أضف للشاشة الرئيسية") }
            }
        }
    }
}

/** The widget drawn exactly as on the home screen, at about its size there. */
@Composable
private fun ColumnScope.WidgetPreview(app: HifzApp, kind: WidgetKind, key: Any) {
    val views by produceState<RemoteViews?>(null, kind, key) {
        value = withContext(Dispatchers.IO) { runCatching { WidgetRenderer.build(app, kind, 0) }.getOrNull() }
    }
    val (width, height) = previewSize(kind)
    Box(
        Modifier
            .align(Alignment.CenterHorizontally)
            .then(if (width == null) Modifier.fillMaxWidth() else Modifier.width(width))
            .height(height)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        val current = views
        if (current == null) {
            Text("…", textAlign = TextAlign.Center)
        } else {
            AndroidView(
                factory = { FrameLayout(it) },
                update = { frame ->
                    frame.removeAllViews()
                    frame.addView(current.apply(frame.context, frame))
                },
                modifier = Modifier.fillMaxSize().padding(6.dp),
            )
        }
    }
}

/** About the widget's size on a phone's home screen; null width fills the card. */
private fun previewSize(kind: WidgetKind): Pair<Dp?, Dp> = when (kind) {
    WidgetKind.LIST -> null to 190.dp
    WidgetKind.COMPACT -> 172.dp to 172.dp
    WidgetKind.RING -> 160.dp to 160.dp
    WidgetKind.WEEK -> null to 100.dp
    WidgetKind.BOARD -> null to 250.dp
}
