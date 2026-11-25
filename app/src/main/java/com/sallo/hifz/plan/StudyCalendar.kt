package com.sallo.hifz.plan

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Days off («إجازة»), both ends included: no session falls on them. */
data class Vacation(val start: LocalDate, val end: LocalDate) {
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    val days: Int get() = (ChronoUnit.DAYS.between(start, end) + 1).toInt()

    companion object {
        /** "startEpochDay-endEpochDay;…", as stored with the plan. */
        fun encode(list: List<Vacation>): String = list.joinToString(";") { "${it.start.toEpochDay()}-${it.end.toEpochDay()}" }

        /** Back from [encode]; anything unreadable is left out. */
        fun decode(text: String): List<Vacation> = text.split(';').mapNotNull { part ->
            val bits = part.trim().split('-')
            val a = bits.getOrNull(0)?.toLongOrNull()
            val b = bits.getOrNull(1)?.toLongOrNull()
            if (bits.size != 2 || a == null || b == null) null else Vacation(LocalDate.ofEpochDay(minOf(a, b)), LocalDate.ofEpochDay(maxOf(a, b)))
        }.sortedBy { it.start }
    }
}

/** Which days have a session. */
data class StudyCalendar(
    val mode: ScheduleMode,
    val weekdays: Set<DayOfWeek>,
    /** N in «every N days / weeks / months». */
    val every: Int,
    /** Vacations: no session falls on them, and the plan goes on after them. */
    val off: List<Vacation> = emptyList(),
) {
    private val usesWeekdays: Boolean get() = mode == ScheduleMode.WEEKDAYS || mode == ScheduleMode.BY_DATE

    /** The vacation [date] falls in, or null. */
    fun vacationOn(date: LocalDate): Vacation? = off.firstOrNull { date in it }

    private fun weekdayOnOrAfter(date: LocalDate): LocalDate {
        if (!usesWeekdays) return date
        require(weekdays.isNotEmpty()) { "no study days" }
        var d = date
        while (d.dayOfWeek !in weekdays) d = d.plusDays(1)
        return d
    }

    /** [date], or the first study day after it that is not in a vacation. */
    private fun clear(date: LocalDate): LocalDate {
        var d = weekdayOnOrAfter(date)
        while (true) {
            val vacation = vacationOn(d) ?: return d
            d = weekdayOnOrAfter(vacation.end.plusDays(1))
        }
    }

    fun firstOnOrAfter(date: LocalDate): LocalDate = clear(date)

    fun next(date: LocalDate): LocalDate {
        val n = every.coerceAtLeast(1).toLong()
        return when (mode) {
            ScheduleMode.WEEKDAYS, ScheduleMode.BY_DATE -> clear(date.plusDays(1))
            ScheduleMode.INTERVAL -> clear(date.plusDays(n))
            ScheduleMode.WEEKS -> clear(date.plusWeeks(n))
            ScheduleMode.MONTHS -> clear(date.plusMonths(n))
            ScheduleMode.ANYTIME -> date
        }
    }

    /** [count] session dates, starting with the first study day on or after [start]. */
    fun dates(start: LocalDate, count: Int): List<LocalDate> {
        if (count <= 0) return emptyList()
        return datesFrom(firstOnOrAfter(start), count)
    }

    /** [count] dates starting exactly on [first] (even if it is not a study day), then following the calendar. */
    fun datesFrom(first: LocalDate, count: Int): List<LocalDate> {
        if (count <= 0) return emptyList()
        val out = ArrayList<LocalDate>(count)
        var d = first
        repeat(count) {
            out += d
            d = next(d)
        }
        return out
    }

    /** Study days from [start] to [end], both included. */
    fun datesBetween(start: LocalDate, end: LocalDate): List<LocalDate> {
        if (mode == ScheduleMode.ANYTIME || end < start) return emptyList()
        val out = ArrayList<LocalDate>()
        var d = firstOnOrAfter(start)
        while (!d.isAfter(end)) {
            out += d
            d = next(d)
        }
        return out
    }

    /** Number of study days from [start] to [end], both included. */
    fun countBetween(start: LocalDate, end: LocalDate): Int {
        if (mode == ScheduleMode.ANYTIME || end < start) return 0
        var count = 0
        var d = firstOnOrAfter(start)
        while (!d.isAfter(end)) {
            count++
            d = next(d)
        }
        return count
    }
}
