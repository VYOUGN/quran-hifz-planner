package com.sallo.hifz.plan

import com.sallo.hifz.common.Plurals
import java.time.LocalDate

/** One plan's last seven days, for «التقرير الأسبوعي». */
data class PlanWeek(val name: String, val due: Int, val done: Int, val missed: Int, val streak: Int, val upcoming: Int)

object WeeklyReport {
    /** [plan] over the seven days up to [today], and its sessions in the seven days after. */
    fun week(plan: PlanFacts, today: LocalDate): PlanWeek {
        val days = Consistency.days(plan.days, plan.calendar, today)
        var due = 0
        var done = 0
        var missed = 0
        for (back in 0L..6L) {
            when (days[today.minusDays(back)]) {
                DayState.DONE -> { due++; done++ }
                DayState.PARTIAL, DayState.TODAY -> due++
                DayState.MISSED -> { due++; missed++ }
                else -> Unit
            }
        }
        val streak = if (plan.calendar != null) Stats.compute(plan.stats, today, plan.calendar).currentStreak else 0
        // Sessions of the coming week still to do: a day ticked ahead of time is not still to come.
        val upcoming = plan.days.filter { it.date > today && it.date <= today.plusDays(7) && !it.done }.map { it.date }.distinct().size
        return PlanWeek(plan.name, due, done, missed, streak, upcoming)
    }

    /** «خطتي: أنجزت 5 من 6، فاتك درس واحد، 8 متتالية». */
    fun line(w: PlanWeek): String = buildList {
        add(if (w.due == 0) "ما كان فيها جلسات" else "أنجزت ${w.done} من ${w.due}")
        if (w.missed > 0) add(Plurals.missed(w.missed))
        if (w.streak > 1) add("${w.streak} متتالية")
    }.joinToString("، ", prefix = "${w.name}: ")

    /** The report's lines; empty when no plan had or has sessions around this week. */
    fun lines(weeks: List<PlanWeek>): List<String> {
        if (weeks.all { it.due == 0 && it.upcoming == 0 }) return emptyList()
        val upcoming = weeks.sumOf { it.upcoming }
        return weeks.filter { it.due > 0 || it.upcoming > 0 }.map(::line) +
            listOfNotNull(if (upcoming > 0) "الأسبوع الجاي: ${Plurals.sessions(upcoming)}" else null)
    }
}
