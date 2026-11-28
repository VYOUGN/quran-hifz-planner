package com.sallo.hifz

import com.sallo.hifz.quran.Quran
import com.sallo.hifz.quran.RangeFormatter
import com.sallo.hifz.quran.RangeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RangeParserTest {
    private fun at(surah: Int, ayah: Int) = Quran.index(surah, ayah)

    @Test
    fun readsWhatTheFormatterWrites() {
        val samples = listOf(
            Quran.surahRange(103),
            at(2, 6)..at(2, 16),
            at(2, 7)..at(2, 7),
            Quran.surahRange(55).first..Quran.surahRange(56).last,
            at(2, 280)..at(3, 5),
            Quran.surahRange(73).first..Quran.surahRange(75).last,
            at(90, 11)..at(90, 20),
        )
        for (r in samples) assertEquals(RangeFormatter.format(r), listOf(r), RangeParser.parse(RangeFormatter.format(r)))
        assertEquals(samples.take(2), RangeParser.parse(RangeFormatter.formatAll(samples.take(2))))
    }

    @Test
    fun takesWhatPeopleTypeAndRefusesTheRest() {
        assertEquals(listOf(at(90, 11)..at(90, 20)), RangeParser.parse("سورة البلد ١١-٢٠"))
        assertEquals(listOf(Quran.surahRange(73).first..Quran.surahRange(75).last), RangeParser.parse("القيامة - المزمل"))
        assertEquals(listOf(at(2, 280)..at(2, 286)), RangeParser.parse("البقرة 280 - 286"))
        assertNull(RangeParser.parse("مراجعة حرة"))
        assertNull(RangeParser.parse("البقرة 300"))
        assertNull(RangeParser.parse(""))
    }
}
