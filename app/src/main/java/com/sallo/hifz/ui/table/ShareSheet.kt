@file:OptIn(ExperimentalMaterial3Api::class)

package com.sallo.hifz.ui.table

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.sallo.hifz.common.Plurals
import com.sallo.hifz.dates.DateText
import com.sallo.hifz.dates.Hijri
import com.sallo.hifz.ui.components.Hint
import com.sallo.hifz.ui.theme.LocalDesign
import com.sallo.hifz.ui.theme.heroBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** «شارك تقدمك»: the plan's progress as a picture in the app's design, sent with a line of text. */
@Composable
fun ShareSheet(state: TableState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val plan = state.plan ?: return
    val stats = state.stats ?: return
    val settings = state.settings ?: return
    val today = remember { LocalDate.now() }
    val week = state.facts?.days.orEmpty().filter { it.done && it.date <= today && it.date > today.minusDays(7) }.map { it.date }.distinct().size
    val last = state.rows.lastOrNull { it.mainDone }?.items?.firstOrNull { !it.empty && !it.offDay }?.text
    val percent = Plurals.percent(stats.progress)
    val text = buildList {
        add("«${plan.name}»: أنجزت ${stats.done} من ${Plurals.sessions(stats.total)} ($percent)")
        if (settings.hasDates && stats.currentStreak > 1) add("و${stats.currentStreak} متتالية")
    }.joinToString("، ") + (last?.let { ". آخر درس: $it." } ?: ".")

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("شارك تقدمك", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            Box(
                Modifier.drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                },
            ) {
                ProgressCard(plan.name, state, week, last, today)
            }
            Hint(text)
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        val bitmap = layer.toImageBitmap().asAndroidBitmap()
                        share(context, bitmap, text)
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("مشاركة") }
        }
    }
}

/** The picture: the hero card's look with the plan's numbers. */
@Composable
private fun ProgressCard(name: String, state: TableState, week: Int, last: String?, today: LocalDate) {
    val stats = state.stats ?: return
    val settings = state.settings ?: return
    val design = LocalDesign.current
    Column(
        Modifier.width(320.dp).heroBackground(design, 28.dp).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("جدول الحفظ", style = MaterialTheme.typography.labelLarge, color = design.onHero.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
            Text(
                "${DateText.dayAndMonth(today)} · ${Hijri.of(today).long}",
                style = MaterialTheme.typography.labelMedium,
                color = design.onHero.copy(alpha = 0.75f),
            )
        }
        Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = design.onHero)
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(stats.progress, stats.progress, 92.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(motivation(stats), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = design.onHero)
                Text("أنجزت ${stats.done} من ${Plurals.sessions(stats.total)}", style = MaterialTheme.typography.bodyMedium, color = design.onHero.copy(alpha = 0.85f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (settings.hasDates) HeroStat(stats.currentStreak.toString(), "متتالية", Modifier.weight(1f))
            if (settings.includeNew) {
                HeroStat(stats.ayahs.toString(), "آية", Modifier.weight(1f))
            } else {
                HeroStat(Plurals.number(Math.round(stats.pages * 10.0) / 10.0), "وجه", Modifier.weight(1f))
            }
            HeroStat(week.toString(), "هذا الأسبوع", Modifier.weight(1f))
        }
        last?.let { Text("آخر درس: $it", style = MaterialTheme.typography.bodyMedium, color = design.onHero) }
    }
}

/** Saves [bitmap] where the share sheet can read it, and opens it with [text]. */
private suspend fun share(context: Context, bitmap: Bitmap, text: String) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        File(dir, "progress.png").also { f -> f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, text)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // So the share sheet itself can show the picture.
    send.clipData = ClipData.newRawUri(null, uri)
    context.startActivity(Intent.createChooser(send, "شارك تقدمك"))
}
