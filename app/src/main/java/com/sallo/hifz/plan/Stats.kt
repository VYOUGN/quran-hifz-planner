package com.sallo.hifz.plan

import com.sallo.hifz.quran.Quran
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

data class StatRow(val date: LocalDate, val lesson: IntRange, val done: Boolean)

data class PlanStats(
    val total: Int,
    val done: Int,
    val ayahs: Int,
    val pages: Double,
    val letters: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    /** Date of the last lesson in the table, or null when everything is done. */
    val scheduledFinish: LocalDate?,
    /** Finish date at the pace kept so far, or null with fewer than 3 done lessons. */
    val paceFinish: LocalDate?,
) {
    val progress: Float get() = if (total == 0) 0f else done.toFloat() / total
    val isComplete: Boolean get() = total > 0 && done == total
}

object Stats {
    /**
     * [calendar] is the plan's study days. With it, a study day that passed with no session at all
     * (its lessons moved on by «ترحيل» or postponed) breaks a streak, as a missed lesson does.
     */
    fun compute(rows: List<StatRow>, today: LocalDate, calendar: StudyCalendar? = null): PlanStats {
        val doneRows = rows.filter { it.done }
        // A study day comes after [from] and before [to] without a session.
        fun gap(from: LocalDate, to: LocalDate): Boolean = calendar != null && calendar.next(from) < to

        var best = 0
        var run = 0
        for ((k, r) in rows.withIndex()) {
            if (k > 0 && gap(rows[k - 1].date, r.date)) run = 0
            if (r.done) {
                run++
                best = maxOf(best, run)
            } else {
                run = 0
            }
        }

        // Today's lesson can still be done, so it does not break the current streak.
        var i = rows.indexOfLast { it.date <= today }
        if (i >= 0 && rows[i].date == today && !rows[i].done) i--
        var current = 0
        // A study day since the last session, with nothing on it, has already broken the streak.
        if (i >= 0 && !gap(rows[i].date, today)) {
            while (i >= 0 && rows[i].done) {
                current++
                if (i > 0 && gap(rows[i - 1].date, rows[i].date)) break
                i--
            }
        }

        val remaining = rows.size - doneRows.size
        val paceFinish = if (doneRows.size >= 3 && remaining > 0) {
            val elapsed = (ChronoUnit.DAYS.between(rows.first().date, today) + 1).coerceAtLeast(1)
            val perDay = doneRows.size.toDouble() / elapsed
            today.plusDays(ceil(remaining / perDay).toLong())
        } else {
            null
        }

        return PlanStats(
            total = rows.size,
            done = doneRows.size,
            ayahs = doneRows.sumOf { it.lesson.last - it.lesson.first + 1 },
            pages = doneRows.sumOf { Quran.pages(it.lesson) },
            letters = doneRows.sumOf { Quran.letters(it.lesson) },
            currentStreak = current,
            bestStreak = best,
            scheduledFinish = if (remaining == 0) null else rows.lastOrNull()?.date,
            paceFinish = paceFinish,
        )
    }
}
