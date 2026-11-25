package com.sallo.hifz.plan

import com.sallo.hifz.quran.Quran
import java.time.DayOfWeek
import java.time.LocalDate

/** When sessions happen («الجدول»). */
enum class ScheduleMode {
    /** Chosen days of the week. */
    WEEKDAYS,

    /** Every N days. */
    INTERVAL,

    /** Every N weeks. */
    WEEKS,

    /** Every N months. */
    MONTHS,

    /** No dates: sessions are just numbered («أي وقت»). */
    ANYTIME,

    /** Chosen days of the week until an end date; the amount is worked out from it. */
    BY_DATE,
}

enum class MinorMode { LAST_N, CUMULATIVE_JUZ }

/** How the major review is filled in («المراجعة الكبرى»). */
enum class MajorMode {
    /** Goes round a range chosen in advance («مدى محدد»). */
    RANGE,

    /**
     * Goes round everything memorized so far that the minor review does not cover («من محفوظي»).
     * Each round takes in the lessons memorized before it starts.
     */
    MEMORIZED,

    /** Written by hand in each row («أكتبها بنفسي»). */
    MANUAL,
}

/** What happens when lessons are missed («التأخر عن الخطة»). */
enum class BehindPolicy { ASK, AUTO }

/** The days of one review when each track has its own days («لكل مسار أيامه»). */
data class TrackSchedule(
    val mode: ScheduleMode = ScheduleMode.WEEKDAYS,
    val weekdays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    /** N in «every N days / weeks / months». */
    val every: Int = 1,
) {
    val calendar: StudyCalendar get() = StudyCalendar(mode, weekdays, every)

    /** Arabic error message, or null. */
    fun validate(): String? = when (mode) {
        ScheduleMode.WEEKDAYS -> if (weekdays.isEmpty()) "اختر يوم واحد على الأقل" else null
        ScheduleMode.INTERVAL, ScheduleMode.WEEKS, ScheduleMode.MONTHS -> if (every !in 1..30) "العدد من 1 إلى 30" else null
        ScheduleMode.ANYTIME, ScheduleMode.BY_DATE -> "اختر أيام المراجعة"
    }

    companion object {
        /** Modes a review can follow on its own. */
        val MODES = listOf(ScheduleMode.WEEKDAYS, ScheduleMode.INTERVAL, ScheduleMode.WEEKS, ScheduleMode.MONTHS)
    }
}

/** Everything needed to generate a plan's table. */
data class PlanSettings(
    val name: String = "خطتي",
    val startDate: LocalDate = LocalDate.now(),
    val scheduleMode: ScheduleMode = ScheduleMode.WEEKDAYS,
    val weekdays: Set<DayOfWeek> = DEFAULT_DAYS,
    /** N in «every N days / weeks / months». */
    val every: Int = 2,
    /** BY_DATE: the last day of the plan. */
    val endDate: LocalDate? = null,
    val includeNew: Boolean = true,
    val newPortion: PortionSpec = PortionSpec(),
    val includeMinor: Boolean = true,
    val minorMode: MinorMode = MinorMode.LAST_N,
    val minorCount: Int = 5,
    val includeMajor: Boolean = true,
    val majorMode: MajorMode = MajorMode.MANUAL,
    /**
     * Size and rounding of a generated major review. Its range is the range to go round (RANGE), or
     * what was memorized before the plan (MEMORIZED, when [majorPrior]).
     */
    val majorPortion: PortionSpec = DEFAULT_MAJOR,
    /** MEMORIZED: false when nothing was memorized before the plan. */
    val majorPrior: Boolean = true,
    /**
     * «كمّل من الدورة الجاية»: a round's last session that is short of the amount is filled up from the
     * start of the next round. With five pages and two left, it reviews those two and three of the next round.
     */
    val majorWrap: Boolean = false,
    /** How grades are shown; it does not change the rows. */
    val gradeScale: GradeScale = GradeScale.WORDS,
    val prepEnabled: Boolean = false,
    val behind: BehindPolicy = BehindPolicy.ASK,
    /**
     * «لكل مسار أيامه»: the reviews follow [minorSchedule] and [majorSchedule] instead of the plan's
     * days, which then belong to the new memorization (and its preparation) only.
     */
    val separateSchedules: Boolean = false,
    val minorSchedule: TrackSchedule = TrackSchedule(),
    val majorSchedule: TrackSchedule = TrackSchedule(),
    /** Vacations («إجازة»): no session of any track falls on them. */
    val vacations: List<Vacation> = emptyList(),
) {
    val calendar: StudyCalendar get() = StudyCalendar(scheduleMode, weekdays, every, vacations)

    /** A review's own days («لكل مسار أيامه»), with the plan's vacations. */
    fun trackCalendar(t: TrackSchedule): StudyCalendar = t.calendar.copy(off = vacations)
    val hasDates: Boolean get() = scheduleMode != ScheduleMode.ANYTIME

    /** Separate days need dates, new memorization to set the plan's length, and a review to schedule. */
    val canSeparateSchedules: Boolean get() = includeNew && hasDates && (hasMinor || includeMajor)
    val hasSeparateSchedules: Boolean get() = separateSchedules && canSeparateSchedules

    /** Turns separate days on (each review starting from the plan's days) or off. */
    fun withSeparateSchedules(on: Boolean): PlanSettings {
        if (!on) return copy(separateSchedules = false)
        val mode = if (scheduleMode in TrackSchedule.MODES) scheduleMode else ScheduleMode.WEEKDAYS
        val same = TrackSchedule(mode, weekdays.ifEmpty { DEFAULT_DAYS }, every.coerceIn(1, 30))
        return copy(separateSchedules = true, minorSchedule = same, majorSchedule = same)
    }
    val hasMinor: Boolean get() = includeNew && includeMinor
    val hasPrep: Boolean get() = includeNew && prepEnabled
    val hasAutoMajor: Boolean get() = includeMajor && majorMode != MajorMode.MANUAL

    /** «من محفوظي» grows with the new memorization; without it the range is simply gone round. */
    val hasMemorizedMajor: Boolean get() = includeNew && hasAutoMajor && majorMode == MajorMode.MEMORIZED

    /** MEMORIZED: what was memorized before the plan, or null. */
    val priorMemorized: IntRange? get() = if (majorPrior) majorPortion.range.bounds() else null

    /** The major review goes round in rounds (alongside new memorization) in sessions that can be filled up. */
    val canWrapMajor: Boolean get() = includeNew && hasAutoMajor && majorPortion.unit.wraps

    /** A round's last session is filled up from the next round. */
    val wrapsMajor: Boolean get() = majorWrap && canWrapMajor

    /** The track whose sessions set the length of the plan. */
    val drivingPortion: PortionSpec get() = if (includeNew) newPortion else majorPortion

    /** BY_DATE: how many sessions fit up to the end date. */
    fun sessionLimit(): Int? =
        if (scheduleMode == ScheduleMode.BY_DATE) endDate?.let { calendar.countBetween(startDate, it) } else null

    /**
     * What was most likely memorized before the plan: whatever comes before its first lesson in the
     * direction of memorization. Starting backward from Al-Haqqah means Al-Ma'arij to An-Nas is
     * memorized; starting forward from Al-Baqarah means Al-Fatiha is.
     */
    fun suggestedPrior(): RangeSpec? {
        val r = newPortion.range
        val b = r.bounds()
        val before = if (r.isBackward) (b.last + 1) until Quran.AYAH_COUNT else 0 until b.first
        return if (before.isEmpty()) null else RangeSpec.covering(before)
    }

    /** Sets what was memorized before the plan to [suggestedPrior]. */
    fun withSuggestedPrior(): PlanSettings {
        val prior = suggestedPrior()
        return copy(majorPrior = prior != null, majorPortion = majorPortion.copy(range = prior ?: majorPortion.range, resume = null))
    }

    /** Changes the new memorization; a prior memorization still at its suggestion follows the change. */
    fun withNewPortion(p: PortionSpec): PlanSettings {
        val suggested = suggestedPrior()
        val following = majorMode == MajorMode.MEMORIZED &&
            if (suggested == null) !majorPrior else majorPrior && majorPortion.range == suggested
        val next = copy(newPortion = p)
        return if (following) next.withSuggestedPrior() else next
    }

    /** Arabic error message, or null when the settings can generate a table. */
    fun validate(): String? {
        if (name.isBlank()) return "اكتب اسم الخطة"
        if (!includeNew && !hasAutoMajor) return "اختر الحفظ أو مراجعة كبرى تلقائية، عشان يتكوّن الجدول"
        when (scheduleMode) {
            ScheduleMode.WEEKDAYS, ScheduleMode.BY_DATE -> if (weekdays.isEmpty()) return "اختر يوم واحد على الأقل"
            ScheduleMode.INTERVAL, ScheduleMode.WEEKS, ScheduleMode.MONTHS -> if (every !in 1..30) return "العدد من 1 إلى 30"
            ScheduleMode.ANYTIME -> Unit
        }
        if (scheduleMode == ScheduleMode.BY_DATE) {
            val end = endDate ?: return "اختر تاريخ الختم"
            if (end < startDate) return "تاريخ الختم قبل تاريخ البداية"
            if ((sessionLimit() ?: 0) == 0) return "ما فيه أيام حفظ قبل تاريخ الختم"
        }
        if (minorCount !in 0..60) return "عدد دروس المراجعة الصغرى من 0 إلى 60"
        if (hasSeparateSchedules) {
            if (hasMinor) minorSchedule.validate()?.let { return "أيام المراجعة الصغرى: $it" }
            if (includeMajor) majorSchedule.validate()?.let { return "أيام المراجعة الكبرى: $it" }
        }
        if (includeNew) newPortion.validate()?.let { return it }
        if (hasAutoMajor) majorPortion.validate()?.let { return "المراجعة الكبرى: $it" }
        return null
    }

    companion object {
        val DEFAULT_DAYS: Set<DayOfWeek> = setOf(DayOfWeek.SUNDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)
        val DEFAULT_MAJOR = PortionSpec(MemUnit.PAGE, 5.0, RangeSpec(RangeType.JUZ, 30, 30), Rounding.NEAREST, completeSurah = true)
    }
}
