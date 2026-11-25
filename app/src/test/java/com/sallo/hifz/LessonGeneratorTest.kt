package com.sallo.hifz

import com.sallo.hifz.plan.LessonGenerator
import com.sallo.hifz.plan.MemUnit
import com.sallo.hifz.plan.PortionSpec
import com.sallo.hifz.plan.RangeSpec
import com.sallo.hifz.plan.RangeType
import com.sallo.hifz.plan.Rounding
import com.sallo.hifz.quran.Quran
import com.sallo.hifz.quran.RangeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonGeneratorTest {
    private fun names(lessons: List<IntRange>) = lessons.map { RangeFormatter.format(it) }

    @Test
    fun paperExampleIsOneSurahPerLesson() {
        assertEquals(
            listOf(
                "العصر", "الهمزة", "الفيل", "قريش", "الماعون", "الكوثر",
                "الكافرون", "النصر", "المسد", "الإخلاص", "الفلق", "الناس",
            ),
            names(LessonGenerator.byAyahs(103, 1, 114, 6, 10)),
        )
    }

    @Test
    fun longSurahsSplitEvenly() {
        assertEquals(listOf("التكاثر 1-4", "التكاثر 5-8"), names(LessonGenerator.byAyahs(102, 1, 102, 8, 5)))
        assertEquals(listOf("الناس"), names(LessonGenerator.byAyahs(114, 1, 114, 6, 5)))
        val baqara = LessonGenerator.byAyahs(2, 1, 2, 286, 10)
        assertEquals(29, baqara.size)
        assertTrue(baqara.all { it.count() in 9..10 })
    }

    @Test
    fun backwardWalksSurahsDownwards() {
        val lessons = LessonGenerator.byAyahs(114, 1, 78, 1, 10)
        assertEquals("الناس", RangeFormatter.format(lessons[0]))
        assertEquals("الفلق", RangeFormatter.format(lessons[1]))
        assertEquals(78, Quran.surahOf(lessons.last().first))
        assertCovers(lessons, Quran.surahRange(78).first..Quran.surahRange(114).last)
        val surahs = lessons.map { Quran.surahOf(it.first) }
        assertEquals(surahs.sortedDescending(), surahs)
    }

    @Test
    fun forwardCoversExactlyTheRange() {
        for (n in listOf(1, 3, 7, 10, 25)) {
            val lessons = LessonGenerator.byAyahs(2, 100, 5, 20, n)
            assertCovers(lessons, Quran.index(2, 100)..Quran.index(5, 20))
            lessons.forEach { assertEquals(Quran.surahOf(it.first), Quran.surahOf(it.last)) }
        }
        assertCovers(LessonGenerator.byAyahs(1, 1, 114, 6, 5), 0 until Quran.AYAH_COUNT)
    }

    @Test
    fun wholePagesForwardAndBackward() {
        val forward = LessonGenerator.byPages(1, 604, 1)
        assertEquals(604, forward.size)
        forward.forEachIndexed { i, r -> assertEquals(Quran.pageRange(i + 1), r) }

        val juzAmma = Quran.pageRange(582).first..Quran.pageRange(604).last
        val two = LessonGenerator.byPages(582, 604, 2)
        assertEquals(12, two.size)
        assertCovers(two, juzAmma)

        val backward = LessonGenerator.byPages(604, 582, 2)
        assertEquals(Quran.pageRange(603).first..Quran.pageRange(604).last, backward.first())
        assertEquals(Quran.pageRange(582), backward.last())
        assertCovers(backward, juzAmma)
    }

    @Test
    fun halfPagesStayInsideTheirPage() {
        val halves = PortionSpec(MemUnit.PAGE, 0.5, RangeSpec(RangeType.WHOLE), Rounding.NONE).generate()
        assertCovers(halves, 0 until Quran.AYAH_COUNT)
        assertTrue(halves.size > 1100)
        halves.forEach { assertEquals(Quran.pageOf(it.first), Quran.pageOf(it.last)) }
        // The half boundary is the same one the half-page splitter picks.
        for (p in listOf(2, 50, 300, 582)) {
            val split = Quran.halfPageSplit(p)!!
            assertTrue("page $p", halves.any { it.first == split })
        }
    }

    companion object {
        fun assertCovers(lessons: List<IntRange>, expected: IntRange) {
            val seen = BooleanArray(Quran.AYAH_COUNT)
            for (lesson in lessons) {
                assertFalse("empty lesson", lesson.isEmpty())
                for (i in lesson) {
                    assertFalse("ayah $i twice", seen[i])
                    seen[i] = true
                }
            }
            for (i in 0 until Quran.AYAH_COUNT) assertEquals("ayah $i", i in expected, seen[i])
        }
    }
}
