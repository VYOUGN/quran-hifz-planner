@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.sallo.hifz.ui.table

import com.sallo.hifz.data.RepeatOptions
import com.sallo.hifz.data.RepeatWay
import androidx.compose.material3.Surface
import com.sallo.hifz.ui.components.OptionRow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import com.sallo.hifz.common.Plurals
import com.sallo.hifz.plan.Overviews
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sallo.hifz.data.Item
import com.sallo.hifz.data.SessionEntity
import com.sallo.hifz.dates.DateText
import com.sallo.hifz.dates.Hijri
import com.sallo.hifz.plan.GradeLevel
import com.sallo.hifz.plan.GradeScale
import com.sallo.hifz.plan.PlanSettings
import com.sallo.hifz.plan.PlanStats
import com.sallo.hifz.quran.RangeFormatter
import com.sallo.hifz.ui.components.Hint
import com.sallo.hifz.ui.components.PickDateDialog
import com.sallo.hifz.ui.components.SurahButton
import com.sallo.hifz.ui.components.SurahPickerDialog
import com.sallo.hifz.ui.theme.LocalTracks
import java.time.LocalDate
import java.util.Locale

/** Text the user typed, or null when it is empty or the same as the generated text. */
private fun override(text: String, generated: String): String? =
    text.trim().takeIf { it.isNotEmpty() && it != generated }

@Composable
fun RowSheet(
    row: RowUi,
    scale: GradeScale,
    showHijri: Boolean,
    /** The plan's tracks have their own days, so postponing plans the following days again. */
    separateDays: Boolean,
    onDismiss: () -> Unit,
    onSave: (SessionEntity) -> Unit,
    onPostpone: (LocalDate) -> Unit,
    /** «إعادة الدرس» for this row, or null when it is not a session to repeat. */
    repeat: RepeatOptions? = null,
    /** The row's main item: the lesson, or the major review in a plan of reviews only. */
    mainItem: Item = Item.NEW,
    onRepeat: (SessionEntity, RepeatWay) -> Unit = { _, _ -> },
) {
    val s = row.session
    var texts by remember { mutableStateOf(row.items.associate { it.item to if (it.empty) "" else it.text }) }
    var done by remember { mutableStateOf(Item.entries.associateWith { s.done(it) }) }
    var grades by remember { mutableStateOf(Item.entries.associateWith { s.score(it) }) }
    var note by remember { mutableStateOf(s.note.orEmpty()) }
    var counts by remember { mutableStateOf(Item.entries.associateWith { s.mistakes(it) to s.alerts(it) }) }
    var postponing by remember { mutableStateOf(false) }
    var pickingSurahs by remember { mutableStateOf(false) }
    var repeating by remember { mutableStateOf(false) }

    fun result(): SessionEntity {
        var out = s.copy(note = note.trim().ifEmpty { null })
        for (item in row.items.filterNot { it.offDay }) {
            val text = texts[item.item].orEmpty()
            out = when (item.item) {
                Item.NEW -> out.copy(newText = override(text, item.generated), newDone = done.getValue(Item.NEW), newGrade = grades.getValue(Item.NEW))
                Item.MINOR -> out.copy(minorText = override(text, item.generated), minorDone = done.getValue(Item.MINOR), minorGrade = grades.getValue(Item.MINOR))
                Item.MAJOR -> out.copy(
                    majorText = if (item.generated.isNotEmpty()) override(text, item.generated) else text.trim().ifEmpty { null },
                    majorDone = done.getValue(Item.MAJOR),
                    majorGrade = grades.getValue(Item.MAJOR),
                )
                Item.PREP -> out.copy(prepText = override(text, item.generated), prepDone = done.getValue(Item.PREP), prepGrade = grades.getValue(Item.PREP))
            }
            val (mistakes, alerts) = counts.getValue(item.item)
            out = out.withCounts(item.item, mistakes, alerts)
        }
        return out
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(
                    if (row.hasDate) DateText.long(row.date) else row.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (showHijri && row.hasDate) Hint(Hijri.of(row.date).long)
            }
            row.items.filterNot { it.offDay }.forEach { item ->
                ItemEditor(
                    item = item.item,
                    text = texts[item.item].orEmpty(),
                    onText = { texts = texts + (item.item to it) },
                    done = done.getValue(item.item),
                    onDone = { done = done + (item.item to it) },
                    scale = scale,
                    score = grades.getValue(item.item),
                    onGrade = { level ->
                        grades = grades + (item.item to (level?.score ?: 0))
                        // A passing grade ticks the item; one to repeat leaves it open.
                        if (level != null) done = done + (item.item to level.passed)
                    },
                    onPickSurahs = if (item.item == Item.MAJOR) ({ pickingSurahs = true }) else null,
                    counts = if (item.item == Item.PREP) null else counts.getValue(item.item),
                    onCounts = { counts = counts + (item.item to it) },
                )
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("ملاحظة") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (repeat != null) {
                val what = if (mainItem == Item.NEW) "الدرس" else "المراجعة"
                val score = grades.getValue(mainItem)
                val struggled = (score in 1 until GradeLevel.PASS) || (counts.getValue(mainItem).first ?: 0) >= MANY_MISTAKES
                val possible = repeat.blocked == null || repeat.extraDay != null
                when {
                    !possible -> Hint("إعادة $what: ${repeat.blocked}")
                    struggled -> Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("أخطاؤك كثيرة في هذا $what. تبغى تعيده؟", fontWeight = FontWeight.Bold)
                            Button(onClick = { repeating = true }, modifier = Modifier.fillMaxWidth()) { Text("أعد $what") }
                        }
                    }
                    else -> OutlinedButton(onClick = { repeating = true }, modifier = Modifier.fillMaxWidth()) { Text("أعد $what") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (row.hasDate) {
                    OutlinedButton(onClick = { postponing = true }, modifier = Modifier.weight(1f)) { Text("تأجيل") }
                }
                Button(onClick = { onSave(result()) }, modifier = Modifier.weight(1f)) { Text("حفظ") }
            }
            if (row.hasDate) {
                Hint(
                    if (separateDays) {
                        "التأجيل يرتّب هذا اليوم واللي بعده من جديد ابتداءً من التاريخ اللي تختاره، وكل مسار على أيامه."
                    } else {
                        "التأجيل ينقل هذي الجلسة واللي بعدها: هذي للتاريخ اللي تختاره، والباقي على أيام الجدول."
                    },
                )
            }
        }
    }

    if (postponing) {
        PickDateDialog(
            initial = maxOf(row.date.plusDays(1), LocalDate.now()),
            onDismiss = { postponing = false },
            onPick = {
                postponing = false
                onPostpone(it)
            },
        )
    }
    if (repeating && repeat != null) {
        RepeatDialog(
            options = repeat,
            what = if (mainItem == Item.NEW) "الدرس" else "المراجعة",
            onDismiss = { repeating = false },
            onPick = { way ->
                repeating = false
                onRepeat(result(), way)
            },
        )
    }
    if (pickingSurahs) {
        SurahSpanDialog(
            onDismiss = { pickingSurahs = false },
            onPick = { a, b ->
                texts = texts + (Item.MAJOR to RangeFormatter.surahSpan(a, b))
                pickingSurahs = false
            },
        )
    }
}

@Composable
private fun ItemEditor(
    item: Item,
    text: String,
    onText: (String) -> Unit,
    done: Boolean,
    onDone: (Boolean) -> Unit,
    scale: GradeScale,
    score: Int,
    onGrade: (GradeLevel?) -> Unit,
    onPickSurahs: (() -> Unit)?,
    /** Mistakes and alerts («عدد الأخطاء»); null for preparation, which is not recited. */
    counts: Pair<Int?, Int?>?,
    onCounts: (Pair<Int?, Int?>) -> Unit,
) {
    val c = LocalTracks.current.of(item)
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.5.dp, c.main.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.label, style = MaterialTheme.typography.titleSmall, color = c.main, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("تم", style = MaterialTheme.typography.bodyMedium)
            Checkbox(checked = done, onCheckedChange = onDone)
        }
        OutlinedTextField(
            value = text,
            onValueChange = onText,
            placeholder = if (item == Item.MAJOR) ({ Text("مثلاً: الرحمن - الواقعة") }) else null,
            modifier = Modifier.fillMaxWidth(),
        )
        if (onPickSurahs != null) TextButton(onClick = onPickSurahs) { Text("اختيار من السور") }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val selected = scale.levelOf(score)
            scale.levels.forEach { level ->
                FilterChip(
                    selected = level == selected,
                    onClick = { onGrade(if (level == selected) null else level) },
                    label = { Text(level.label) },
                )
            }
        }
        if (counts != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CountStepper("أخطاء", counts.first, Modifier.weight(1f)) { onCounts(it to counts.second) }
                CountStepper("تنبيهات", counts.second, Modifier.weight(1f)) { onCounts(counts.first to it) }
            }
        }
    }
}

/** «أخطاء −  2  +»: «−» at nothing records a zero, so «no mistakes» can be said too. */
@Composable
private fun CountStepper(label: String, value: Int?, modifier: Modifier, onChange: (Int?) -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange(((value ?: 0) - 1).coerceAtLeast(0)) }, enabled = (value ?: 0) > 0, modifier = Modifier.size(36.dp)) {
            Text("−", style = MaterialTheme.typography.titleMedium)
        }
        // Not counted yet: a faded 0, so it does not read as a second minus.
        Text(
            (value ?: 0).toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 20.dp),
            color = when {
                value == null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                value > 0 -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        IconButton(onClick = { onChange((value ?: 0) + 1) }, modifier = Modifier.size(36.dp)) {
            Text("+", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Mistakes on the main item from which «إعادة الدرس» is offered up front. */
private const val MANY_MISTAKES = 5

/** «أعد الدرس»: in place of the next session, or on a free day before it. */
@Composable
private fun RepeatDialog(options: RepeatOptions, what: String, onDismiss: () -> Unit, onPick: (RepeatWay) -> Unit) {
    var way by remember { mutableStateOf(if (options.blocked == null) RepeatWay.NEXT_SESSION else RepeatWay.EXTRA_DAY) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("أعد $what") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Hint("هذي الجلسة تنحسب منجزة وتبقى أخطاؤها، والإعادة جلسة جديدة.")
                if (options.blocked == null) {
                    val day = options.nextDay?.let { " ${DateText.withWeekday(it)}" }.orEmpty()
                    val finish = if (options.finishBefore != null && options.finishAfter != null) {
                        " الختم يصير ${DateText.long(options.finishAfter)} بدل ${DateText.long(options.finishBefore)}."
                    } else {
                        ""
                    }
                    OptionRow(
                        "في الجلسة الجاية$day",
                        "بدل الدرس الجاي، وكل درس بعده ياخذ مكان اللي بعده.$finish",
                        way == RepeatWay.NEXT_SESSION,
                    ) { way = RepeatWay.NEXT_SESSION }
                } else {
                    Hint("في الجلسة الجاية: ${options.blocked}")
                }
                options.extraDay?.let { day ->
                    OptionRow(
                        "بجلسة زيادة ${DateText.withWeekday(day)}",
                        "في يوم فاضي قبل الجلسة الجاية، وجدولك ما يتغيّر.",
                        way == RepeatWay.EXTRA_DAY,
                    ) { way = RepeatWay.EXTRA_DAY }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(way) }) { Text("أعده") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun SurahSpanDialog(onDismiss: () -> Unit, onPick: (Int, Int) -> Unit) {
    var from by remember { mutableStateOf(1) }
    var to by remember { mutableStateOf(1) }
    var picking by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("المراجعة الكبرى") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("من سورة")
                SurahButton(from, Modifier.fillMaxWidth()) { picking = "from" }
                Text("إلى سورة")
                SurahButton(to, Modifier.fillMaxWidth()) { picking = "to" }
                Hint(RangeFormatter.surahSpan(from, to))
            }
        },
        confirmButton = { TextButton(onClick = { onPick(from, to) }) { Text("تم") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
    picking?.let { which ->
        SurahPickerDialog(
            title = if (which == "from") "من سورة" else "إلى سورة",
            onDismiss = { picking = null },
            onPick = { n ->
                if (which == "from") {
                    from = n
                    if (to < n) to = n
                } else {
                    to = n
                }
                picking = null
            },
        )
    }
}

@Composable
fun StatsSheet(
    state: TableState,
    onCertificate: (Achievement) -> Unit,
    onOpenRow: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val stats = state.stats ?: return
    val settings = state.settings ?: return
    val averageGrade = state.averageGrade
    val word = if (settings.includeNew) "المحفوظة" else "المراجعة"
    val today = remember { LocalDate.now() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Text("الإحصائيات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            StatLine("الجلسات المنجزة", "${stats.done} من ${stats.total}")
            if (settings.includeNew) StatLine("الآيات $word", stats.ayahs.toString())
            StatLine("الأوجه $word", String.format(Locale.US, "%.1f", stats.pages))
            StatLine("الحروف $word", String.format(Locale.US, "%,d", stats.letters))
            averageGrade?.let { StatLine(if (settings.includeNew) "متوسط تقييم الحفظ" else "متوسط تقييم المراجعة", it.label) }
            if (settings.hasDates) {
                StatLine("سلسلة الالتزام الحالية", stats.currentStreak.toString())
                StatLine("أطول سلسلة", stats.bestStreak.toString())
            }
            if (stats.isComplete) {
                StatLine("الختم", "اكتملت الخطة")
            } else {
                if (settings.hasDates) {
                    StatLine("الختم المتوقع حسب الجدول", stats.scheduledFinish?.let { DateText.long(it) } ?: "—")
                }
                StatLine("الختم المتوقع حسب سرعتك", stats.paceFinish?.let { DateText.long(it) } ?: "يظهر بعد 3 جلسات منجزة")
            }
            state.facts?.takeIf { it.calendar != null }?.let { facts ->
                SheetHeading("تقويم الالتزام")
                DaysCalendar(facts, today)
            }
            val achievements = achievementsOf(state)
            SheetHeading("الإنجازات")
            if (achievements.isEmpty()) {
                Hint("لما تكمل حفظ جزء كامل أو تكمل الخطة، تطلع هنا وتقدر تطبع شهادتها.")
            }
            achievements.forEach { a ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (a) {
                                Achievement.Whole -> if (settings.includeNew) "🎉 الخطة كاملة" else "🎉 المراجعة كاملة"
                                is Achievement.Juz -> "🌙 ${com.sallo.hifz.print.Certificate.juz(a.done.juz)}"
                            },
                            fontWeight = FontWeight.Bold,
                        )
                        val date = when (a) {
                            Achievement.Whole -> state.facts?.stats?.filter { it.done }?.maxOfOrNull { it.date }
                            is Achievement.Juz -> a.done.date
                        }
                        date?.let { Hint(DateText.long(it)) }
                    }
                    TextButton(onClick = { onCertificate(a) }) { Text("شهادة") }
                }
            }
            val mistakes = state.facts?.mistakes.orEmpty()
            if (mistakes.isNotEmpty()) {
                SheetHeading("الأخطاء")
                Overviews.average(mistakes)?.let { StatLine("متوسط الأخطاء في التسميع", String.format(Locale.US, "%.1f", it)) }
                StatLine("التسميعات المسجّلة", mistakes.sumOf { it.items }.toString())
                val top = Overviews.topMistakes(mistakes)
                if (top.isNotEmpty()) {
                    Text("أكثر الدروس أخطاءً", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                    top.forEach { m ->
                        val index = state.rows.indexOfFirst { it.session.idx == m.idx }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = index >= 0) { onOpenRow(index) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(m.lesson, fontWeight = FontWeight.Bold)
                                Hint(DateText.withWeekday(m.date))
                            }
                            Text(Plurals.mistakes(m.mistakes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Bold)
    }
    HorizontalDivider()
}
