package com.sallo.hifz.plan

import com.sallo.hifz.quran.Quran
import java.time.LocalDate

/** A juz memorized whole, and the day its last lesson was done. */
data class JuzDone(val juz: Int, val date: LocalDate)

object Achievements {
    /** Which ayahs are in a done lesson. */
    fun covered(rows: List<StatRow>): BooleanArray {
        val covered = BooleanArray(Quran.AYAH_COUNT)
        for (r in rows) if (r.done) for (i in r.lesson) if (i in 0 until Quran.AYAH_COUNT) covered[i] = true
        return covered
    }

    /** The juz whose every ayah is in a done lesson, in order. */
    fun juzDone(rows: List<StatRow>): List<JuzDone> {
        val done = rows.filter { it.done }
        if (done.isEmpty()) return emptyList()
        val covered = covered(done)
        return (1..Quran.JUZ_COUNT).mapNotNull { juz ->
            val range = Quran.juzRange(juz)
            if (!range.all { covered[it] }) return@mapNotNull null
            val last = done.filter { it.lesson.first <= range.last && it.lesson.last >= range.first }.maxOf { it.date }
            JuzDone(juz, last)
        }
    }

    /** The covered ayahs as ranges, for counting pages. */
    fun ranges(covered: BooleanArray): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var start = -1
        for (i in covered.indices) {
            if (covered[i] && start < 0) start = i
            if (!covered[i] && start >= 0) {
                out += start until i
                start = -1
            }
        }
        if (start >= 0) out += start until covered.size
        return out
    }
}
