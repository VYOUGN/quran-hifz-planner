package com.sallo.hifz

import com.sallo.hifz.data.PlanEntity
import com.sallo.hifz.data.RepeatWay
import com.sallo.hifz.data.Repeats
import com.sallo.hifz.data.SessionEntity
import com.sallo.hifz.data.toEntity
import com.sallo.hifz.data.toSettings
import com.sallo.hifz.plan.PlanBuilder
import com.sallo.hifz.plan.PlanSettings
import com.sallo.hifz.plan.PlanTemplate
import com.sallo.hifz.plan.ScheduleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RepeatTest {
    private fun d(month: Int, day: Int) = LocalDate.of(2026, month, day)

    /** A plan from Sunday 1 November on its default days (Sunday, Tuesday, Thursday), with ids. */
    private fun plan(settings: PlanSettings = PlanSettings(startDate = d(11, 1))): Pair<PlanEntity, List<SessionEntity>> {
        val plan = settings.toEntity(1, 0, PlanTemplate.MY_TABLE)
        val rows = PlanBuilder.build(settings).mapIndexed { i, r -> r.toEntity(1, i).copy(id = i + 1L) }
        return plan to rows
    }

    @Test
    fun theNextSessionTakesTheLessonAndTheRestMoveOneSessionLater() {
        val (plan, built) = plan()
        val at = 3
        val today = built[at].localDate
        // Recited with many mistakes; a review written on a later day stays on that day.
        val rows = built.toMutableList().apply {
            set(at, get(at).copy(newDone = false, newGrade = 40, newMistakes = 7))
            set(5, get(5).copy(majorText = "النبأ"))
        }
        val opts = Repeats.options(plan, rows, at, today)!!
        assertNull(opts.blocked)
        assertEquals(rows[4].localDate, opts.nextDay)
        assertEquals(plan.toSettings().calendar.next(rows.last().localDate), opts.finishAfter)

        val out = Repeats.apply(plan, rows, at, RepeatWay.NEXT_SESSION, today)!!
        assertEquals(rows.size - at, out.size)
        // The repeat: the same lesson and minor review, on the next session's day, with nothing recorded.
        assertEquals(rows[at].lesson, out[0].lesson)
        assertEquals(rows[at].minorRanges, out[0].minorRanges)
        assertEquals(rows[4].date, out[0].date)
        assertEquals(rows[4].id, out[0].id)
        assertFalse(out[0].newDone)
        assertEquals(0, out[0].newGrade)
        assertNull(out[0].newMistakes)
        // Each lesson takes the place of the one after it; the days and the major reviews stay.
        for (k in 1 until out.size - 1) {
            assertEquals(rows[at + k].lesson, out[k].lesson)
            assertEquals(rows[at + k].minorRanges, out[k].minorRanges)
            assertEquals(rows[at + 1 + k].date, out[k].date)
        }
        assertEquals("النبأ", out[1].majorText)
        // The last lesson goes to a new session on the plan's next day.
        assertEquals(rows.last().lesson, out.last().lesson)
        assertEquals(opts.finishAfter, out.last().localDate)
        assertEquals(0L, out.last().id)
        assertEquals((at + 1 until rows.size + 1).toList(), out.map { it.idx })
    }

    @Test
    fun anExtraDayFitsBetweenSessionsWithoutMovingAnything() {
        val (plan, rows) = plan()
        // Sunday 1 November, and the next session on Tuesday: Monday is free.
        val opts = Repeats.options(plan, rows, 0, d(11, 1))!!
        assertEquals(d(11, 2), opts.extraDay)
        val out = Repeats.apply(plan, rows, 0, RepeatWay.EXTRA_DAY, d(11, 1))!!
        assertEquals(rows.size, out.size)
        assertEquals(d(11, 2), out[0].localDate)
        assertEquals(rows[0].lesson, out[0].lesson)
        assertNull(out[0].majorRange)
        for (k in 1 until out.size) {
            assertEquals(rows[k].copy(idx = k + 1), out[k])
        }

        // Every day a session: no free day, so only the next session is offered.
        val (daily, dailyRows) = plan(PlanSettings(startDate = d(11, 1), scheduleMode = ScheduleMode.INTERVAL, every = 1))
        val dailyOpts = Repeats.options(daily, dailyRows, 0, d(11, 1))!!
        assertNull(dailyOpts.extraDay)
        assertNull(Repeats.apply(daily, dailyRows, 0, RepeatWay.EXTRA_DAY, d(11, 1)))
        assertEquals(d(11, 2), dailyOpts.nextDay)
    }

    @Test
    fun recordedLessonsAfterItAndFutureSessionsAreLeftAlone() {
        val (plan, built) = plan()
        val rows = built.toMutableList().apply { set(6, get(6).copy(newDone = true)) }
        val today = rows[3].localDate
        val opts = Repeats.options(plan, rows, 3, today)!!
        assertNotNull(opts.blocked)
        assertNull(Repeats.apply(plan, rows, 3, RepeatWay.NEXT_SESSION, today))
        // A session still to come is not one to repeat.
        assertNull(Repeats.options(plan, rows, 4, today))
        assertTrue(Repeats.options(plan, rows, 2, today) != null)
    }
}
