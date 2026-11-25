@file:OptIn(ExperimentalLayoutApi::class)

package com.sallo.hifz.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sallo.hifz.common.PlanText
import com.sallo.hifz.common.Plurals
import com.sallo.hifz.plan.MemUnit
import com.sallo.hifz.plan.PortionSpec
import com.sallo.hifz.plan.RangeSpec
import com.sallo.hifz.plan.RangeType
import com.sallo.hifz.plan.Rounding
import com.sallo.hifz.quran.Quran
import com.sallo.hifz.quran.RangeFormatter
import com.sallo.hifz.ui.components.DropdownPill
import com.sallo.hifz.ui.components.Hint
import com.sallo.hifz.ui.components.LabeledRow
import com.sallo.hifz.ui.components.NumberDialog
import com.sallo.hifz.ui.components.NumberGridDialog
import com.sallo.hifz.ui.components.OptionRow
import com.sallo.hifz.ui.components.Pill
import com.sallo.hifz.ui.components.SurahPickerDialog
import com.sallo.hifz.ui.components.Word

/**
 * «أنوي أن أحفظ [10] [آيات] من [سورة] [العصر] إلى [الناس]»: amount, unit, range and how sessions end.
 * [byDate]: the amount comes from the end date, so it is not editable.
 */
@Composable
fun PortionEditor(verb: String, portion: PortionSpec, byDate: Boolean, color: Color, onChange: (PortionSpec) -> Unit) {
    Text(
        PlanText.intent("أنوي أن $verb", portion, byDate),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = color,
    )
    AmountRow(portion, byDate, color, onChange)
    LabeledRow("المدى") {
        DropdownPill(
            text = portion.range.type.label,
            options = RangeType.entries,
            label = { it.label },
            selected = portion.range.type,
            onSelect = { onChange(portion.copy(range = portion.range.convertTo(it), resume = null)) },
            color = color,
        )
    }
    RangeValues(portion.range, color) { onChange(portion.copy(range = it, resume = null)) }
    if (portion.range.isBackward) Hint("الاتجاه: من آخر المصحف صعوداً")
    portion.resume?.let { r ->
        Text("يكمل من ${RangeFormatter.format(r..r)}", style = MaterialTheme.typography.bodySmall, color = color)
    }
    SessionEnds(portion, byDate, color, onChange)
}

/**
 * «من محفوظي»: how much to review per session, what was memorized before the plan ([prior] false:
 * nothing), and where sessions end. The rounds themselves follow the memorization.
 */
@Composable
fun MemorizedEditor(portion: PortionSpec, prior: Boolean, color: Color, onChange: (PortionSpec, Boolean) -> Unit) {
    Text(
        "أنوي أن أراجع ${Plurals.amount(portion.unit, portion.amount)} من محفوظي",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = color,
    )
    AmountRow(portion, byDate = false, color = color) { onChange(it, prior) }
    LabeledRow("محفوظ سابق") {
        DropdownPill<RangeType?>(
            text = if (prior) portion.range.type.label else NOTHING,
            options = listOf(null) + RangeType.entries,
            label = { it?.label ?: NOTHING },
            selected = if (prior) portion.range.type else null,
            onSelect = { t ->
                if (t == null) onChange(portion, false) else onChange(portion.copy(range = portion.range.convertTo(t), resume = null), true)
            },
            color = color,
        )
    }
    if (prior) RangeValues(portion.range, color) { onChange(portion.copy(range = it, resume = null), true) }
    Hint(
        if (prior) {
            "الدورة الأولى تراجع محفوظك السابق (${portion.range.describe()})، وبعدها كل دورة تبدأ من قبل المراجعة الصغرى وتشمل كل اللي حفظته."
        } else {
            "تبدأ المراجعة أول ما يصير عندك محفوظ قبل المراجعة الصغرى، وكل دورة تشمل كل اللي حفظته."
        },
    )
    SessionEnds(portion, byDate = false, color = color) { onChange(it, prior) }
}

private const val NOTHING = "لا شيء"

@Composable
private fun AmountRow(portion: PortionSpec, byDate: Boolean, color: Color, onChange: (PortionSpec) -> Unit) {
    var amountDialog by remember { mutableStateOf(false) }
    val isPage = portion.unit == MemUnit.PAGE
    LabeledRow("المقدار") {
        if (byDate) {
            Pill("حسب تاريخ الختم", onClick = null, color = color, enabled = false)
        } else {
            Pill(Plurals.number(portion.amount), onClick = { amountDialog = true }, color = color)
        }
        DropdownPill(
            text = portion.unit.label,
            options = MemUnit.entries.filter { it != MemUnit.PASSAGE || Quran.hasPassages },
            label = { it.label },
            selected = portion.unit,
            onSelect = { onChange(portion.withUnit(it)) },
            color = color,
        )
        Word("لكل جلسة")
    }
    if (portion.unit == MemUnit.PASSAGE) {
        Hint("المقاطع حسب تقسيم موسوعة التفسير في الدرر السنية، مثل المعارج: 1-7، 8-18، 19-35، 36-44. وكل سورة تبدأ من أولها.")
    }

    if (amountDialog) {
        NumberDialog(
            title = "المقدار لكل جلسة",
            value = portion.amount,
            min = if (isPage) 0.5 else 1.0,
            max = portion.unit.maxAmount.toDouble(),
            step = if (isPage) 0.5 else 1.0,
            hint = { Plurals.amount(portion.unit, it) },
            onDismiss = { amountDialog = false },
        ) {
            onChange(portion.copy(amount = it))
            amountDialog = false
        }
    }
}

@Composable
private fun RangeValues(range: RangeSpec, color: Color, onChange: (RangeSpec) -> Unit) {
    var dialog by remember { mutableStateOf<String?>(null) }
    fun name(s: Int) = Quran.surahName(s)
    when (range.type) {
        RangeType.WHOLE -> Hint("من الفاتحة إلى الناس")
        RangeType.SURAH -> LabeledRow("السورة") { Pill(name(range.from), { dialog = "from" }, color = color) }
        RangeType.SURAHS -> LabeledRow("من") {
            Pill(name(range.from), { dialog = "from" }, color = color)
            Word("إلى")
            Pill(name(range.to), { dialog = "to" }, color = color)
        }
        RangeType.JUZ -> LabeledRow("الجزء") { Pill("الجزء ${range.from}", { dialog = "from" }, color = color) }
        RangeType.JUZS -> LabeledRow("من") {
            Pill("الجزء ${range.from}", { dialog = "from" }, color = color)
            Word("إلى")
            Pill("الجزء ${range.to}", { dialog = "to" }, color = color)
        }
        RangeType.HIZB -> LabeledRow("الحزب") { Pill("الحزب ${range.from}", { dialog = "from" }, color = color) }
        RangeType.HIZBS -> LabeledRow("من") {
            Pill("الحزب ${range.from}", { dialog = "from" }, color = color)
            Word("إلى")
            Pill("الحزب ${range.to}", { dialog = "to" }, color = color)
        }
        RangeType.PAGES -> {
            LabeledRow("من") {
                Pill("ص ${range.from}", { dialog = "from" }, color = color)
                Word("إلى")
                Pill("ص ${range.to}", { dialog = "to" }, color = color)
            }
            Hint("ص ${range.from} تبدأ بسورة ${Quran.surahAtPage(range.from)}، وص ${range.to} تبدأ بسورة ${Quran.surahAtPage(range.to)}")
        }
        RangeType.AYAHS -> {
            LabeledRow("من") {
                Pill(name(range.from), { dialog = "from" }, color = color)
                Pill("آية ${range.fromAyah}", { dialog = "fromAyah" }, color = color)
            }
            LabeledRow("إلى") {
                Pill(name(range.to), { dialog = "to" }, color = color)
                if (!range.isBackward) Pill("آية ${range.toAyah}", { dialog = "toAyah" }, color = color)
            }
            if (range.isBackward) Hint("السورة الأخيرة تنحفظ كاملة، وكل سورة من أولها")
        }
    }

    val which = dialog ?: return
    val close = { dialog = null }
    when (range.type) {
        RangeType.SURAH, RangeType.SURAHS, RangeType.AYAHS -> when (which) {
            "from", "to" -> SurahPickerDialog(if (which == "from") "من سورة" else "إلى سورة", onDismiss = close) { n ->
                onChange(
                    when {
                        range.type == RangeType.SURAH -> range.copy(from = n, to = n)
                        which == "from" -> range.copy(from = n, fromAyah = 1)
                        else -> range.copy(to = n, toAyah = Quran.ayahCount(n))
                    },
                )
                close()
            }
            else -> {
                val surah = if (which == "fromAyah") range.from else range.to
                NumberDialog(
                    title = "رقم الآية في سورة ${name(surah)}",
                    value = (if (which == "fromAyah") range.fromAyah else range.toAyah).toDouble(),
                    min = 1.0,
                    max = Quran.ayahCount(surah).toDouble(),
                    step = 1.0,
                    onDismiss = close,
                ) { v ->
                    onChange(if (which == "fromAyah") range.copy(fromAyah = v.toInt()) else range.copy(toAyah = v.toInt()))
                    close()
                }
            }
        }
        RangeType.JUZ, RangeType.JUZS, RangeType.HIZB, RangeType.HIZBS -> {
            val isJuz = range.type == RangeType.JUZ || range.type == RangeType.JUZS
            NumberGridDialog(
                title = if (isJuz) "اختر الجزء" else "اختر الحزب",
                max = range.type.maxNumber,
                selected = if (which == "from") range.from else range.to,
                onDismiss = close,
            ) { n ->
                onChange(
                    when {
                        !range.type.twoEnds -> range.copy(from = n, to = n)
                        which == "from" -> range.copy(from = n)
                        else -> range.copy(to = n)
                    },
                )
                close()
            }
        }
        RangeType.PAGES -> NumberDialog(
            title = if (which == "from") "من صفحة" else "إلى صفحة",
            value = (if (which == "from") range.from else range.to).toDouble(),
            min = 1.0,
            max = Quran.PAGE_COUNT.toDouble(),
            step = 1.0,
            hint = { "ص ${it.toInt()} تبدأ بسورة ${Quran.surahAtPage(it.toInt().coerceIn(1, Quran.PAGE_COUNT))}" },
            onDismiss = close,
        ) { v ->
            onChange(if (which == "from") range.copy(from = v.toInt()) else range.copy(to = v.toInt()))
            close()
        }
        RangeType.WHOLE -> close()
    }
}

/** Where page sessions end: the rounding rule and «أكمل السورة». */
@Composable
private fun SessionEnds(portion: PortionSpec, byDate: Boolean, color: Color, onChange: (PortionSpec) -> Unit) {
    if (portion.unit != MemUnit.PAGE || !(byDate || portion.amount >= 1.0)) return
    RoundingSelector(portion.rounding, color) { onChange(portion.copy(rounding = it)) }
    SwitchRow(
        title = "راعِ حدود السور",
        hint = "إذا بدأت سورة جديدة في وسط آخر صفحة أو أسفلها، تتأجل للجلسة الجاية، وإذا بدأت في أعلاها تدخل في الجلسة. " +
            "وإذا انتهت الجلسة في نص سورة ونهايتها في الصفحة اللي بعدها، تكمّلها.",
        checked = portion.completeSurah,
        color = color,
    ) { onChange(portion.copy(completeSurah = it)) }
}

@Composable
internal fun SwitchRow(title: String, hint: String, checked: Boolean, color: Color, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = color))
    }
}

@Composable
private fun RoundingSelector(rounding: Rounding, color: Color, onChange: (Rounding) -> Unit) {
    var open by remember { mutableStateOf(false) }
    LabeledRow("نهاية الجلسة") { Pill(rounding.label, onClick = { open = !open }, dropdown = true, color = color) }
    Hint("المكان اللي تنتهي عنده الجلسة إذا ما جات عند آخر الصفحة")
    AnimatedVisibility(open) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Rounding.entries.forEach { r ->
                OptionRow(r.label, r.hint, r == rounding) {
                    onChange(r)
                    open = false
                }
            }
        }
    }
}
