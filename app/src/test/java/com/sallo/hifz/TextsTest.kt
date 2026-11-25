package com.sallo.hifz

import com.sallo.hifz.common.Plurals
import org.junit.Assert.assertEquals
import org.junit.Test

class TextsTest {
    @Test
    fun percentShowsTheFirstSessions() {
        assertEquals("0%", Plurals.percent(0f))
        // 6 of 1219 sessions: a long plan's first week still shows.
        assertEquals("0.5%", Plurals.percent(6f / 1219f))
        assertEquals("0.1%", Plurals.percent(1f / 5000f))
        assertEquals("1%", Plurals.percent(0.01f))
        assertEquals("9.9%", Plurals.percent(0.099f))
        assertEquals("10%", Plurals.percent(0.0996f))
        assertEquals("42%", Plurals.percent(0.42f))
        // Not 100% before the last session.
        assertEquals("99%", Plurals.percent(0.999f))
        assertEquals("100%", Plurals.percent(1f))
    }

    @Test
    fun countedNouns() {
        assertEquals("آية واحدة", Plurals.ayahs(1))
        assertEquals("7 آيات", Plurals.ayahs(7))
        assertEquals("46 آية", Plurals.ayahs(46))
        assertEquals("درس واحد", Plurals.lessons(1))
        assertEquals("درسان", Plurals.lessons(2))
        assertEquals("3 دروس", Plurals.lessons(3))
        assertEquals("أسبوعان", Plurals.weeks(2))
        assertEquals("5 أسابيع", Plurals.weeks(5))
        assertEquals("12 أسبوعاً", Plurals.weeks(12))
    }
}
