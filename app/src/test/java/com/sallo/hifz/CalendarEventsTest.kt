package com.sallo.hifz

import com.sallo.hifz.calendar.CalendarEvents
import com.sallo.hifz.calendar.CalendarStyle
import com.sallo.hifz.data.toEntity
import com.sallo.hifz.plan.MemUnit
import com.sallo.hifz.plan.MinorMode
import com.sallo.hifz.plan.PlanBuilder
import com.sallo.hifz.plan.PlanSettings
import com.sallo.hifz.plan.PlanTemplate
import com.sallo.hifz.plan.PortionSpec
import com.sallo.hifz.plan.RangeSpec
import com.sallo.hifz.plan.RangeType
import com.sallo.hifz.plan.ScheduleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class CalendarEventsTest {
    private val paper = PlanSettings(
        name = "محمد",
        startDate = LocalDate.of(2026, 11, 1),
        newPortion = PortionSpec(MemUnit.AYAH, 10.0, RangeSpec(RangeType.AYAHS, 103, 114, 1, 6)),
        minorMode = MinorMode.LAST_N,
        minorCount = 5,
    )
    private val plan = paper.toEntity(1, 0, PlanTemplate.MY_TABLE)
    private val rows = PlanBuilder.build(paper).mapIndexed { i, d -> d.toEntity(1, i) }
    private val riyadh = ZoneId.of("Asia/Riyadh")

    @Test
    fun aDayPerSessionWithWhatItHolds() {
        val events = CalendarEvents.drafts(plan, rows, CalendarStyle(), riyadh)
        assertEquals(rows.size, events.size)
        val first = events[0]
        assertEquals("محمد: العصر", first.title)
        assertTrue(first.allDay)
        assertEquals("UTC", first.timeZone)
        assertEquals(LocalDate.of(2026, 11, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), first.start)
        assertEquals(24 * 60 * 60 * 1000L, first.end - first.start)
        assertTrue(first.description, first.description.startsWith("○ الحفظ: العصر\n"))
        // The third session also reviews the two before it.
        assertTrue(events[2].description.contains("○ المراجعة الصغرى: العصر - الهمزة"))
        assertEquals(LocalDate.of(2026, 11, 5).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), events[2].start)
    }

    @Test
    fun ticksShowAndChangeTheEvent() {
        val before = CalendarEvents.drafts(plan, rows, CalendarStyle(), riyadh)[1]
        val partly = rows.toMutableList().also { it[1] = it[1].copy(newDone = true) }
        val half = CalendarEvents.drafts(plan, partly, CalendarStyle(), riyadh)[1]
        assertEquals("محمد: الهمزة", half.title)
        assertTrue(half.description.startsWith("✓ الحفظ: الهمزة"))
        assertNotEquals(before.hash, half.hash)
        // Everything on the day done: the title is ticked too.
        val all = partly.also { it[1] = it[1].copy(minorDone = true) }
        assertEquals("✓ محمد: الهمزة", CalendarEvents.drafts(plan, all, CalendarStyle(), riyadh)[1].title)
        // Nothing changed: the same hash, so the event is left alone.
        assertEquals(before.hash, CalendarEvents.drafts(plan, rows, CalendarStyle(), riyadh)[1].hash)
    }

    @Test
    fun timedSessions() {
        val style = CalendarStyle(allDay = false, minuteOfDay = 17 * 60 + 30, durationMinutes = 45, alertMinutes = 10)
        val e = CalendarEvents.drafts(plan, rows, style, riyadh)[0]
        assertEquals(ZonedDateTime.of(2026, 11, 1, 17, 30, 0, 0, riyadh).toInstant().toEpochMilli(), e.start)
        assertEquals(45 * 60 * 1000L, e.end - e.start)
        assertEquals("Asia/Riyadh", e.timeZone)
        assertEquals(10, e.alertMinutes)
        // All-day events have no alert of their own.
        assertEquals(CalendarStyle.NO_ALERT, CalendarEvents.drafts(plan, rows, style.copy(allDay = true), riyadh)[0].alertMinutes)
    }

    @Test
    fun noDatesNoEvents() {
        val anytime = paper.copy(scheduleMode = ScheduleMode.ANYTIME)
        val entity = anytime.toEntity(2, 0, PlanTemplate.MY_TABLE)
        val anyRows = PlanBuilder.build(anytime).mapIndexed { i, d -> d.toEntity(2, i) }
        assertTrue(CalendarEvents.drafts(entity, anyRows, CalendarStyle(), riyadh).isEmpty())
    }
}
