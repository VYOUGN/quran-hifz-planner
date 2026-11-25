package com.sallo.hifz

import com.sallo.hifz.quran.Quran
import com.sallo.hifz.quran.QuranData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranDataTest {

    @Test
    fun countsMatchTheMushaf() {
        assertEquals(114, QuranData.SURAH_NAMES.size)
        assertEquals(6236, (1..114).sumOf { Quran.ayahCount(it) })
        assertEquals(6236, (1..604).sumOf { Quran.pageRange(it).count() })
        assertEquals(6236, (1..30).sumOf { Quran.juzRange(it).count() })
    }

    @Test
    fun knownPageStarts() {
        assertEquals(Quran.index(1, 1), Quran.pageRange(1).first)
        assertEquals(Quran.index(2, 1), Quran.pageRange(2).first)
        assertEquals(Quran.index(2, 6), Quran.pageRange(3).first)
        assertEquals(Quran.index(3, 1), Quran.pageRange(50).first)
        assertEquals(Quran.index(4, 1), Quran.pageRange(77).first)
        assertEquals(Quran.index(78, 1), Quran.pageRange(582).first)
        assertEquals(Quran.index(112, 1), Quran.pageRange(604).first)
        assertEquals(Quran.AYAH_COUNT - 1, Quran.pageRange(604).last)
    }

    @Test
    fun juzStarts() {
        val expected = listOf(
            1 to 1, 2 to 142, 2 to 253, 3 to 93, 4 to 24, 4 to 148, 5 to 82, 6 to 111, 7 to 88, 8 to 41,
            9 to 93, 11 to 6, 12 to 53, 15 to 1, 17 to 1, 18 to 75, 21 to 1, 23 to 1, 25 to 21, 27 to 56,
            29 to 46, 33 to 31, 36 to 28, 39 to 32, 41 to 47, 46 to 1, 51 to 31, 58 to 1, 67 to 1, 78 to 1,
        )
        expected.forEachIndexed { i, (s, a) ->
            assertEquals("juz ${i + 1}", Quran.index(s, a), Quran.juzRange(i + 1).first)
        }
        assertEquals(22, Quran.pageOf(Quran.juzRange(2).first))
        assertEquals(582, Quran.pageOf(Quran.juzRange(30).first))
    }

    @Test
    fun indexRoundTrip() {
        for (i in 0 until Quran.AYAH_COUNT) {
            assertEquals(i, Quran.index(Quran.surahOf(i), Quran.ayahOf(i)))
            assertTrue(i in Quran.pageRange(Quran.pageOf(i)))
            assertTrue(i in Quran.juzRange(Quran.juzOf(i)))
        }
    }

    @Test
    fun lettersAndPageSizes() {
        assertEquals(19, Quran.letters(Quran.index(1, 1)))
        assertEquals(604.0, Quran.pages(0 until Quran.AYAH_COUNT), 1e-6)
        assertEquals(1.0, Quran.pages(Quran.pageRange(300)), 1e-9)
    }

    @Test
    fun halfPageSplitFallsInsideThePage() {
        for (p in 1..604) {
            val r = Quran.pageRange(p)
            val split = Quran.halfPageSplit(p)
            if (r.first == r.last) {
                assertNull(split)
            } else {
                assertNotNull(split)
                assertTrue("page $p", split!! > r.first && split <= r.last)
            }
        }
    }

    @Test
    fun names() {
        assertEquals("الفاتحة", Quran.surahName(1))
        assertEquals("إبراهيم", Quran.surahName(14))
        assertEquals("الناس", Quran.surahName(114))
        assertEquals("النبأ", Quran.surahAtPage(582))
    }
}
