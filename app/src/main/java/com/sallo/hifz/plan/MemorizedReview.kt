package com.sallo.hifz.plan

import com.sallo.hifz.quran.Ranges

/**
 * «من محفوظي»: the major review goes round everything memorized so far, in mushaf order, leaving out
 * what the minor review covers. A round is planned when it starts, so it takes in the lessons
 * memorized during the round before. Memorizing backward from Al-Haqqah with Al-Ma'arij to An-Nas
 * already memorized, the first round is Al-Ma'arij to An-Nas; if Al-Mulk is being memorized when
 * it ends, the next round starts at Al-Qalam (or before the minor review's lessons) and runs to An-Nas.
 */
object MemorizedReview {
    /**
     * The major review of a run of sessions: session j comes when lessons before [positions][j] are
     * memorized and its minor review is [minors][j]. Each session is its parts in reading order (more
     * than one when a round's last session is filled up from the next round), or empty where there is
     * nothing to review yet. [last] is the major review just before them, so a round in progress
     * carries on after it.
     */
    fun plan(lessons: List<IntRange>, positions: List<Int>, minors: List<List<IntRange>>, s: PlanSettings, last: IntRange?): List<List<IntRange>> {
        val stream = ReviewStream(s.majorPortion, backward = false, wraps = s.wrapsMajor)
        if (last != null && minors.isNotEmpty()) {
            stream.start(coverage(lessons, positions[0], minors[0], s).map { maxOf(it.first, last.last + 1)..it.last })
        }
        return minors.mapIndexed { j, minor -> stream.next { coverage(lessons, positions[j], minor, s) } }
    }

    /**
     * What a round starting with lesson [i] goes over: the earlier memorization and the lessons before
     * [i], without the minor review and lesson [i] itself.
     */
    fun coverage(lessons: List<IntRange>, i: Int, minor: List<IntRange>, s: PlanSettings): List<IntRange> {
        val memorized = listOfNotNull(s.priorMemorized) + lessons.subList(0, i.coerceIn(0, lessons.size))
        return Ranges.subtract(memorized, minor + listOfNotNull(lessons.getOrNull(i)))
    }
}
