package com.sallo.hifz

import com.sallo.hifz.data.Item
import com.sallo.hifz.data.rescheduleSeparately
import com.sallo.hifz.data.toEntity
import com.sallo.hifz.plan.LessonGenerator
import com.sallo.hifz.plan.TrackSchedule
import com.sallo.hifz.plan.Tracks
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit
import com.sallo.hifz.plan.MajorMode
import com.sallo.hifz.plan.MemUnit
import com.sallo.hifz.plan.MinorMode
import com.sallo.hifz.plan.MinorReview
import com.sallo.hifz.plan.PlanBuilder
import com.sallo.hifz.plan.PlanSettings
import com.sallo.hifz.plan.PlanTemplate
import com.sallo.hifz.plan.PortionSpec
import com.sallo.hifz.plan.RangeSpec
import com.sallo.hifz.plan.RangeType
import com.sallo.hifz.plan.Rounding
import com.sallo.hifz.plan.ScheduleMode
import com.sallo.hifz.quran.Quran
import com.sallo.hifz.quran.RangeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanBuilderTest {
    private val start = LocalDate.of(2026, 11, 1)
    private val paper = PlanSettings(
        startDate = start,
        newPortion = PortionSpec(MemUnit.AYAH, 10.0, RangeSpec(RangeType.AYAHS, 103, 114, 1, 6)),
        minorMode = MinorMode.LAST_N,
        minorCount = 5,
    )

    @Test
    fun reproducesThePaperTable() {
        val rows = PlanBuilder.build(paper)
        assertEquals(12, rows.size)
        assertEquals(
            listOf(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 8)),
            rows.take(4).map { it.date },
        )
        assertEquals("العصر", RangeFormatter.format(rows[0].lesson!!))
        assertEquals("—", RangeFormatter.formatAll(rows[0].minor))
        assertEquals("الهمزة", RangeFormatter.format(rows[1].lesson!!))
        assertEquals("العصر", RangeFormatter.formatAll(rows[1].minor))
        assertEquals("الفيل", RangeFormatter.format(rows[2].lesson!!))
        assertEquals("العصر - الهمزة", RangeFormatter.formatAll(rows[2].minor))
        assertTrue(rows.all { it.major == null })
    }

    @Test
    fun minorReviewIsTheLastFiveLessons() {
        val lessons = LessonGenerator.byAyahs(103, 1, 114, 6, 10)
        fun text(i: Int, count: Int = 5) = RangeFormatter.formatAll(MinorReview.window(lessons, i, MinorMode.LAST_N, count))
        assertEquals("—", text(0))
        assertEquals("العصر", text(1))
        assertEquals("العصر - الماعون", text(5))
        assertEquals("الهمزة - الكوثر", text(6))
        assertEquals("قريش - الكوثر", text(6, count = 3))
        assertEquals("—", text(6, count = 0))
    }

    @Test
    fun cumulativeReviewStartsOverEachJuz() {
        val pages = LessonGenerator.byPages(580, 584, 1)
        fun window(i: Int) = MinorReview.window(pages, i, MinorMode.CUMULATIVE_JUZ, 0)
        assertEquals(listOf(Quran.pageRange(580)), window(1))
        assertEquals(emptyList<IntRange>(), window(2))
        assertEquals(listOf(Quran.pageRange(582)), window(3))
        assertEquals(listOf(Quran.pageRange(582).first..Quran.pageRange(583).last), window(4))
    }

    @Test
    fun reviewOnlyPlan() {
        val review = PlanSettings(
            startDate = start,
            scheduleMode = ScheduleMode.INTERVAL,
            every = 2,
            includeNew = false,
            includeMinor = false,
            majorMode = MajorMode.RANGE,
            majorPortion = PortionSpec(MemUnit.PAGE, 5.0, RangeSpec(RangeType.SURAHS, 70, 114), Rounding.NEAREST),
        )
        assertNull(review.validate())
        val rows = PlanBuilder.build(review)
        assertEquals(review.majorPortion.generate(), rows.map { it.major })
        assertTrue(rows.all { it.lesson == null && it.minor.isEmpty() })
        assertEquals(LocalDate.of(2026, 11, 3), rows[1].date)
    }

    @Test
    fun majorReviewCyclesWhileMemorizing() {
        val combined = paper.copy(
            majorMode = MajorMode.RANGE,
            majorPortion = PortionSpec(MemUnit.JUZ, 1.0, RangeSpec(RangeType.JUZS, 28, 29)),
        )
        val rows = PlanBuilder.build(combined)
        assertEquals(12, rows.size)
        assertEquals(Quran.juzRange(28), rows[0].major)
        assertEquals(Quran.juzRange(29), rows[1].major)
        assertEquals(Quran.juzRange(28), rows[2].major)
        // Continuing after a kept row picks up the cycle where it stopped.
        val more = PlanBuilder.extend(rows.take(3).map { it.lesson!! }, rows[2].major, combined)
        assertEquals(Quran.juzRange(29), more.first().major)
    }

    @Test
    fun byEndDateFitsTheSessionsBeforeTheDate() {
        val byDate = paper.copy(
            scheduleMode = ScheduleMode.BY_DATE,
            endDate = LocalDate.of(2026, 11, 10),
            newPortion = PortionSpec(MemUnit.PAGE, 1.0, RangeSpec(RangeType.JUZ, 30), Rounding.NEAREST),
        )
        val limit = byDate.sessionLimit()!!
        // Sun/Tue/Thu from 11/1 to 11/10: 1, 3, 5, 8 and 10.
        assertEquals(5, limit)
        val rows = PlanBuilder.build(byDate)
        assertTrue(rows.size <= limit)
        assertTrue(rows.last().date <= LocalDate.of(2026, 11, 10))
    }

    @Test
    fun anytimePlansHaveNoDates() {
        val anytime = paper.copy(scheduleMode = ScheduleMode.ANYTIME)
        val rows = PlanBuilder.build(anytime)
        assertEquals(12, rows.size)
        assertTrue(rows.all { it.date == start })
    }

    @Test
    fun continuationAfterProgress() {
        val rows = PlanBuilder.build(paper)
        val continued = PlanBuilder.continuation(paper, rows[2].lesson, null)!!
        assertEquals(Quran.index(106, 1), continued.newPortion.resume)
        val more = PlanBuilder.extend(rows.take(3).map { it.lesson!! }, null, continued.copy(startDate = LocalDate.of(2026, 11, 10)))
        assertEquals("قريش", RangeFormatter.format(more[0].lesson!!))
        assertEquals("العصر - الفيل", RangeFormatter.format(more[0].minor.single()))
        assertNull(PlanBuilder.continuation(paper, rows.last().lesson, null))
    }

    @Test
    fun validation() {
        assertNull(paper.validate())
        assertEquals(
            "اختر الحفظ أو مراجعة كبرى تلقائية، عشان يتكوّن الجدول",
            paper.copy(includeNew = false, majorMode = MajorMode.MANUAL).validate(),
        )
        assertNotNull(paper.copy(scheduleMode = ScheduleMode.BY_DATE, endDate = null).validate())
    }

    @Test
    fun templates() {
        val review = PlanTemplate.REVIEW.applyTo(paper)
        assertEquals(false, review.includeNew)
        assertEquals(MajorMode.RANGE, review.majorMode)
        assertNull(review.validate())
        val forts = PlanTemplate.FIVE_FORTS.applyTo(paper)
        assertEquals(MemUnit.PAGE, forts.newPortion.unit)
        assertEquals(20, forts.minorCount)
        assertTrue(forts.prepEnabled)
        // The far review goes round everything memorized; the paper plan starts at Al-Asr.
        assertEquals(MajorMode.MEMORIZED, forts.majorMode)
        assertEquals("من الفاتحة إلى التكاثر", RangeSpec.covering(forts.priorMemorized!!).describe())
        assertEquals(MinorMode.CUMULATIVE_JUZ, PlanTemplate.CUMULATIVE.applyTo(paper).minorMode)
        val passages = PlanTemplate.PASSAGES.applyTo(paper)
        assertEquals(MemUnit.PASSAGE, passages.newPortion.unit)
        assertEquals(MajorMode.MEMORIZED, passages.majorMode)
        assertNull(passages.validate())
        // The paper range from Al-Asr to An-Nas, a passage per session.
        assertEquals("العصر", RangeFormatter.format(PlanBuilder.build(passages).first().lesson!!))
    }

    /** Backward from Al-Haqqah a surah per session, with Al-Ma'arij to An-Nas already memorized. */
    private val fromHaqqah = PlanSettings(
        startDate = start,
        newPortion = PortionSpec(MemUnit.SURAH, 1.0, RangeSpec(RangeType.SURAHS, 69, 58)),
        includeMinor = false,
        majorMode = MajorMode.MEMORIZED,
        majorPortion = PortionSpec(MemUnit.PAGE, 20.0, RangeSpec(), Rounding.NEAREST, completeSurah = true),
    ).withSuggestedPrior()

    @Test
    fun majorReviewGoesRoundWhatIsMemorized() {
        assertEquals(RangeSpec(RangeType.SURAHS, 70, 114), fromHaqqah.majorPortion.range)
        assertNull(fromHaqqah.validate())
        val rows = PlanBuilder.build(fromHaqqah)
        assertEquals(listOf("الحاقة", "القلم", "الملك"), rows.take(3).map { RangeFormatter.format(it.lesson!!) })
        // The first round reviews Al-Ma'arij to An-Nas.
        assertEquals(Quran.surahRange(70).first..Quran.surahRange(114).last, rows[0].major!!.first..rows[1].major!!.last)
        assertEquals(rows[0].major!!.last + 1, rows[1].major!!.first)
        // When it ends Al-Mulk is being memorized, so the next round starts at Al-Qalam.
        assertEquals(Quran.index(68, 1), rows[2].major!!.first)
        // With a minor review of the last lesson (Al-Qalam), it starts before that, at Al-Haqqah.
        val withMinor = PlanBuilder.build(fromHaqqah.copy(includeMinor = true, minorCount = 1))
        assertEquals("القلم", RangeFormatter.formatAll(withMinor[2].minor))
        assertEquals(Quran.index(69, 1), withMinor[2].major!!.first)
    }

    @Test
    fun eachRoundCoversWhatIsMemorizedOutsideTheMinorReview() {
        val s = fromHaqqah.copy(
            newPortion = PortionSpec(MemUnit.PAGE, 1.0, RangeSpec(RangeType.SURAHS, 69, 58), Rounding.NEAREST),
            includeMinor = true,
            minorCount = 5,
            majorPortion = fromHaqqah.majorPortion.copy(amount = 5.0),
        )
        val rows = PlanBuilder.build(s)
        val lessons = rows.map { it.lesson!! }
        // A round starts where the review goes back to an earlier place in the mushaf.
        val starts = listOf(0) + (1 until rows.size).filter { rows[it].major!!.first < rows[it - 1].major!!.first }
        assertTrue("rounds: $starts", starts.size >= 3)
        for ((r, from) in starts.withIndex()) {
            val until = starts.getOrElse(r + 1) { rows.size }
            if (until == rows.size) break
            val round = rows.subList(from, until).map { it.major!! }
            // Each round is gapless, and runs from just before the minor review to An-Nas.
            assertEquals(listOf(round.first().first..round.last().last), com.sallo.hifz.quran.Ranges.merge(round))
            assertEquals(round.sumOf { it.count() }, (round.first().first..round.last().last).count())
            assertEquals(Quran.AYAH_COUNT - 1, round.last().last)
            val expectedStart = if (from <= 5) Quran.index(70, 1) else lessons[from - 6].first
            assertEquals("round $r at $from", expectedStart, round.first().first)
            // The minor review never repeats inside the round.
            rows.subList(from, until).forEach { row ->
                row.minor.forEach { m -> assertTrue(round.none { it.first <= m.last && m.first <= it.last }) }
            }
        }
    }

    @Test
    fun suggestedPriorMemorization() {
        fun prior(range: RangeSpec) = paper.copy(newPortion = paper.newPortion.copy(range = range)).suggestedPrior()?.describe()
        assertEquals("من المعارج إلى الناس", prior(RangeSpec(RangeType.SURAHS, 69, 1)))
        assertEquals("سورة الفاتحة", prior(RangeSpec(RangeType.SURAHS, 2, 114)))
        assertNull(prior(RangeSpec(RangeType.WHOLE)))
        assertNull(prior(RangeSpec(RangeType.SURAHS, 114, 78)))
        assertEquals("من الفاتحة إلى البقرة 5", prior(RangeSpec(RangeType.AYAHS, 2, 2, 6, 20)))
        // The suggestion follows the new memorization until it is changed by hand.
        val s = fromHaqqah.withNewPortion(fromHaqqah.newPortion.copy(range = RangeSpec(RangeType.SURAHS, 77, 58)))
        assertEquals(RangeSpec(RangeType.SURAHS, 78, 114), s.majorPortion.range)
        val custom = fromHaqqah.copy(majorPortion = fromHaqqah.majorPortion.copy(range = RangeSpec(RangeType.JUZ, 30, 30)))
        assertEquals(RangeSpec(RangeType.JUZ, 30, 30), custom.withNewPortion(s.newPortion).majorPortion.range)
    }

    /** The paper plan with a daily minor review and the major review on Mondays and Wednesdays. */
    private val ownDays = paper.withSeparateSchedules(true).copy(
        minorSchedule = TrackSchedule(ScheduleMode.WEEKDAYS, DayOfWeek.entries.toSet()),
        majorSchedule = TrackSchedule(ScheduleMode.WEEKDAYS, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)),
    )

    @Test
    fun eachTrackOnItsOwnDays() {
        assertTrue(ownDays.hasSeparateSchedules)
        assertNull(ownDays.validate())
        val rows = PlanBuilder.build(ownDays)
        // The lessons keep the paper's days.
        val lessons = rows.filter { it.lesson != null }
        assertEquals(PlanBuilder.build(paper).map { it.date to it.lesson }, lessons.map { it.date to it.lesson })
        // With a daily minor review every day up to the last lesson has a row.
        assertEquals(ChronoUnit.DAYS.between(rows.first().date, rows.last().date) + 1, rows.size.toLong())
        assertEquals(lessons.last().date, rows.last().date)
        // Monday: no lesson; its minor review is the lessons before it; the major review falls on it.
        val monday = rows.first { it.date == LocalDate.of(2026, 11, 2) }
        assertNull(monday.lesson)
        assertEquals("العصر", RangeFormatter.formatAll(monday.minor))
        assertEquals(Tracks.MINOR or Tracks.MAJOR, monday.tracks)
        rows.forEach { r ->
            assertEquals(r.date.dayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), r.tracks and Tracks.MAJOR != 0)
            assertEquals(r.lesson != null, r.tracks and Tracks.NEW != 0)
        }
        // Same days for all is the old table.
        assertTrue(PlanBuilder.build(ownDays.withSeparateSchedules(false)).all { it.tracks == Tracks.ALL })
    }

    @Test
    fun memorizedReviewOnItsOwnDays() {
        // A surah every Sun/Tue/Thu backward from Al-Haqqah, and 20 pages of review every day.
        val s = fromHaqqah.withSeparateSchedules(true).copy(
            majorSchedule = TrackSchedule(ScheduleMode.WEEKDAYS, DayOfWeek.entries.toSet()),
        )
        val rows = PlanBuilder.build(s)
        assertEquals(listOf("الحاقة", null, "القلم"), rows.take(3).map { r -> r.lesson?.let { RangeFormatter.format(it) } })
        // Sunday and Monday review Al-Ma'arij to An-Nas; on Tuesday Al-Haqqah is memorized, so the next round starts there.
        assertEquals(Quran.surahRange(70).first..Quran.surahRange(114).last, rows[0].major!!.first..rows[1].major!!.last)
        assertEquals(Quran.index(69, 1), rows[2].major!!.first)
    }

    @Test
    fun reschedulingWithSeparateDays() {
        val rows = PlanBuilder.build(ownDays).mapIndexed { i, d -> d.toEntity(1, i) }.toMutableList()
        rows[0] = rows[0].copy(newDone = true) // Sunday's lesson
        rows[1] = rows[1].copy(minorDone = true) // Monday's review
        val r = rescheduleSeparately(ownDays, rows, LocalDate.of(2026, 11, 4))!!
        // Tuesday's lesson was missed; Tuesday keeps its review without the lesson.
        assertEquals(1, r.missed)
        assertEquals(2, r.from)
        assertEquals(listOf(LocalDate.of(2026, 11, 3)), r.kept.map { it.localDate })
        assertTrue(!r.kept.single().hasLesson && r.kept.single().has(Item.MINOR))
        // From today: Wednesday's reviews, then Al-Humazah on Thursday.
        assertEquals(LocalDate.of(2026, 11, 4), r.added.first().date)
        val first = r.added.first { it.lesson != null }
        assertEquals(LocalDate.of(2026, 11, 5), first.date)
        assertEquals("الهمزة", RangeFormatter.format(first.lesson!!))
        assertEquals("العصر", RangeFormatter.formatAll(r.added.first().minor))
        // Nothing missed: nothing to do.
        assertNull(rescheduleSeparately(ownDays, rows, LocalDate.of(2026, 11, 3)))
    }

    @Test
    fun memorizedReviewCarriesOnAfterKeptRows() {
        val rows = PlanBuilder.build(fromHaqqah)
        val continued = PlanBuilder.continuation(fromHaqqah, rows[0].lesson, rows[0].major)!!
        val more = PlanBuilder.extend(listOf(rows[0].lesson!!), rows[0].major, continued)
        assertEquals(rows.drop(1).map { it.major }, more.map { it.major })
        // Nothing memorized before the plan: the review waits for the first lesson outside the minor review.
        val fresh = PlanBuilder.build(fromHaqqah.copy(majorPrior = false, includeMinor = true, minorCount = 2))
        assertEquals(listOf(null, null, null), fresh.take(3).map { it.major })
        assertEquals(fresh[0].lesson, fresh[3].major)
    }

    /**
     * No session reviews the same ayah twice, or (with [ownTracks]) what its own lesson and minor review
     * cover. A fixed review range may well hold the lessons, so that is only checked for «من محفوظي».
     */
    private fun assertNoRepeats(rows: List<com.sallo.hifz.plan.RowDraft>, ownTracks: Boolean = true) {
        rows.forEachIndexed { i, r ->
            val parts = r.majorParts + if (ownTracks) r.minor + listOfNotNull(r.lesson) else emptyList()
            assertEquals("row $i: $parts", parts.sumOf { it.count() }, com.sallo.hifz.quran.Ranges.merge(parts).sumOf { it.count() })
        }
    }

    @Test
    fun lastSessionOfARoundGoesOnIntoTheNextRound() {
        // Five pages a session over Al-Ma'arij to An-Nas while memorizing backward from Al-Haqqah.
        val s = fromHaqqah.copy(majorPortion = fromHaqqah.majorPortion.copy(amount = 5.0), majorWrap = true)
        assertTrue(s.wrapsMajor)
        val rows = PlanBuilder.build(s)
        val j = rows.indexOfFirst { it.majorLead.isNotEmpty() }
        assertTrue("no session went on into the next round", j > 0)
        // The first round runs from Al-Ma'arij without gaps…
        assertEquals(Quran.surahRange(70).first, rows[0].major!!.first)
        for (k in 1 until j) assertEquals(rows[k - 1].major!!.last + 1, rows[k].major!!.first)
        // …and its last session reviews what is left of it, to An-Nas…
        val lead = rows[j].majorLead.single()
        val head = rows[j].major!!
        assertEquals(rows[j - 1].major!!.last + 1, lead.first)
        assertEquals(Quran.AYAH_COUNT - 1, lead.last)
        assertTrue(Quran.weight(lead) < 5.0)
        // …then the start of the next round, the surah memorized just before this session's lesson, up to five pages.
        assertEquals(Quran.surahRange(70 - j).first, head.first)
        assertEquals(5.0, Quran.weight(lead) + Quran.weight(head), 1.0)
        // The next round goes on after it.
        assertEquals(head.last + 1, rows[j + 1].major!!.first)
        assertNoRepeats(rows)
        // Shown as one review: the end of the round, then the start of the next.
        val entity = rows[j].toEntity(1, j)
        assertEquals(listOf(lead, head), entity.majorParts)
        assertEquals(RangeFormatter.formatAll(listOf(lead, head)), com.sallo.hifz.common.PlanText.major(entity))
        // Without the option no session has a second part.
        assertTrue(PlanBuilder.build(s.copy(majorWrap = false)).all { it.majorLead.isEmpty() })
        // Editing the plan after that session carries on in the next round.
        val more = PlanBuilder.extend(rows.take(j + 1).map { it.lesson!! }, head, PlanBuilder.continuation(s, rows[j].lesson, head)!!)
        assertEquals(rows[j + 1].major, more.first().major)
    }

    @Test
    fun aRangeGoneRoundFillsItsLastSession() {
        // Juz 30 (pages 582 to 604) five pages a session, while memorizing the paper's surahs.
        val s = paper.copy(
            majorMode = MajorMode.RANGE,
            majorPortion = PortionSpec(MemUnit.PAGE, 5.0, RangeSpec(RangeType.JUZ, 30, 30), Rounding.NEAREST),
            majorWrap = true,
        )
        val rows = PlanBuilder.build(s)
        assertEquals((582..601 step 5).map { Quran.pageRange(it).first..Quran.pageRange(it + 4).last }, rows.take(4).map { it.major })
        // Pages 602 to 604 are left: then two pages from the start of the juz.
        assertEquals(listOf(Quran.pageRange(602).first..Quran.pageRange(604).last), rows[4].majorLead)
        assertEquals(Quran.pageRange(582).first..Quran.pageRange(583).last, rows[4].major)
        assertEquals(Quran.pageRange(584).first..Quran.pageRange(588).last, rows[5].major)
        assertNoRepeats(rows, ownTracks = false)
        // Continuing after the kept rows picks up there.
        val more = PlanBuilder.extend(rows.take(5).map { it.lesson!! }, rows[4].major, s)
        assertEquals(rows[5].major, more.first().major)
        // Backward from An-Nas the start of the next round is the end of the mushaf.
        val up = s.copy(majorPortion = s.majorPortion.copy(range = RangeSpec(RangeType.SURAHS, 114, 78)))
        val upRows = PlanBuilder.build(up)
        val k = upRows.indexOfFirst { it.majorLead.isNotEmpty() }
        assertEquals(Quran.surahRange(78).first, upRows[k].majorLead.single().first)
        assertEquals(Quran.AYAH_COUNT - 1, upRows[k].major!!.last)
        assertEquals(upRows[k].major!!.first - 1, upRows[k + 1].major!!.last)
        // Whole units: two juz a session over juz 28 to 30.
        val juz = s.copy(majorPortion = PortionSpec(MemUnit.JUZ, 2.0, RangeSpec(RangeType.JUZS, 28, 30)))
        val juzRows = PlanBuilder.build(juz)
        assertEquals(Quran.juzRange(28).first..Quran.juzRange(29).last, juzRows[0].major)
        assertEquals(listOf(Quran.juzRange(30)), juzRows[1].majorLead)
        assertEquals(Quran.juzRange(28), juzRows[1].major)
        assertEquals(Quran.juzRange(29).first..Quran.juzRange(30).last, juzRows[2].major)
    }

    @Test
    fun aShortRoundIsNotFilledWithItself() {
        // Nothing memorized before: the first rounds are a lesson or two, shorter than a session.
        val s = fromHaqqah.copy(majorPrior = false, includeMinor = true, minorCount = 2, majorPortion = fromHaqqah.majorPortion.copy(amount = 5.0), majorWrap = true)
        val rows = PlanBuilder.build(s)
        assertEquals(rows[0].lesson, rows[3].major)
        assertNoRepeats(rows)
        // Each track on its own days fills up the same way.
        val ownDays = fromHaqqah.copy(majorPortion = fromHaqqah.majorPortion.copy(amount = 5.0), majorWrap = true)
            .withSeparateSchedules(true)
            .copy(majorSchedule = TrackSchedule(ScheduleMode.WEEKDAYS, DayOfWeek.entries.toSet()))
        val separate = PlanBuilder.build(ownDays)
        assertTrue(separate.any { it.majorLead.isNotEmpty() })
        assertNoRepeats(separate)
    }
}
