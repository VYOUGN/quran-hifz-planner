package com.sallo.hifz

import com.sallo.hifz.LessonGeneratorTest.Companion.assertCovers
import com.sallo.hifz.plan.MemUnit
import com.sallo.hifz.plan.PortionSpec
import com.sallo.hifz.plan.RangeSpec
import com.sallo.hifz.plan.RangeType
import com.sallo.hifz.plan.Rounding
import com.sallo.hifz.quran.Quran
import com.sallo.hifz.quran.RangeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortionsTest {
    private val maarijToNas = RangeSpec(RangeType.SURAHS, 70, 114)

    @Test
    fun quartersAndHizbs() {
        assertEquals(240, Quran.quarters.size)
        assertEquals(6236, Quran.quarters.sumOf { it.count() })
        assertEquals(Quran.juzRange(30).first, Quran.hizbRange(59).first)
        assertEquals(Quran.juzRange(30).last, Quran.hizbRange(60).last)
        assertEquals(60, Quran.hizbOf(Quran.AYAH_COUNT - 1))
        assertEquals(1, Quran.quarterOf(0))
    }

    @Test
    fun everyUnitDirectionAndRoundingCoversTheRange() {
        val ranges = listOf(
            RangeSpec(RangeType.WHOLE),
            RangeSpec(RangeType.SURAH, 2),
            maarijToNas,
            RangeSpec(RangeType.SURAHS, 114, 78),
            RangeSpec(RangeType.JUZS, 28, 30),
            RangeSpec(RangeType.JUZS, 30, 27),
            RangeSpec(RangeType.HIZBS, 3, 6),
            RangeSpec(RangeType.PAGES, 604, 560),
            RangeSpec(RangeType.AYAHS, 2, 30, 3, 10),
        )
        val portions = listOf(
            MemUnit.AYAH to 7.0, MemUnit.PAGE to 0.5, MemUnit.PAGE to 1.0, MemUnit.PAGE to 2.5,
            MemUnit.PAGE to 5.0, MemUnit.QUARTER to 3.0, MemUnit.HIZB to 1.0, MemUnit.JUZ to 1.0, MemUnit.SURAH to 2.0,
            MemUnit.PASSAGE to 1.0, MemUnit.PASSAGE to 3.0,
        )
        for (range in ranges) for ((unit, amount) in portions) for (rounding in Rounding.entries) for (complete in listOf(false, true)) {
            val sessions = PortionSpec(unit, amount, range, rounding, completeSurah = complete).generate()
            assertCovers(sessions, range.bounds())
        }
    }

    @Test
    fun sessionsRespectSurahBoundaries() {
        val juz30 = RangeSpec(RangeType.JUZ, 30, 30)
        val plain = PortionSpec(MemUnit.PAGE, 5.0, juz30, Rounding.NEAREST).generate()
        assertEquals("الانفطار - الأعلى 15", RangeFormatter.format(plain[1]))
        // Al-A'la begins below the top of page 591, that session's last page, so it waits for the next one.
        val bounded = PortionSpec(MemUnit.PAGE, 5.0, juz30, Rounding.NEAREST, completeSurah = true).generate()
        assertEquals("الانفطار - الطارق", RangeFormatter.format(bounded[1]))
        assertEquals(Quran.index(87, 1), bounded[2].first)
        assertCovers(bounded, juz30.bounds())
        for (range in listOf(maarijToNas, juz30, RangeSpec(RangeType.JUZS, 26, 30), RangeSpec(RangeType.SURAH, 2), RangeSpec(RangeType.WHOLE))) {
            val sessions = PortionSpec(MemUnit.PAGE, 5.0, range, Rounding.NEAREST, completeSurah = true).generate()
            assertCovers(sessions, range.bounds())
            sessions.dropLast(1).forEach { assertForwardEnd(it) }
        }
        // Backward plans get the mirror image at each session's first page.
        for (pages in listOf(RangeSpec(RangeType.PAGES, 604, 562), RangeSpec(RangeType.PAGES, 300, 200))) {
            val back = PortionSpec(MemUnit.PAGE, 2.0, pages, Rounding.NEAREST, completeSurah = true).generate()
            assertCovers(back, pages.bounds())
            back.dropLast(1).forEach { assertBackwardStart(it) }
        }
    }

    private fun downThePage(position: Int, page: Int) = Quran.prefix(position) - Quran.prefix(Quran.pageRange(page).first)

    /** A session ends at a surah's end, or inside a surah that began above it and runs past the next page. */
    private fun assertForwardEnd(s: IntRange) {
        val next = s.last + 1
        if (Quran.ayahOf(next) == 1) return
        val surah = Quran.surahRange(Quran.surahOf(next))
        val lastPage = Quran.pageOf(s.last)
        val text = RangeFormatter.format(s)
        if (surah.first > s.first && Quran.pageOf(surah.first) == lastPage) {
            assertTrue("$text: its last surah begins low on the page", downThePage(surah.first, lastPage) < 1.0 / 3)
        }
        assertTrue("$text: its last surah ends on the next page", Quran.pageOf(surah.last) > lastPage + 1)
    }

    /** Mirror image: a session starts at a surah's beginning, or after one whose short end it left out. */
    private fun assertBackwardStart(s: IntRange) {
        if (Quran.ayahOf(s.first) == 1) return
        val surah = Quran.surahRange(Quran.surahOf(s.first))
        val firstPage = Quran.pageOf(s.first)
        val text = RangeFormatter.format(s)
        if (surah.last < s.last && Quran.pageOf(surah.last) == firstPage) {
            assertTrue("$text: starts with a short surah end", downThePage(surah.last + 1, firstPage) > 2.0 / 3)
        }
        assertTrue("$text: its first surah begins on the page before", Quran.pageOf(surah.first) < firstPage - 1)
    }

    @Test
    fun roundingToTheNearestPageEnd() {
        val p = PortionSpec(MemUnit.PAGE, 5.0, maarijToNas, Rounding.NEAREST)
        val sessions = p.generate()
        val first = sessions.first()
        assertEquals(Quran.index(70, 1), first.first)
        // Five pages from mid-page 568 end nearer the end of page 573 than of page 572.
        assertEquals(573, Quran.pageOf(first.last))
        assertTrue(Quran.isPageStart(first.last + 1))
        assertTrue(Quran.weight(first) in 5.0..6.0)
        // Every later session also ends at a page end.
        sessions.dropLast(1).forEach { assertTrue(Quran.isPageStart(it.last + 1)) }
        assertCovers(sessions, maarijToNas.bounds())
        // After the first session the sessions are whole blocks of five pages.
        assertEquals(Quran.pageRange(574).first..Quran.pageRange(578).last, sessions[1])
        assertEquals(Quran.pageRange(579).first..Quran.pageRange(583).last, sessions[2])
        assertEquals(Quran.pageRange(584).first..Quran.pageRange(588).last, sessions[3])
    }

    @Test
    fun noRoundingKeepsTheAmount() {
        val sessions = PortionSpec(MemUnit.PAGE, 5.0, maarijToNas, Rounding.NONE).generate()
        sessions.dropLast(1).forEach { assertEquals(5.0, Quran.weight(it), 0.6) }
    }

    @Test
    fun downAndUpRounding() {
        val down = PortionSpec(MemUnit.PAGE, 5.0, maarijToNas, Rounding.DOWN).generate().first()
        val up = PortionSpec(MemUnit.PAGE, 5.0, maarijToNas, Rounding.UP).generate().first()
        assertTrue(Quran.weight(down) <= 5.0)
        assertTrue(Quran.weight(up) >= 5.0)
        assertTrue(Quran.isPageStart(down.last + 1))
        assertTrue(Quran.isPageStart(up.last + 1))
    }

    @Test
    fun splittingIntoSessionsByEndDate() {
        for (n in listOf(1, 3, 8, 20)) {
            val pages = PortionSpec(MemUnit.PAGE, 1.0, maarijToNas, Rounding.NEAREST).generate(sessions = n)
            assertTrue(pages.size <= n)
            assertCovers(pages, maarijToNas.bounds())
            val ayahs = PortionSpec(MemUnit.AYAH, 1.0, maarijToNas).generate(sessions = n)
            assertTrue("ayahs $n -> ${ayahs.size}", ayahs.size <= n)
            assertCovers(ayahs, maarijToNas.bounds())
            val passages = PortionSpec(MemUnit.PASSAGE, 1.0, maarijToNas).generate(sessions = n)
            assertTrue("passages $n -> ${passages.size}", passages.size <= n)
            assertCovers(passages, maarijToNas.bounds())
        }
        val juz = PortionSpec(MemUnit.JUZ, 1.0, RangeSpec(RangeType.WHOLE)).generate(sessions = 20)
        assertEquals(20, juz.size)
        assertCovers(juz, 0 until Quran.AYAH_COUNT)
    }

    @Test
    fun wholeUnits() {
        val juz = PortionSpec(MemUnit.JUZ, 1.0, RangeSpec(RangeType.WHOLE)).generate()
        assertEquals(30, juz.size)
        juz.forEachIndexed { i, r -> assertEquals(Quran.juzRange(i + 1), r) }
        val backward = PortionSpec(MemUnit.JUZ, 1.0, RangeSpec(RangeType.JUZS, 30, 28)).generate()
        assertEquals(listOf(Quran.juzRange(30), Quran.juzRange(29), Quran.juzRange(28)), backward)
        val surahs = PortionSpec(MemUnit.SURAH, 2.0, RangeSpec(RangeType.SURAHS, 78, 114)).generate()
        assertEquals(19, surahs.size)
        assertEquals("النبأ - النازعات", RangeFormatter.format(surahs.first()))
        // A range starting mid-juz: the first session runs to the end of that juz.
        val fromMaarij = PortionSpec(MemUnit.JUZ, 1.0, maarijToNas).generate()
        assertEquals(Quran.index(70, 1)..Quran.juzRange(29).last, fromMaarij.first())
    }

    @Test
    fun continuingFromAKeptSession() {
        val specs = listOf(
            PortionSpec(MemUnit.PAGE, 2.0, RangeSpec(RangeType.JUZ, 30), Rounding.NEAREST),
            PortionSpec(MemUnit.PAGE, 1.0, RangeSpec(RangeType.PAGES, 604, 580), Rounding.NEAREST),
            PortionSpec(MemUnit.AYAH, 10.0, RangeSpec(RangeType.SURAHS, 114, 100)),
            PortionSpec(MemUnit.AYAH, 10.0, RangeSpec(RangeType.SURAHS, 2, 3)),
            PortionSpec(MemUnit.HIZB, 1.0, RangeSpec(RangeType.HIZBS, 60, 55)),
            PortionSpec(MemUnit.PAGE, 5.0, maarijToNas, Rounding.NEAREST, completeSurah = true),
            PortionSpec(MemUnit.PAGE, 2.0, RangeSpec(RangeType.PAGES, 604, 562), Rounding.NEAREST, completeSurah = true),
            PortionSpec(MemUnit.PASSAGE, 1.0, RangeSpec(RangeType.SURAHS, 114, 67)),
            PortionSpec(MemUnit.PASSAGE, 2.0, RangeSpec(RangeType.JUZ, 29, 29)),
        )
        for (spec in specs) {
            val full = spec.generate()
            for (k in 1 until full.size) {
                val next = spec.nextResume(full[k - 1])!!
                assertEquals("${spec.range.describe()} after $k", full.drop(k), spec.copy(resume = next).generate())
            }
            assertNull(spec.nextResume(full.last()))
            assertEquals(full, spec.copy(resume = spec.resumeAt(full.first())).generate())
        }
    }

    @Test
    fun tafsirPassages() {
        assertTrue(Quran.hasPassages)
        assertCovers(Quran.passages, 0 until Quran.AYAH_COUNT)
        Quran.passages.forEach { assertEquals(Quran.surahOf(it.first), Quran.surahOf(it.last)) }
        val maarij = listOf("المعارج 1-7", "المعارج 8-18", "المعارج 19-35", "المعارج 36-44")
        val forward = PortionSpec(MemUnit.PASSAGE, 1.0, RangeSpec(RangeType.SURAH, 70, 70)).generate()
        assertEquals(maarij, forward.map { RangeFormatter.format(it) })
        // Backward the surahs go up the mushaf, but each one is read from its beginning.
        val backward = PortionSpec(MemUnit.PASSAGE, 1.0, RangeSpec(RangeType.SURAHS, 71, 70)).generate()
        assertEquals(Quran.index(71, 1), backward.first().first)
        assertEquals(maarij, backward.takeLast(4).map { RangeFormatter.format(it) })
        assertCovers(backward, RangeSpec(RangeType.SURAHS, 70, 71).bounds())
        // Two passages a session join whole short surahs, never a surah's end to the start of the one before it.
        val pairs = PortionSpec(MemUnit.PASSAGE, 2.0, RangeSpec(RangeType.SURAHS, 114, 112)).generate()
        assertEquals(listOf("الفلق - الناس", "الإخلاص"), pairs.map { RangeFormatter.format(it) })
        val acrossMaarij = PortionSpec(MemUnit.PASSAGE, 2.0, RangeSpec(RangeType.SURAHS, 71, 70)).generate()
        acrossMaarij.forEach { assertEquals(Quran.surahOf(it.first), Quran.surahOf(it.last)) }
    }

    @Test
    fun backwardAyahRangeStartsPartwayIntoItsFirstSurah() {
        val sessions = PortionSpec(MemUnit.AYAH, 10.0, RangeSpec(RangeType.AYAHS, 114, 112, 4, 1)).generate()
        assertEquals("الناس 4-6", RangeFormatter.format(sessions.first()))
        assertEquals(listOf("الناس 4-6", "الفلق", "الإخلاص"), sessions.map { RangeFormatter.format(it) })
    }

    @Test
    fun rangeDescriptionsAndValidation() {
        assertEquals("من المعارج إلى الناس", maarijToNas.describe())
        assertEquals("الجزء 30", RangeSpec(RangeType.JUZ, 30).describe())
        assertEquals("بداية المدى بعد نهايته", RangeSpec(RangeType.AYAHS, 2, 2, 10, 5).validate())
        assertNull(RangeSpec(RangeType.AYAHS, 3, 2, 10, 1).validate())
        assertTrue(maarijToNas.isBackward.not())
        assertTrue(RangeSpec(RangeType.PAGES, 604, 1).isBackward)
        assertEquals(37.0, PortionSpec(MemUnit.PAGE, 5.0, maarijToNas).totalPages(), 1.0)
    }
}
