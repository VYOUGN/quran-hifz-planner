package com.sallo.hifz

import com.sallo.hifz.quran.Quran
import com.sallo.hifz.quran.RangeFormatter
import com.sallo.hifz.quran.Ranges
import org.junit.Assert.assertEquals
import org.junit.Test

class RangeFormatterTest {
    private fun r(s1: Int, a1: Int, s2: Int, a2: Int) = Quran.index(s1, a1)..Quran.index(s2, a2)

    @Test
    fun formatsLikeThePaper() {
        assertEquals("العصر", RangeFormatter.format(Quran.surahRange(103)))
        assertEquals("الرحمن - الواقعة", RangeFormatter.format(r(55, 1, 56, 96)))
        assertEquals("البقرة 6-16", RangeFormatter.format(r(2, 6, 2, 16)))
        assertEquals("البقرة 282", RangeFormatter.format(r(2, 282, 2, 282)))
        assertEquals("البقرة 280 - آل عمران 5", RangeFormatter.format(r(2, 280, 3, 5)))
        assertEquals("الملك - القلم 15", RangeFormatter.format(r(67, 1, 68, 15)))
        assertEquals("—", RangeFormatter.formatAll(emptyList()))
        assertEquals(
            "العصر، الفيل",
            RangeFormatter.formatAll(listOf(Quran.surahRange(103), Quran.surahRange(105))),
        )
        assertEquals("الرحمن - الواقعة", RangeFormatter.surahSpan(56, 55))
        assertEquals("الفاتحة", RangeFormatter.surahSpan(1, 1))
    }

    @Test
    fun pageLabels() {
        assertEquals("ص 604", RangeFormatter.pageLabel(Quran.pageRange(604), halves = false))
        assertEquals(
            "ص 603-604",
            RangeFormatter.pageLabel(Quran.pageRange(603).first..Quran.pageRange(604).last, halves = false),
        )
        val split = Quran.halfPageSplit(2)!!
        assertEquals("ص 2 (النصف الأول)", RangeFormatter.pageLabel(Quran.pageRange(2).first until split, halves = true))
        assertEquals("ص 2 (النصف الثاني)", RangeFormatter.pageLabel(split..Quran.pageRange(2).last, halves = true))
    }

    @Test
    fun rangesMergeAndRoundTrip() {
        val merged = Ranges.merge(listOf(10..12, 0..4, 5..7, 13..13))
        assertEquals(listOf(0..7, 10..13), merged)
        assertEquals(merged, Ranges.decode(Ranges.encode(merged)))
        assertEquals(emptyList<IntRange>(), Ranges.decode(""))
    }
}
