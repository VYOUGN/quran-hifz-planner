package com.sallo.hifz.plan

import com.sallo.hifz.quran.Quran
import com.sallo.hifz.quran.Ranges

/** What the minor review (المراجعة الصغرى) covers in each session. */
object MinorReview {
    /**
     * Ayah ranges to review in session [i], built from the lessons before it:
     * LAST_N takes the last [count] lessons; CUMULATIVE_JUZ takes every earlier lesson in the
     * same juz as lesson [i], so it starts over with each juz.
     */
    fun window(lessons: List<IntRange>, i: Int, mode: MinorMode, count: Int): List<IntRange> {
        if (i <= 0 || i >= lessons.size) return emptyList()
        val from = when (mode) {
            MinorMode.LAST_N -> (i - count.coerceAtLeast(0)).coerceAtLeast(0)
            MinorMode.CUMULATIVE_JUZ -> {
                val juz = Quran.juzOf(lessons[i].first)
                var j = i
                while (j > 0 && Quran.juzOf(lessons[j - 1].first) == juz) j--
                j
            }
        }
        return Ranges.merge(lessons.subList(from, i))
    }
}
