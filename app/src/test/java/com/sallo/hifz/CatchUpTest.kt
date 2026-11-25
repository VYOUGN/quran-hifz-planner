package com.sallo.hifz

import com.sallo.hifz.data.catchUpOptions
import com.sallo.hifz.data.rescheduleSeparately
import com.sallo.hifz.data.toEntity
import com.sallo.hifz.plan.MemUnit
import com.sallo.hifz.plan.Missed
import com.sallo.hifz.plan.PlanBuilder
import com.sallo.hifz.plan.PlanSettings
import com.sallo.hifz.plan.PlanTemplate
import com.sallo.hifz.plan.PortionSpec
import com.sallo.hifz.plan.RangeSpec
import com.sallo.hifz.plan.RangeType
import com.sallo.hifz.plan.ScheduleMode
import com.sallo.hifz.plan.StudyCalendar
import com.sallo.hifz.plan.TrackSchedule
import com.sallo.hifz.quran.RangeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class CatchUpTest {
    /** The paper plan: Al-Asr to An-Nas, Sun/Tue/Thu from Sunday 1 November 2026. */
    private val paper = PlanSettings(
        startDate = LocalDate.of(2026, 11, 1),
        newPortion = PortionSpec(MemUnit.AYAH, 10.0, RangeSpec(RangeType.AYAHS, 103, 114, 1, 6)),
    )
    private val dates = PlanBuilder.build(paper).map { it.date }
    private fun day(d: Int) = LocalDate.of(2026, 11, d)

    @Test
    fun aSessionEveryDayUntilBackOnThePlan() {
        // Sunday's and Tuesday's lessons were missed; today is Wednesday the 4th.
        val moved = Missed.daily(dates, 0, day(4))
        // Wednesday, Thursday and Friday take the three lessons up to Thursday's; Sunday the 8th is the plan's own again.
        assertEquals(listOf(day(4), day(5), day(6), day(8), day(10)), moved.take(5))
        assertEquals(dates.drop(3), moved.drop(3))
        assertEquals(day(6), Missed.dailyUntil(dates, 0, day(4)))
        // With a lesson every day there is no day to spare: nothing is gained over shifting.
        val everyDay = StudyCalendar(ScheduleMode.INTERVAL, emptySet(), 1).dates(day(1), 10)
        assertNull(Missed.dailyUntil(everyDay, 0, day(3)))
    }

    @Test
    fun shiftingFromAChosenDay() {
        val calendar = paper.calendar
        val moved = Missed.shift(dates, 1, day(8), calendar)
        assertEquals(day(1), moved[0])
        assertEquals(listOf(day(8), day(10), day(12)), moved.subList(1, 4))
        // The same as the old «ترحيل» when it starts on the next study day.
        assertEquals(Missed.reschedule(dates, List(dates.size) { it == 0 }, day(6), calendar), Missed.shift(dates, 1, day(8), calendar))
    }

    @Test
    fun choicesForAPlanWithMissedLessons() {
        val plan = paper.toEntity(1, 0, PlanTemplate.MY_TABLE)
        val rows = PlanBuilder.build(paper).mapIndexed { i, d -> d.toEntity(1, i) }.toMutableList()
        rows[0] = rows[0].copy(newDone = true)
        val options = catchUpOptions(plan, rows, day(6))!!
        // Tuesday and Thursday were missed; Friday the 6th is not a study day, so shifting starts on Sunday.
        assertEquals(listOf(day(3), day(5)), options.missed.map { it.localDate })
        assertEquals(day(8), options.shiftStart)
        assertEquals(rows.last().localDate, options.end)
        assertTrue(options.shiftEnd(day(8)) > options.end)
        // Every day from Friday: Friday, Saturday, then Sunday's own lesson is back on its day.
        assertEquals(day(7), options.dailyUntil)
        // Nothing missed: no choices.
        assertNull(catchUpOptions(plan, rows, day(3)))
    }

    @Test
    fun catchingUpDailyWithReviewsOnTheirOwnDays() {
        val own = paper.withSeparateSchedules(true).copy(
            minorSchedule = TrackSchedule(ScheduleMode.WEEKDAYS, DayOfWeek.entries.toSet()),
            majorSchedule = TrackSchedule(ScheduleMode.WEEKDAYS, setOf(DayOfWeek.MONDAY)),
        )
        val rows = PlanBuilder.build(own).mapIndexed { i, d -> d.toEntity(1, i) }
        val lessonDates = rows.filter { it.hasLesson }.map { it.localDate }
        // Nothing done; today is Wednesday the 4th: Sunday's and Tuesday's lessons were missed.
        val moved = Missed.daily(lessonDates, 0, day(4))
        val r = rescheduleSeparately(own, rows, day(4), moved)!!
        val lessons = r.added.filter { it.lesson != null }
        assertEquals(listOf(day(4), day(5), day(6), day(8)), lessons.take(4).map { it.date })
        assertEquals("العصر", RangeFormatter.format(lessons.first().lesson!!))
        // The reviews keep their own days from today on: a minor review every day, the major one on Mondays.
        assertTrue(r.added.all { it.date >= day(4) })
        assertEquals(r.added.size, r.added.map { it.date }.toSet().size)
    }
}
