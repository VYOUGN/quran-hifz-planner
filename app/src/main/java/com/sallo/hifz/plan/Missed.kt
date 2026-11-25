package com.sallo.hifz.plan

import java.time.LocalDate

/** How missed lessons are put back into the plan («طريقة الترحيل»). */
enum class CatchUp {
    /** From a chosen day on, a lesson per study day: everything after moves, and the plan ends later. */
    SHIFT,

    /** A session every day until the plan is back on its own dates: the end stays where it was. */
    DAILY,

    /** They were memorized but not ticked: they are marked done where they are. */
    MARK_DONE,
}

/** Lessons whose date passed without being memorized, and putting them back into the plan. */
object Missed {
    /**
     * First row after the last row whose new lesson is done, when that row is dated before
     * [today]. Rows after the last done lesson are never done themselves, so moving them is safe.
     */
    fun firstMissed(dates: List<LocalDate>, newDone: List<Boolean>, today: LocalDate): Int? {
        val first = newDone.indexOfLast { it } + 1
        return first.takeIf { it < dates.size && dates[it] < today }
    }

    fun count(dates: List<LocalDate>, newDone: List<Boolean>, today: LocalDate): Int {
        val first = firstMissed(dates, newDone, today) ?: return 0
        var n = 0
        var i = first
        while (i < dates.size && dates[i] < today) {
            n++
            i++
        }
        return n
    }

    /**
     * Dates for the whole plan after moving the missed rows: rows before the first missed row keep
     * their dates, and from there one lesson per study day starting today or the next study day.
     */
    fun reschedule(dates: List<LocalDate>, newDone: List<Boolean>, today: LocalDate, calendar: StudyCalendar): List<LocalDate>? {
        val first = firstMissed(dates, newDone, today) ?: return null
        return dates.take(first) + calendar.dates(today, dates.size - first)
    }

    /** «كمّلها من جديد»: the rows from [first] start exactly on [start], and the rest follow on study days. */
    fun shift(dates: List<LocalDate>, first: Int, start: LocalDate, calendar: StudyCalendar): List<LocalDate> =
        dates.take(first) + calendar.datesFrom(start, dates.size - first)

    /**
     * «عوّضها بجلسة كل يوم»: the rows from [first] that are behind their day take today, tomorrow and so on,
     * until a row's own day comes round; from there the plan keeps its dates and its end. With a lesson
     * every day there is no day to spare, so every row just moves.
     */
    fun daily(dates: List<LocalDate>, first: Int, today: LocalDate): List<LocalDate> {
        val out = dates.toMutableList()
        var day = today
        var i = first
        while (i < out.size && out[i] < day) {
            out[i] = day
            day = day.plusDays(1)
            i++
        }
        return out
    }

    /**
     * The last day of [daily]'s sessions, when the plan is back on its own dates after it; null when
     * every row up to the end has to move (nothing is gained over shifting the plan).
     */
    fun dailyUntil(dates: List<LocalDate>, first: Int, today: LocalDate): LocalDate? {
        val moved = daily(dates, first, today)
        var i = first
        while (i < dates.size && moved[i] != dates[i]) i++
        return if (i < dates.size && i > first) moved[i - 1] else null
    }

    /**
     * Where the plan moves when a vacation from [from] is added or removed: the first row still to do
     * (after the last done one) dated on or after [from], and its new date. [calendar] already has the
     * plan's vacations as they will be. Adding one moves that row out of it (or to today when its day
     * has passed); removing one ([pullBack]) brings it back to [from], or today. Null when nothing moves.
     */
    fun afterVacation(
        dates: List<LocalDate>,
        mainDone: List<Boolean>,
        from: LocalDate,
        today: LocalDate,
        calendar: StudyCalendar,
        pullBack: Boolean,
    ): Pair<Int, LocalDate>? {
        val afterDone = mainDone.indexOfLast { it } + 1
        val first = (afterDone until dates.size).firstOrNull { dates[it] >= from } ?: return null
        val start = calendar.firstOnOrAfter(maxOf(if (pullBack) from else dates[first], today))
        return (first to start).takeIf { shift(dates, first, start, calendar) != dates }
    }

    /** Dates after postponing row [from] to [newDate]; later rows follow on study days. */
    fun postpone(dates: List<LocalDate>, from: Int, newDate: LocalDate, calendar: StudyCalendar): List<LocalDate> =
        dates.take(from) + calendar.datesFrom(newDate, dates.size - from)
}
