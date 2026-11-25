package com.sallo.hifz.plan

import com.sallo.hifz.quran.Quran
import java.time.DayOfWeek
import java.time.LocalDate

/** A day of «آخر 7 أيام»: sessions due and done across every plan. */
data class DayTally(val date: LocalDate, val due: Int, val done: Int)

/** «إحصائياتي»: the numbers of every plan together. */
class Overview(
    /** Ayahs, pages and whole juz memorized, each counted once across plans. */
    val ayahs: Int,
    val pages: Double,
    val juz: Int,
    val sessionsDone: Int,
    val sessionsTotal: Int,
    val bestStreak: Int,
    val currentStreak: Int,
    val last7: List<DayTally>,
    /** Share of due days done on each weekday over the last 90 days, Sunday first; null with none due. */
    val weekdays: List<Pair<DayOfWeek, Float?>>,
    /** Average mistakes per recited item over the last 30 days, or null when none were recorded. */
    val mistakesAverage: Double?,
    /** The sessions with the most mistakes, most first. */
    val topMistakes: List<MistakeRow>,
    val firstDay: LocalDate?,
)

object Overviews {
    val WEEK: List<DayOfWeek> = listOf(
        DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
    )

    fun compute(plans: List<PlanFacts>, today: LocalDate): Overview {
        val covered = Achievements.covered(plans.filter { it.memorizes }.flatMap { it.stats })
        val ranges = Achievements.ranges(covered)
        val streaks = plans.filter { it.calendar != null }.map { Stats.compute(it.stats, today, it.calendar) }
        val states = plans.filter { it.calendar != null }.map { Consistency.days(it.days, it.calendar, today) }

        val last7 = (6 downTo 0).map { back ->
            val day = today.minusDays(back.toLong())
            var due = 0
            var done = 0
            for (m in states) when (m[day]) {
                DayState.DONE -> { due++; done++ }
                DayState.PARTIAL, DayState.MISSED, DayState.TODAY -> due++
                else -> Unit
            }
            DayTally(day, due, done)
        }

        val since = today.minusDays(90)
        val weekdays = WEEK.map { weekday ->
            var due = 0
            var done = 0
            for (m in states) for ((day, state) in m) {
                if (day.dayOfWeek != weekday || day < since || day >= today) continue
                when (state) {
                    DayState.DONE -> { due++; done++ }
                    DayState.PARTIAL, DayState.MISSED -> due++
                    else -> Unit
                }
            }
            weekday to (if (due == 0) null else done.toFloat() / due)
        }

        val recent = plans.flatMap { it.mistakes }.filter { it.date > today.minusDays(30) }
        val items = recent.sumOf { it.items }
        return Overview(
            ayahs = covered.count { it },
            pages = ranges.sumOf { Quran.pages(it) },
            juz = Achievements.juzDone(plans.filter { it.memorizes }.flatMap { it.stats }).size,
            sessionsDone = plans.sumOf { p -> p.stats.count { it.done } },
            sessionsTotal = plans.sumOf { it.stats.size },
            bestStreak = streaks.maxOfOrNull { it.bestStreak } ?: 0,
            currentStreak = streaks.maxOfOrNull { it.currentStreak } ?: 0,
            last7 = last7,
            weekdays = weekdays,
            mistakesAverage = if (items == 0) null else recent.sumOf { it.mistakes }.toDouble() / items,
            topMistakes = topMistakes(plans.flatMap { it.mistakes }),
            firstDay = plans.flatMap { p -> p.stats.filter { it.done }.map { it.date } }.minOrNull(),
        )
    }

    /** The five sessions with the most mistakes, most first (alerts break ties). */
    fun topMistakes(rows: List<MistakeRow>, count: Int = 5): List<MistakeRow> =
        rows.filter { it.mistakes > 0 }.sortedWith(compareByDescending<MistakeRow> { it.mistakes }.thenByDescending { it.alerts }).take(count)

    /** Average mistakes per recited item, or null when none were recorded. */
    fun average(rows: List<MistakeRow>): Double? {
        val items = rows.sumOf { it.items }
        return if (items == 0) null else rows.sumOf { it.mistakes }.toDouble() / items
    }
}
