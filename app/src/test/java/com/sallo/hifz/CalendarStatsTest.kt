package com.sallo.hifz

import com.sallo.hifz.dates.DateText
import com.sallo.hifz.dates.HijriDate
import com.sallo.hifz.plan.LessonGenerator
import com.sallo.hifz.plan.Missed
import com.sallo.hifz.plan.PlanSettings
import com.sallo.hifz.plan.ScheduleMode
import com.sallo.hifz.plan.StatRow
import com.sallo.hifz.plan.Stats
import com.sallo.hifz.plan.StudyCalendar
import com.sallo.hifz.quran.Quran
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

class CalendarStatsTest {
    private val sunTueThu = StudyCalendar(ScheduleMode.WEEKDAYS, PlanSettings.DEFAULT_DAYS, 2)
    private fun d(month: Int, day: Int) = LocalDate.of(2026, month, day)

    @Test
    fun weekdayAndIntervalDates() {
        assertEquals(listOf(d(11, 1), d(11, 3), d(11, 5), d(11, 8)), sunTueThu.dates(d(11, 1), 4))
        assertEquals(d(11, 3), sunTueThu.firstOnOrAfter(d(11, 2)))
        val everyOtherDay = StudyCalendar(ScheduleMode.INTERVAL, emptySet(), 2)
        assertEquals(listOf(d(11, 1), d(11, 3), d(11, 5), d(11, 7)), everyOtherDay.dates(d(11, 1), 4))
    }

    @Test
    fun missedLessonsMoveForwardWithoutStacking() {
        val dates = sunTueThu.dates(d(11, 1), 6)
        val done = listOf(true, false, false, false, false, false)
        val friday = d(11, 6)
        assertEquals(1, Missed.firstMissed(dates, done, friday))
        assertEquals(2, Missed.count(dates, done, friday))
        val moved = Missed.reschedule(dates, done, friday, sunTueThu)!!
        assertEquals(d(11, 1), moved[0])
        assertEquals(listOf(d(11, 8), d(11, 10), d(11, 12), d(11, 15), d(11, 17)), moved.drop(1))
        assertNull(Missed.firstMissed(dates, done, d(11, 3)))
        assertNull(Missed.reschedule(dates, List(6) { true }, friday, sunTueThu))
    }

    @Test
    fun postponeFromARow() {
        val dates = sunTueThu.dates(d(11, 1), 4)
        assertEquals(listOf(d(11, 1), d(11, 4), d(11, 5), d(11, 8)), Missed.postpone(dates, 1, d(11, 4), sunTueThu))
    }

    @Test
    fun streaksTotalsAndFinishDates() {
        val lessons = LessonGenerator.byAyahs(103, 1, 114, 6, 10)
        val dates = sunTueThu.dates(d(11, 1), lessons.size)
        val done = listOf(true, true, false, true, true, true) + List(6) { false }
        val rows = lessons.indices.map { StatRow(dates[it], lessons[it], done[it]) }
        val today = dates[6]
        val stats = Stats.compute(rows, today)
        assertEquals(12, stats.total)
        assertEquals(5, stats.done)
        assertEquals(3, stats.currentStreak)
        assertEquals(3, stats.bestStreak)
        val doneLessons = lessons.filterIndexed { i, _ -> done[i] }
        assertEquals(doneLessons.sumOf { it.count() }, stats.ayahs)
        assertEquals(doneLessons.sumOf { Quran.letters(it) }, stats.letters)
        assertEquals(dates.last(), stats.scheduledFinish)
        assertNotNull(stats.paceFinish)
        assertTrue(stats.paceFinish!! > today)
        // Sessions on the plan's own days: the calendar changes nothing.
        assertEquals(3, Stats.compute(rows, today, sunTueThu).currentStreak)
        assertEquals(3, Stats.compute(rows, today, sunTueThu).bestStreak)
    }

    @Test
    fun aStudyDayWithNoSessionBreaksTheStreak() {
        val daily = StudyCalendar(ScheduleMode.INTERVAL, emptySet(), 1)
        val lessons = LessonGenerator.byAyahs(103, 1, 114, 6, 10)
        // Done on 11/6, then nothing until the lessons were moved on («ترحيل») to start again on 11/12.
        val dates = listOf(d(11, 6)) + daily.datesFrom(d(11, 12), lessons.size - 1)
        val rows = lessons.indices.map { StatRow(dates[it], lessons[it], done = dates[it] <= d(11, 17)) }
        val stats = Stats.compute(rows, d(11, 17), daily)
        assertEquals(6, stats.currentStreak)
        assertEquals(6, stats.bestStreak)
        // Without the calendar the gap goes unseen, as before.
        assertEquals(7, Stats.compute(rows, d(11, 17)).currentStreak)

        // Five days done, then study days with nothing on them up to today: the streak is gone.
        val later = daily.datesFrom(d(11, 1), 5) + daily.datesFrom(d(11, 12), lessons.size - 5)
        val stopped = lessons.indices.map { StatRow(later[it], lessons[it], done = it < 5) }
        val now = Stats.compute(stopped, d(11, 10), daily)
        assertEquals(0, now.currentStreak)
        assertEquals(5, now.bestStreak)
        // The day after the last session is no gap yet.
        assertEquals(5, Stats.compute(stopped, d(11, 6), daily).currentStreak)
    }

    @Test
    fun paperStyleDateText() {
        assertEquals("1‏/‏11", DateText.short(d(11, 1)))
        assertEquals("الأحد", DateText.weekday(d(11, 1)))
        assertEquals("الخميس 5 نوفمبر 2026", DateText.long(d(11, 5)))
        assertEquals("21 جمادى الأولى 1448 هـ", HijriDate(1448, 5, 21).long)
    }

    @Test
    fun hijriReferenceFromTheJdk() {
        // Reference for the on-device check: the JDK's Hijrah calendar is Umm al-Qura.
        val h = HijrahDate.from(d(11, 1))
        println("JDK Umm al-Qura for 2026-11-01: ${h.get(ChronoField.DAY_OF_MONTH)}/${h.get(ChronoField.MONTH_OF_YEAR)}/${h.get(ChronoField.YEAR)}")
        assertEquals(1448, h.get(ChronoField.YEAR))
        assertEquals(5, h.get(ChronoField.MONTH_OF_YEAR))
    }
}
