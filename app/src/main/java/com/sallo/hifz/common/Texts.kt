package com.sallo.hifz.common

import com.sallo.hifz.data.Item
import com.sallo.hifz.data.PlanEntity
import com.sallo.hifz.data.SessionEntity
import com.sallo.hifz.dates.DateText
import com.sallo.hifz.plan.MemUnit
import com.sallo.hifz.plan.PlanSettings
import com.sallo.hifz.plan.PortionSpec
import com.sallo.hifz.plan.ScheduleMode
import com.sallo.hifz.plan.TrackSchedule
import java.time.DayOfWeek
import com.sallo.hifz.quran.RangeFormatter
import com.sallo.hifz.quran.Ranges
import java.util.Locale
import kotlin.math.roundToInt

/** Arabic counted nouns: 1, 2, 3 to 10, and 11 or more take different forms. */
object Plurals {
    fun lessons(n: Int) = count(n, "درس واحد", "درسان", "دروس", "درساً")
    fun sessions(n: Int) = count(n, "جلسة واحدة", "جلستان", "جلسات", "جلسة")
    fun ayahs(n: Int) = count(n, "آية واحدة", "آيتان", "آيات", "آية")
    fun pages(n: Int) = count(n, "وجه واحد", "وجهان", "أوجه", "وجهاً")
    fun days(n: Int) = count(n, "يوم واحد", "يومان", "أيام", "يوماً")
    fun weeks(n: Int) = count(n, "أسبوع واحد", "أسبوعان", "أسابيع", "أسبوعاً")
    fun juz(n: Int) = count(n, "جزء واحد", "جزءان", "أجزاء", "جزءاً")
    fun mistakes(n: Int) = count(n, "خطأ واحد", "خطآن", "أخطاء", "خطأً")
    fun alerts(n: Int) = count(n, "تنبيه واحد", "تنبيهان", "تنبيهات", "تنبيهاً")
    fun plans(n: Int) = count(n, "خطة واحدة", "خطتان", "خطط", "خطة")

    fun missed(n: Int) = when {
        n == 1 -> "فاتك درس واحد"
        n == 2 -> "فاتك درسان"
        n in 3..10 -> "فاتتك $n دروس"
        else -> "فاتك $n درساً"
    }

    fun every(n: Int) = when {
        n == 1 -> "كل يوم"
        n == 2 -> "كل يومين"
        n in 3..10 -> "كل $n أيام"
        else -> "كل $n يوماً"
    }

    fun everyWeeks(n: Int) = when {
        n == 1 -> "كل أسبوع"
        n == 2 -> "كل أسبوعين"
        n in 3..10 -> "كل $n أسابيع"
        else -> "كل $n أسبوعاً"
    }

    fun everyMonths(n: Int) = when {
        n == 1 -> "كل شهر"
        n == 2 -> "كل شهرين"
        n in 3..10 -> "كل $n أشهر"
        else -> "كل $n شهراً"
    }

    /** A number for display: 2, 1.5, ½. */
    fun number(value: Double): String = when {
        value == 0.5 -> "½"
        value % 1.0 == 0.0 -> value.toLong().toString()
        else -> String.format(Locale.US, "%.1f", value)
    }

    /**
     * A plan's progress: whole percents, with a tenth below 10% so the first sessions of a long plan
     * show (0.5%, not 0%), and never 100% before the plan is complete.
     */
    fun percent(progress: Float): String {
        val v = progress.toDouble() * 100.0
        return when {
            v <= 0.0 -> "0%"
            v < 9.95 -> String.format(Locale.US, "%.1f", maxOf(v, 0.1)).removeSuffix(".0") + "%"
            progress < 1f -> "${v.roundToInt().coerceAtMost(99)}%"
            else -> "100%"
        }
    }

    /** Session size: «10 آيات», «نصف وجه», «وجهان», «جزء واحد». */
    fun amount(unit: MemUnit, amount: Double): String {
        if (unit == MemUnit.PAGE && amount == 0.5) return "نصف وجه"
        if (unit == MemUnit.PAGE && amount % 1.0 != 0.0) return "${number(amount)} وجه"
        val n = amount.roundToInt()
        return when (unit) {
            MemUnit.AYAH -> ayahs(n)
            MemUnit.PAGE -> pages(n)
            MemUnit.QUARTER -> count(n, "ربع واحد", "ربعان", "أرباع", "ربعاً")
            MemUnit.HIZB -> count(n, "حزب واحد", "حزبان", "أحزاب", "حزباً")
            MemUnit.JUZ -> count(n, "جزء واحد", "جزءان", "أجزاء", "جزءاً")
            MemUnit.SURAH -> count(n, "سورة واحدة", "سورتان", "سور", "سورة")
            MemUnit.PASSAGE -> count(n, "مقطع واحد", "مقطعان", "مقاطع", "مقطعاً")
        }
    }

    private fun count(n: Int, one: String, two: String, few: String, many: String) = when {
        n == 1 -> one
        n == 2 -> two
        n in 3..10 -> "$n $few"
        else -> "$n $many"
    }
}

object PlanText {
    fun lesson(row: SessionEntity): String =
        row.newText ?: row.lessonOrNull?.let { RangeFormatter.format(it) } ?: RangeFormatter.NONE

    fun minor(row: SessionEntity): String = row.minorText ?: RangeFormatter.formatAll(Ranges.decode(row.minorRanges))

    /** Major review: a correction or hand-written text first, then the generated ranges. */
    fun major(row: SessionEntity): String = row.majorText ?: generatedMajor(row.majorParts)

    /** «الكافرون - الناس، القلم - الحاقة 25» when a round's last session goes on into the next round. */
    fun generatedMajor(parts: List<IntRange>): String = if (parts.isEmpty()) "" else RangeFormatter.formatAll(parts)

    /**
     * What the row's day holds: its lesson, its major review in a review-only plan, or the reviews of
     * a review-only day when the reviews have their own days.
     */
    fun main(row: SessionEntity, plan: PlanEntity): String = when {
        !plan.includeNew -> major(row).ifBlank { RangeFormatter.NONE }
        row.hasLesson -> lesson(row)
        else -> listOfNotNull(
            major(row).takeIf { plan.includeMajor && row.has(Item.MAJOR) && it.isNotBlank() }?.let { "الكبرى $it" },
            minor(row).takeIf { plan.includeMinor && row.has(Item.MINOR) && it != RangeFormatter.NONE }?.let { "الصغرى $it" },
        ).joinToString("، ").ifEmpty { RangeFormatter.NONE }
    }

    /** «درس» for a day with a lesson (or a review-only plan's session), «مراجعة» for a review-only day. */
    fun dayWord(row: SessionEntity, plan: PlanEntity): String = if (!plan.includeNew || row.hasLesson) "درس" else "مراجعة"

    fun schedule(s: PlanSettings): String = days(s.scheduleMode, s.weekdays, s.every)
        ?: s.endDate?.let { "حتى ${DateText.long(it)}" } ?: "حسب تاريخ الختم"

    fun schedule(t: TrackSchedule): String = days(t.mode, t.weekdays, t.every) ?: ""

    /** «الأحد، الثلاثاء، الخميس», «كل يوم», «كل يومين»…; null for a schedule by end date. */
    private fun days(mode: ScheduleMode, weekdays: Set<DayOfWeek>, every: Int): String? = when (mode) {
        ScheduleMode.WEEKDAYS -> if (weekdays.size == 7) "كل يوم" else DateText.WEEK_ORDER.filter { it in weekdays }.joinToString("، ") { DateText.weekday(it) }
        ScheduleMode.INTERVAL -> Plurals.every(every)
        ScheduleMode.WEEKS -> Plurals.everyWeeks(every)
        ScheduleMode.MONTHS -> Plurals.everyMonths(every)
        ScheduleMode.ANYTIME -> "في أي وقت"
        ScheduleMode.BY_DATE -> null
    }

    /** «أحفظ 10 آيات من العصر إلى الناس» */
    fun intent(verb: String, p: PortionSpec, byDate: Boolean): String =
        if (byDate) "$verb ${p.range.describe()}" else "$verb ${Plurals.amount(p.unit, p.amount)} ${rangeWords(p)}"

    private fun rangeWords(p: PortionSpec): String {
        val d = p.range.describe()
        return if (d.startsWith("من ")) d else "من $d"
    }

    /** One line for cards: what the plan does and when. */
    fun describe(s: PlanSettings): String {
        val byDate = s.scheduleMode == ScheduleMode.BY_DATE
        val parts = mutableListOf<String>()
        if (s.includeNew) parts += intent("حفظ", s.newPortion, byDate)
        if (s.hasMemorizedMajor) {
            parts += "مراجعة ${Plurals.amount(s.majorPortion.unit, s.majorPortion.amount)} من محفوظك"
        } else if (s.hasAutoMajor) {
            parts += intent("مراجعة", s.majorPortion, byDate && !s.includeNew)
        }
        if (s.hasSeparateSchedules) {
            parts += "الحفظ ${schedule(s)}"
            if (s.hasMinor) parts += "الصغرى ${schedule(s.minorSchedule)}"
            if (s.includeMajor) parts += "الكبرى ${schedule(s.majorSchedule)}"
        } else {
            parts += schedule(s)
        }
        return parts.joinToString(" • ")
    }

    fun time(hour: Int, minute: Int): String {
        val h = (hour % 12).let { if (it == 0) 12 else it }
        val suffix = if (hour < 12) "ص" else "م"
        return "$h:${minute.toString().padStart(2, '0')} $suffix"
    }
}
