@file:OptIn(ExperimentalMaterial3Api::class)

package com.sallo.hifz.ui.table

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sallo.hifz.HifzApp
import com.sallo.hifz.dates.DateText
import com.sallo.hifz.findActivity
import com.sallo.hifz.plan.JuzDone
import com.sallo.hifz.print.Certificate
import com.sallo.hifz.print.TablePrinter
import com.sallo.hifz.quran.Quran
import com.sallo.hifz.ui.components.Hint
import com.sallo.hifz.ui.components.OptionRow
import com.sallo.hifz.ui.theme.LocalDesign
import java.time.LocalDate

/** What a certificate is for: the whole plan, or a juz memorized whole. */
sealed interface Achievement {
    data object Whole : Achievement

    data class Juz(val done: JuzDone) : Achievement
}

/** The achievements of a plan, plan first. */
fun achievementsOf(state: TableState): List<Achievement> = buildList {
    if (state.stats?.isComplete == true) add(Achievement.Whole)
    state.juzDone.forEach { add(Achievement.Juz(it)) }
}

/** «شهادة إتمام»: pick the achievement, write the name, and print it (or save it as PDF). */
@Composable
fun CertificateSheet(app: HifzApp, state: TableState, initial: Achievement?, onDismiss: () -> Unit) {
    val activity = LocalContext.current.findActivity()
    val design = LocalDesign.current
    val choices = remember(state) { achievementsOf(state) }
    var chosen by remember { mutableStateOf(initial ?: choices.firstOrNull()) }
    var name by remember { mutableStateOf(app.prefs.values.value.certificateName) }
    val plan = state.plan ?: return
    val settings = state.settings ?: return
    val stats = state.facts?.stats.orEmpty()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("شهادة إتمام", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (choices.isEmpty()) {
                Hint("أول شهادة تطلع لك لما تكمل حفظ جزء كامل أو تكمل الخطة.")
                return@Column
            }
            choices.forEach { a ->
                OptionRow(title(a, settings.includeNew), dateOf(a, stats)?.let { DateText.long(it) }.orEmpty(), chosen == a) { chosen = a }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("الاسم في الشهادة") },
                placeholder = { Text("اسمك") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val a = chosen ?: return@Button
                    app.prefs.update { it.copy(certificateName = name.trim()) }
                    val rows = when (a) {
                        Achievement.Whole -> stats.filter { it.done }
                        is Achievement.Juz -> Quran.juzRange(a.done.juz).let { r -> stats.filter { it.done && it.lesson.first <= r.last && it.lesson.last >= r.first } }
                    }
                    val from = rows.minOfOrNull { it.date } ?: LocalDate.now()
                    val to = rows.maxOfOrNull { it.date } ?: LocalDate.now()
                    val what = when (a) {
                        Achievement.Whole -> if (settings.includeNew) "حفظ ${settings.newPortion.range.describe()}" else "مراجعة ${settings.majorPortion.range.describe()}"
                        is Achievement.Juz -> "حفظ ${Certificate.juz(a.done.juz)}"
                    }
                    val html = Certificate.html(name, what, from, to, rows.size, design.accent.toArgbInt(), design.hero[1].toArgbInt(), LocalDate.now())
                    activity?.let { TablePrinter.print(it, "شهادة ${plan.name}", html, landscape = true) }
                },
                enabled = chosen != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("اطبع الشهادة") }
            Hint("من نافذة الطباعة تقدر تطبعها، أو تحفظها PDF وترسلها.")
        }
    }
}

private fun title(a: Achievement, memorizes: Boolean): String = when (a) {
    Achievement.Whole -> if (memorizes) "الخطة كاملة" else "المراجعة كاملة"
    is Achievement.Juz -> "حفظ ${Certificate.juz(a.done.juz)}"
}

private fun dateOf(a: Achievement, stats: List<com.sallo.hifz.plan.StatRow>): LocalDate? = when (a) {
    Achievement.Whole -> stats.filter { it.done }.maxOfOrNull { it.date }
    is Achievement.Juz -> a.done.date
}

private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)
