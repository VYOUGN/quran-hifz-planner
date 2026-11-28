package com.sallo.hifz

import com.sallo.hifz.common.PlanText
import com.sallo.hifz.data.Item
import com.sallo.hifz.data.PlanEntity
import com.sallo.hifz.data.RepeatWay
import com.sallo.hifz.data.Repeats
import com.sallo.hifz.data.SessionEntity
import com.sallo.hifz.data.hasMain
import com.sallo.hifz.data.toEntity
import com.sallo.hifz.data.toSettings
import com.sallo.hifz.data.withMainDates
import com.sallo.hifz.plan.MajorMode
import com.sallo.hifz.plan.MinorMode
import com.sallo.hifz.plan.Missed
import com.sallo.hifz.plan.PlanBuilder
import com.sallo.hifz.plan.PlanSettings
import com.sallo.hifz.plan.PlanTemplate
import com.sallo.hifz.plan.ScheduleMode
import com.sallo.hifz.plan.Tracks
import com.sallo.hifz.plan.Vacation
import com.sallo.hifz.quran.Ranges
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

    private fun List<SessionEntity>.options(plan: PlanEntity, at: Int, item: Item, today: LocalDate = this[at].localDate) =
        Repeats.options(plan, this, at, today).first { it.item == item }

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
        val opts = rows.options(plan, at, Item.NEW)
        assertTrue(opts.main)
        assertNull(opts.blocked)
        assertEquals(rows[4].localDate, opts.nextDay)
        assertEquals(plan.toSettings().calendar.next(rows.last().localDate), opts.finishAfter)

        val out = Repeats.apply(plan, rows, at, Item.NEW, RepeatWay.NEXT_SESSION, today)!!
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
        // Repeated: the next session has it again.
        assertTrue((rows.take(at + 1) + out).options(plan, at, Item.NEW).repeated)
    }

    @Test
    fun anExtraDayFitsBetweenSessionsWithoutMovingAnything() {
        val (plan, rows) = plan()
        // Sunday 1 November, and the next session on Tuesday: Monday is free.
        val opts = rows.options(plan, 0, Item.NEW)
        assertEquals(d(11, 2), opts.extraDay)
        assertFalse(opts.extraJoins)
        val out = Repeats.apply(plan, rows, 0, Item.NEW, RepeatWay.EXTRA_DAY, d(11, 1))!!
        assertEquals(rows.size, out.size)
        assertEquals(d(11, 2), out[0].localDate)
        assertEquals(rows[0].lesson, out[0].lesson)
        assertNull(out[0].majorRange)
        assertFalse(out[0].has(Item.MAJOR))
        for (k in 1 until out.size) {
            assertEquals(rows[k].copy(idx = k + 1), out[k])
        }

        // Every day a session: no free day, so only the next session is offered.
        val (daily, dailyRows) = plan(PlanSettings(startDate = d(11, 1), scheduleMode = ScheduleMode.INTERVAL, every = 1))
        val dailyOpts = dailyRows.options(daily, 0, Item.NEW)
        assertNull(dailyOpts.extraDay)
        assertNull(Repeats.apply(daily, dailyRows, 0, Item.NEW, RepeatWay.EXTRA_DAY, d(11, 1)))
        assertEquals(d(11, 2), dailyOpts.nextDay)
    }

    @Test
    fun recordedLessonsAfterItAndFutureSessionsAreLeftAlone() {
        val (plan, built) = plan()
        val rows = built.toMutableList().apply { set(6, get(6).copy(newDone = true)) }
        val today = rows[3].localDate
        val opts = rows.options(plan, 3, Item.NEW)
        assertNotNull(opts.blocked)
        assertNull(Repeats.apply(plan, rows, 3, Item.NEW, RepeatWay.NEXT_SESSION, today))
        // A session still to come is not one to repeat.
        assertTrue(Repeats.options(plan, rows, 4, today).isEmpty())
        assertTrue(Repeats.options(plan, rows, 2, today).isNotEmpty())
    }

    @Test
    fun aMajorReviewTakesTheNextOnesPlaceAndTheLessonsStay() {
        val (plan, built) = plan(PlanSettings(startDate = d(11, 1), majorMode = MajorMode.RANGE))
        val at = 3
        val today = built[at].localDate
        val rows = built.toMutableList().apply { set(at, get(at).copy(majorMistakes = 6)) }
        assertEquals(listOf(Item.NEW, Item.MINOR, Item.MAJOR), Repeats.options(plan, rows, at, today).map { it.item })
        val opts = rows.options(plan, at, Item.MAJOR)
        assertFalse(opts.main)
        assertNull(opts.blocked)
        assertFalse(opts.repeated)
        assertEquals(rows[4].localDate, opts.nextDay)
        assertNull(opts.finishAfter)

        val out = Repeats.apply(plan, rows, at, Item.MAJOR, RepeatWay.NEXT_SESSION, today)!!
        // Nothing is added: the last generated review goes round again after the plan.
        assertEquals(rows.size - at - 1, out.size)
        assertEquals(rows[at].majorRange, out[0].majorRange)
        assertNull(out[0].majorMistakes)
        for (k in 1 until out.size) assertEquals(rows[at + k].majorRange, out[k].majorRange)
        // The lessons and minor reviews keep their days.
        for (k in out.indices) {
            val was = rows[at + 1 + k]
            assertEquals(was.copy(majorStart = out[k].majorStart, majorEnd = out[k].majorEnd, majorLead = out[k].majorLead), out[k])
        }
        assertTrue((rows.take(at + 1) + out).options(plan, at, Item.MAJOR).repeated)

        // A ticked review after it stays where it is.
        val ticked = rows.toMutableList().apply { set(8, get(8).copy(majorDone = true)) }
        assertNotNull(ticked.options(plan, at, Item.MAJOR).blocked)

        // In a plan of reviews only, the review is the main item: the last one gets a session of its own.
        val (reviews, reviewRows) = plan(PlanSettings(startDate = d(11, 1), includeNew = false, majorMode = MajorMode.RANGE))
        val main = Repeats.options(reviews, reviewRows, 2, reviewRows[2].localDate).single()
        assertTrue(main.main)
        assertEquals("المراجعة", main.noun)
        assertEquals(reviewRows.size - 2, Repeats.apply(reviews, reviewRows, 2, Item.MAJOR, RepeatWay.NEXT_SESSION, reviewRows[2].localDate)!!.size)
    }

    @Test
    fun aMajorReviewWrittenByHandMovesAndTheLastOneWrittenKeepsASession() {
        val (plan, built) = plan()
        val at = 2
        val today = built[at].localDate
        val last = built.lastIndex
        val rows = built.toMutableList().apply {
            set(at, get(at).copy(majorText = "الرحمن - الواقعة", majorGrade = 40))
            set(last, get(last).copy(majorText = "النبأ"))
        }
        assertNull(rows.options(plan, at, Item.MAJOR).blocked)
        val out = Repeats.apply(plan, rows, at, Item.MAJOR, RepeatWay.NEXT_SESSION, today)!!
        assertEquals("الرحمن - الواقعة", out[0].majorText)
        assertEquals(0, out[0].majorGrade)
        assertNull(out[1].majorText)
        // The plan's last review moves past its last day, to a session of its own.
        assertEquals(rows.size - at, out.size)
        val tail = out.last()
        assertEquals("النبأ", tail.majorText)
        assertEquals(Tracks.MAJOR, tail.tracks)
        assertFalse(tail.hasLesson)
        assertEquals(plan.toSettings().calendar.next(rows.last().localDate), tail.localDate)
        assertNull(out[out.size - 2].majorText)
        assertEquals(rows.last().lesson, out[out.size - 2].lesson)
    }

    @Test
    fun aMinorReviewIsAddedToTheNextOne() {
        val (plan, rows) = plan()
        val at = 6
        val today = rows[at].localDate
        val opts = rows.options(plan, at, Item.MINOR)
        assertNull(opts.blocked)
        assertFalse(opts.repeated)
        assertEquals(rows[at + 1].localDate, opts.nextDay)
        val out = Repeats.apply(plan, rows, at, Item.MINOR, RepeatWay.NEXT_SESSION, today)!!
        assertEquals(rows.size - at - 1, out.size)
        val merged = Ranges.merge(Ranges.decode(rows[at].minorRanges) + Ranges.decode(rows[at + 1].minorRanges))
        assertEquals(Ranges.encode(merged), out[0].minorRanges)
        assertTrue(Ranges.subtract(Ranges.decode(rows[at].minorRanges), merged).isEmpty())
        assertEquals(PlanText.minor(out[0]), opts.merged)
        // Its lesson and everything after stay.
        assertEquals(rows[at + 1].copy(minorRanges = out[0].minorRanges), out[0])
        for (k in 1 until out.size) assertEquals(rows[at + 1 + k], out[k])
        // Once added, the next session goes over it again.
        assertTrue((rows.take(at + 1) + out).options(plan, at, Item.MINOR).repeated)

        // A minor review that takes in the whole juz so far already has it next time.
        val (juz, juzRows) = plan(PlanSettings(startDate = d(11, 1), minorMode = MinorMode.CUMULATIVE_JUZ))
        val covered = juzRows.options(juz, 3, Item.MINOR)
        assertTrue(covered.repeated)
        assertNotNull(covered.blocked)
        assertNull(Repeats.apply(juz, juzRows, 3, Item.MINOR, RepeatWay.NEXT_SESSION, juzRows[3].localDate))
        assertEquals(d(11, 9), covered.extraDay)
    }

    @Test
    fun reviewsGetAnExtraDayOfTheirOwnAndShareIt() {
        val (plan, built) = plan()
        val at = 3 // Sunday 8 November
        val today = built[at].localDate
        val rows = built.toMutableList().apply { set(at, get(at).copy(majorText = "الملك")) }
        val minor = rows.options(plan, at, Item.MINOR)
        assertEquals(d(11, 9), minor.extraDay)
        assertFalse(minor.extraJoins)
        val out = Repeats.apply(plan, rows, at, Item.MINOR, RepeatWay.EXTRA_DAY, today)!!
        val extra = out[0]
        assertEquals(d(11, 9), extra.localDate)
        assertEquals(Tracks.MINOR, extra.tracks)
        assertEquals(rows[at].minorRanges, extra.minorRanges)
        assertFalse(extra.hasLesson)
        assertFalse(extra.hasMain(plan))
        for (k in 1 until out.size) assertEquals(rows[at + k].copy(idx = rows[at + k].idx + 1), out[k])

        // The major review on the same day joins it.
        val withExtra = rows.take(at + 1) + out
        val major = withExtra.options(plan, at, Item.MAJOR)
        assertEquals(d(11, 9), major.extraDay)
        assertTrue(major.extraJoins)
        val joined = Repeats.apply(plan, withExtra, at, Item.MAJOR, RepeatWay.EXTRA_DAY, today)!!
        assertEquals(withExtra.size - at - 1, joined.size)
        assertEquals(Tracks.MINOR or Tracks.MAJOR, joined[0].tracks)
        assertEquals("الملك", joined[0].majorText)
        assertEquals(extra.minorRanges, joined[0].minorRanges)
        // So does the lesson, with the minor review already there.
        val lesson = withExtra.options(plan, at, Item.NEW)
        assertTrue(lesson.extraJoins)

        // The extra session can be repeated too, and it has no lesson.
        val after = withExtra.take(at + 1) + joined
        assertEquals(listOf(Item.MINOR, Item.MAJOR), Repeats.options(plan, after, at + 1, d(11, 9)).map { it.item })
    }

    @Test
    fun lessonsMovingPastAnExtraReviewKeepTheTableInOrder() {
        val (plan, built) = plan()
        val at = 3
        val rows = built.take(at + 1) + Repeats.apply(plan, built, at, Item.MINOR, RepeatWay.EXTRA_DAY, built[at].localDate)!!
        // Sunday's lesson was not memorized: «ترحيل» on Tuesday moves it there, past Monday's extra review.
        val main = rows.filter { it.hasMain(plan) }
        val first = main.indexOfFirst { it.idx == at }
        val moved = Missed.shift(main.map { it.localDate }, first, d(11, 10), plan.toSettings().calendar)
        val out = withMainDates(plan, rows, moved, d(11, 10))
        assertEquals(rows.size, out.size)
        assertEquals(out.sortedBy { it.date }, out)
        assertEquals(out.indices.toList(), out.map { it.idx })
        val extra = out.single { !it.hasLesson }
        assertEquals(d(11, 9), extra.localDate)
        assertEquals(at, extra.idx)
        assertEquals(d(11, 10), out[at + 1].localDate)
        assertEquals(rows[at].lesson, out[at + 1].lesson)

        // A vacation over the extra day moves it out (nothing was recorded on it).
        val off = plan.copy(vacations = Vacation.encode(listOf(Vacation(d(11, 9), d(11, 9)))))
        val out2 = withMainDates(off, rows, main.map { it.localDate }, d(11, 8))
        assertEquals(d(11, 10), out2.single { !it.hasLesson }.localDate)
        assertEquals(out2.sortedBy { it.date }, out2)
    }
}
