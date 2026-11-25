package com.sallo.hifz

import com.sallo.hifz.plan.GradeScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradingTest {
    @Test
    fun everySystemRoundTripsItsOwnMarks() {
        for (scale in GradeScale.entries) {
            assertEquals(scale.levels.size, scale.levels.map { it.score }.toSet().size)
            scale.levels.forEach { assertEquals(it, scale.levelOf(it.score)) }
            assertNull(scale.levelOf(0))
            // Each system has a mark that means «repeat it» and is not a pass.
            assertFalse(scale.name, scale.levels.last().passed)
            assertTrue(scale.name, scale.levels.first().passed)
        }
    }

    @Test
    fun switchingSystemsShowsTheNearestMark() {
        val excellent = GradeScale.WORDS.levels.first().score
        assertEquals("أ+", GradeScale.LETTERS.levelOf(excellent)!!.label)
        assertEquals("ممتاز", GradeScale.WORDS.levelOf(GradeScale.LETTERS.levels[1].score)!!.label)
        assertEquals("ممتاز", GradeScale.WORDS.levelOf(GradeScale.LETTERS.levelOf(excellent)!!.score)!!.label)
        val repeat = GradeScale.WORDS.levels.last().score
        assertEquals("🔴 يُعاد", GradeScale.LIGHTS.levelOf(repeat)!!.label)
        assertEquals("4", GradeScale.TEN.levelOf(repeat)!!.label)
        assertEquals("3 أخطاء", GradeScale.MISTAKES.levelOf(GradeScale.STARS.levelOf(70)!!.score)!!.label)
    }

    @Test
    fun oldGradesBecomeScores() {
        assertEquals(listOf("ممتاز", "جيد جداً", "جيد", "يُعاد"), (1..4).map { GradeScale.WORDS.levelOf(GradeScale.legacyScore(it))!!.label })
        assertEquals(0, GradeScale.legacyScore(0))
    }

    @Test
    fun averageIgnoresUngradedItems() {
        assertNull(GradeScale.average(listOf(0, 0)))
        assertEquals(90, GradeScale.average(listOf(95, 0, 85)))
        assertEquals("جيد جداً", GradeScale.WORDS.levelOf(GradeScale.average(listOf(95, 75))!!)!!.label)
    }
}
