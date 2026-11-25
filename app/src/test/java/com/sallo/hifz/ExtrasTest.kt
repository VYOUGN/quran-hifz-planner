package com.sallo.hifz

import com.sallo.hifz.data.PlanBackup
import com.sallo.hifz.data.Backup
import com.sallo.hifz.data.Item
import com.sallo.hifz.data.planFacts
import com.sallo.hifz.data.toEntity
import com.sallo.hifz.data.toSettings
import com.sallo.hifz.data.withAllDone
import com.sallo.hifz.plan.Achievements
import com.sallo.hifz.plan.Badge
import com.sallo.hifz.plan.BadgeInput
import com.sallo.hifz.plan.Badges
import com.sallo.hifz.plan.Consistency
import com.sallo.hifz.plan.DayRow
import com.sallo.hifz.plan.DayState
import com.sallo.hifz.plan.Missed
import com.sallo.hifz.plan.MistakeRow
import com.sallo.hifz.plan.Overviews
import com.sallo.hifz.plan.PlanBuilder
import com.sallo.hifz.plan.PlanFacts
import com.sallo.hifz.plan.PlanSettings
import com.sallo.hifz.plan.PlanTemplate
import com.sallo.hifz.plan.PlanWeek
import com.sallo.hifz.plan.ScheduleMode
import com.sallo.hifz.plan.StatRow
import com.sallo.hifz.plan.Stats
import com.sallo.hifz.plan.StudyCalendar
import com.sallo.hifz.plan.Vacation
import com.sallo.hifz.plan.WeeklyReport
import com.sallo.hifz.quran.Quran
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ExtrasTest {
    private fun d(month: Int, day: Int) = LocalDate.of(2026, month, day)
    private val sunTueThu = StudyCalendar(ScheduleMode.WEEKDAYS, PlanSettings.DEFAULT_DAYS, 2)
    private val daily = StudyCalendar(ScheduleMode.INTERVAL, emptySet(), 1)
    private val lesson = 0..9

    @Test
    fun vacationsSkipStudyDays() {
        val trip = Vacation(d(11, 8), d(11, 12))
        val cal = sunTueThu.copy(off = listOf(trip))
        assertEquals(listOf(d(11, 1), d(11, 3), d(11, 5), d(11, 15), d(11, 17)), cal.dates(d(11, 1), 5))
        assertEquals(d(11, 15), cal.firstOnOrAfter(d(11, 9)))
        assertEquals(trip, cal.vacationOn(d(11, 10)))
        // Every other day: the plan goes on the day after the vacation.
        val other = StudyCalendar(ScheduleMode.INTERVAL, emptySet(), 2, listOf(trip))
        assertEquals(listOf(d(11, 5), d(11, 7), d(11, 13), d(11, 15)), other.datesFrom(d(11, 5), 4))
        // Stored with the plan, and read back; anything unreadable is left out.
        val two = listOf(trip, Vacation(d(12, 1), d(12, 3)))
        assertEquals(two, Vacation.decode(Vacation.encode(two)))
        assertEquals(listOf(trip), Vacation.decode(Vacation.encode(listOf(trip)) + ";junk;5"))
        assertEquals(emptyList<Vacation>(), Vacation.decode(""))
        assertEquals(5, trip.days)
    }

    @Test
    fun addingAndRemovingAVacationMovesWhatIsLeft() {
        val today = d(11, 19)
        val dates = daily.datesFrom(d(11, 17), 15) // 11/17 to 12/1, one a day
        val done = dates.map { it < today }
        val trip = Vacation(d(11, 25), d(11, 27))
        val withTrip = daily.copy(off = listOf(trip))
        // A vacation ahead: the row of 11/25 moves to 11/28, and the rows before it stay.
        val (first, start) = Missed.afterVacation(dates, done, trip.start, today, withTrip, pullBack = false)!!
        assertEquals(dates.indexOf(d(11, 25)), first)
        assertEquals(d(11, 28), start)
        val moved = Missed.shift(dates, first, start, withTrip)
        assertEquals(dates.take(first), moved.take(first))
        assertTrue(moved.none { it in trip })
        // Taking it off brings them back to 11/25.
        val (back, backStart) = Missed.afterVacation(moved, done, trip.start, today, daily, pullBack = true)!!
        assertEquals(first, back)
        assertEquals(dates, Missed.shift(moved, back, backStart, daily))
        // A past vacation over days that already have no sessions moves nothing.
        val shifted = listOf(d(11, 6)) + daily.datesFrom(d(11, 12), 10)
        val shiftedDone = shifted.map { it <= d(11, 18) }
        assertNull(Missed.afterVacation(shifted, shiftedDone, d(11, 7), today, daily.copy(off = listOf(Vacation(d(11, 7), d(11, 11)))), pullBack = false))
        // A past vacation over missed lessons: they start again today.
        val behind = daily.datesFrom(d(11, 10), 12)
        val behindDone = behind.map { it < d(11, 12) }
        val (late, lateStart) = Missed.afterVacation(behind, behindDone, d(11, 12), today, daily.copy(off = listOf(Vacation(d(11, 12), d(11, 16)))), pullBack = false)!!
        assertEquals(2, late)
        assertEquals(today, lateStart)
    }

    @Test
    fun aVacationKeepsTheStreak() {
        val lessons = List(12) { lesson }
        val dates = listOf(d(11, 6)) + daily.datesFrom(d(11, 12), 11)
        val rows = lessons.indices.map { StatRow(dates[it], lessons[it], done = dates[it] <= d(11, 17)) }
        assertEquals(6, Stats.compute(rows, d(11, 17), daily).currentStreak)
        val rested = daily.copy(off = listOf(Vacation(d(11, 7), d(11, 11))))
        assertEquals(7, Stats.compute(rows, d(11, 17), rested).currentStreak)
        assertEquals(7, Stats.compute(rows, d(11, 17), rested).bestStreak)
    }

    @Test
    fun theCalendarOfDays() {
        val today = d(11, 19)
        val cal = daily.copy(off = listOf(Vacation(d(11, 8), d(11, 9))))
        val rows = listOf(
            DayRow(d(11, 6), done = true, partly = true),
            DayRow(d(11, 7), done = false, partly = true),
            // 11/8 and 11/9: vacation. 11/10 and 11/11: no session (moved on).
            DayRow(d(11, 12), done = false, partly = false),
            DayRow(d(11, 13), done = true, partly = true),
            DayRow(today, done = false, partly = false),
            DayRow(d(11, 20), done = false, partly = false),
        )
        val days = Consistency.days(rows, cal, today)
        assertEquals(DayState.DONE, days[d(11, 6)])
        assertEquals(DayState.PARTIAL, days[d(11, 7)])
        assertEquals(DayState.VACATION, days[d(11, 8)])
        assertEquals(DayState.VACATION, days[d(11, 9)])
        assertEquals(DayState.MISSED, days[d(11, 10)])
        assertEquals(DayState.MISSED, days[d(11, 11)])
        assertEquals(DayState.MISSED, days[d(11, 12)])
        assertEquals(DayState.MISSED, days[d(11, 14)])
        assertEquals(DayState.MISSED, days[d(11, 18)])
        assertEquals(DayState.TODAY, days[today])
        assertEquals(DayState.UPCOMING, days[d(11, 20)])
        assertNull(days[d(11, 5)])
        val (done, due) = Consistency.tally(days, YearMonth.of(2026, 11), today)
        assertEquals(2, done)
        assertEquals(2 + 1 + 8 + 1, due)
        // Two plans: each day as the worse one has it.
        val other = mapOf(d(11, 6) to DayState.MISSED, d(11, 10) to DayState.DONE)
        val both = Consistency.combine(listOf(days, other))
        assertEquals(DayState.MISSED, both[d(11, 6)])
        assertEquals(DayState.MISSED, both[d(11, 10)])
    }

    @Test
    fun perfectWeeksAndMonths() {
        // Sunday 11/1 to Saturday 11/7 all done; the next week has a missed day.
        val states = HashMap<LocalDate, DayState>()
        for (day in 1..7) states[d(11, day)] = DayState.DONE
        states[d(11, 10)] = DayState.MISSED
        states[d(11, 12)] = DayState.DONE
        assertEquals(1, Consistency.perfectWeeks(states, d(11, 20)))
        assertEquals(0, Consistency.perfectWeeks(states, d(11, 7)))
        val october = HashMap<LocalDate, DayState>()
        for (day in listOf(4, 6, 8, 11, 13)) october[d(10, day)] = DayState.DONE
        assertEquals(1, Consistency.perfectMonths(october, d(11, 2)))
        assertEquals(0, Consistency.perfectMonths(october, d(10, 30)))
    }

    @Test
    fun aJuzMemorizedWhole() {
        val juz30 = Quran.juzRange(30)
        val parts = juz30.chunked(60).map { it.first()..it.last() }
        val rows = parts.mapIndexed { i, r -> StatRow(d(11, 1).plusDays(i.toLong()), r, done = true) }
        assertEquals(listOf(30), Achievements.juzDone(rows).map { it.juz })
        assertEquals(rows.last().date, Achievements.juzDone(rows).single().date)
        val missingOne = rows.mapIndexed { i, r -> if (i == 2) r.copy(done = false) else r }
        assertTrue(Achievements.juzDone(missingOne).isEmpty())
        assertEquals(juz30.count(), Achievements.covered(rows).count { it })
        assertEquals(listOf(juz30), Achievements.ranges(Achievements.covered(rows)))
    }

    @Test
    fun badgesAndTheirProgress() {
        val input = BadgeInput(sessionsDone = 12, bestStreak = 5, juz = 1, perfectWeeks = 0, perfectMonths = 0)
        assertTrue(Badge.FIRST.earned(input))
        assertTrue(Badge.SESSIONS_10.earned(input))
        assertFalse(Badge.SESSIONS_50.earned(input))
        assertEquals(5, Badge.STREAK_7.progress(input))
        assertTrue(Badge.JUZ_1.earned(input))
        assertEquals(1, Badge.JUZ_5.progress(input))
        assertEquals(Badge.STREAK_30, Badge.fromName("STREAK_30"))
        // From plans: sessions, the best streak and the perfect weeks.
        val dates = daily.datesFrom(d(11, 1), 14)
        val plan = PlanFacts(
            planId = 1,
            name = "خطتي",
            memorizes = true,
            stats = dates.map { StatRow(it, lesson, done = it <= d(11, 9)) },
            days = dates.map { DayRow(it, done = it <= d(11, 9), partly = it <= d(11, 9)) },
            calendar = daily,
            mistakes = emptyList(),
        )
        val got = Badges.input(listOf(plan), d(11, 10))
        assertEquals(9, got.sessionsDone)
        assertEquals(9, got.bestStreak)
        assertEquals(1, got.perfectWeeks)
    }

    @Test
    fun theOverviewCountsEachAyahOnce() {
        val surah = Quran.surahRange(103)
        fun plan(id: Long) = PlanFacts(
            planId = id,
            name = "خطة $id",
            memorizes = true,
            stats = listOf(StatRow(d(11, 18), surah, done = true)),
            days = listOf(DayRow(d(11, 18), done = true, partly = true), DayRow(d(11, 19), done = false, partly = false)),
            calendar = daily,
            mistakes = listOf(MistakeRow(id, "خطة $id", 0, d(11, 18), "العصر", mistakes = id.toInt() * 2, alerts = 1, items = 1)),
        )
        val o = Overviews.compute(listOf(plan(1), plan(2)), d(11, 19))
        assertEquals(surah.count(), o.ayahs)
        assertEquals(2, o.sessionsDone)
        assertEquals(DayStateTally(2, 2), o.last7[5].let { DayStateTally(it.due, it.done) })
        assertEquals(3.0, o.mistakesAverage!!, 1e-9)
        assertEquals(listOf(2L, 1L), o.topMistakes.map { it.planId })
        assertEquals(d(11, 18), o.firstDay)
    }

    private data class DayStateTally(val due: Int, val done: Int)

    @Test
    fun theWeeklyReport() {
        assertEquals(
            "خطتي: أنجزت 5 من 6، فاتك درس واحد، 8 متتالية",
            WeeklyReport.line(PlanWeek("خطتي", due = 6, done = 5, missed = 1, streak = 8, upcoming = 6)),
        )
        assertEquals(
            listOf("خطتي: أنجزت 3 من 3", "الأسبوع الجاي: 6 جلسات"),
            WeeklyReport.lines(listOf(PlanWeek("خطتي", 3, 3, 0, 1, 6), PlanWeek("ثانية", 0, 0, 0, 0, 0))),
        )
        assertTrue(WeeklyReport.lines(listOf(PlanWeek("ثانية", 0, 0, 0, 0, 0))).isEmpty())
        // The coming week: only days still to do; a day ticked ahead of time is not one.
        val today = d(11, 25)
        val days = listOf(
            DayRow(d(11, 24), done = true, partly = false),
            DayRow(d(11, 26), done = true, partly = false),
            DayRow(d(11, 29), done = false, partly = true),
            DayRow(d(12, 1), done = false, partly = false),
            DayRow(d(12, 3), done = false, partly = false),
        )
        val facts = PlanFacts(1, "خطتي", true, emptyList(), days, sunTueThu, emptyList())
        assertEquals(2, WeeklyReport.week(facts, today).upcoming)
    }

    @Test
    fun tickingAWholeSessionAndTheNewFieldsInABackup() {
        val settings = PlanSettings(startDate = d(11, 1), vacations = listOf(Vacation(d(11, 8), d(11, 10))))
        val plan = settings.toEntity(1, 0, PlanTemplate.MY_TABLE)
        assertEquals(settings.vacations, plan.toSettings().vacations)
        val rows = PlanBuilder.build(settings).mapIndexed { i, r -> r.toEntity(1, i) }
        assertTrue(rows.none { r -> settings.vacations.any { r.localDate in it } })
        val second = rows[1]
        val ticked = second.withAllDone(plan)
        assertTrue(ticked.newDone && ticked.minorDone)
        // A major review written by hand, not written yet, stays as it is.
        assertFalse(ticked.majorDone)
        val counted = ticked.withCounts(Item.NEW, 2, 1).withCounts(Item.MINOR, 0, null)
        assertEquals(2, counted.totalMistakes)
        val back = Backup.read(Backup.write(listOf(PlanBackup(plan, listOf(counted))), 0)) as Backup.Result.Plans
        assertEquals(plan.vacations, back.plans.single().plan.vacations)
        val row = back.plans.single().sessions.single()
        assertEquals(2, row.newMistakes)
        assertEquals(1, row.newAlerts)
        assertEquals(0, row.minorMistakes)
        assertNull(row.minorAlerts)
        val facts = planFacts(plan, listOf(counted))
        assertEquals(1, facts.mistakes.size)
        assertEquals(2, facts.mistakes.single().items)
    }
}
