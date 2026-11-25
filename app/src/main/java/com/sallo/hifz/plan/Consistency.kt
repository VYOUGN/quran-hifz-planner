package com.sallo.hifz.plan

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** How a day of the calendar of days («تقويم الالتزام») looks. */
enum class DayState(val weight: Int) {
    VACATION(0),
    DONE(1),
    UPCOMING(2),
    TODAY(3),
    PARTIAL(4),
    MISSED(5),
}

object Consistency {
    /**
     * The state of every day the plan touches. A day with sessions is done, partly done, missed,
     * today's or still to come; a vacation day stays a vacation unless everything on it was done.
     * A study day that passed with no session at all (its lessons moved on) is missed too: the
     * same rule that breaks the streak ([Stats]).
     */
    fun days(rows: List<DayRow>, calendar: StudyCalendar?, today: LocalDate): Map<LocalDate, DayState> {
        val out = HashMap<LocalDate, DayState>()
        val byDate = rows.groupBy { it.date }.toSortedMap()
        for ((date, list) in byDate) {
            out[date] = when {
                list.all { it.done } -> DayState.DONE
                calendar?.vacationOn(date) != null -> DayState.VACATION
                date == today -> DayState.TODAY
                date > today -> DayState.UPCOMING
                list.any { it.done || it.partly } -> DayState.PARTIAL
                else -> DayState.MISSED
            }
        }
        if (calendar != null && calendar.mode != ScheduleMode.ANYTIME) {
            val past = byDate.keys.filter { it < today }
            for ((i, day) in past.withIndex()) {
                val until = past.getOrNull(i + 1) ?: today
                var d = calendar.next(day)
                var guard = 0
                while (d < until && guard++ < 5000) {
                    out.putIfAbsent(d, DayState.MISSED)
                    d = calendar.next(d)
                }
            }
            for (v in calendar.off) {
                var d = v.start
                while (!d.isAfter(v.end)) {
                    out.putIfAbsent(d, DayState.VACATION)
                    d = d.plusDays(1)
                }
            }
        }
        return out
    }

    /** Several plans' days as one: each day as its worst plan has it. */
    fun combine(maps: List<Map<LocalDate, DayState>>): Map<LocalDate, DayState> {
        val out = HashMap<LocalDate, DayState>()
        for (m in maps) for ((date, state) in m) {
            val had = out[date]
            if (had == null || state.weight > had.weight) out[date] = state
        }
        return out
    }

    /** (days done, days due so far) in [month], for the line under the calendar. */
    fun tally(states: Map<LocalDate, DayState>, month: YearMonth, today: LocalDate): Pair<Int, Int> {
        var done = 0
        var due = 0
        for (d in 1..month.lengthOfMonth()) {
            val date = month.atDay(d)
            if (date > today) break
            when (states[date]) {
                DayState.DONE -> { done++; due++ }
                DayState.PARTIAL, DayState.MISSED -> due++
                DayState.TODAY -> due++
                else -> Unit
            }
        }
        return done to due
    }

    /** Weeks (Sunday to Saturday) that ended before today with something done and nothing missed. */
    fun perfectWeeks(states: Map<LocalDate, DayState>, today: LocalDate): Int {
        val firstDay = states.keys.minOrNull() ?: return 0
        var start = firstDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        var count = 0
        while (start.plusDays(6) < today) {
            val week = (0L..6L).mapNotNull { states[start.plusDays(it)] }
            if (week.any { it == DayState.DONE } && week.none { it == DayState.MISSED || it == DayState.PARTIAL }) count++
            start = start.plusWeeks(1)
        }
        return count
    }

    /** Months that ended before this one with at least four days done and nothing missed. */
    fun perfectMonths(states: Map<LocalDate, DayState>, today: LocalDate): Int {
        val firstDay = states.keys.minOrNull() ?: return 0
        var month = YearMonth.from(firstDay)
        val now = YearMonth.from(today)
        var count = 0
        while (month < now) {
            val days = (1..month.lengthOfMonth()).mapNotNull { states[month.atDay(it)] }
            if (days.count { it == DayState.DONE } >= 4 && days.none { it == DayState.MISSED || it == DayState.PARTIAL }) count++
            month = month.plusMonths(1)
        }
        return count
    }
}
