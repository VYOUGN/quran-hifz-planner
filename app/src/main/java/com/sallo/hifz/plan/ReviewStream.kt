package com.sallo.hifz.plan

import kotlin.math.max
import kotlin.math.min

/**
 * The sessions of a major review that goes round and round. A round is a list of stretches in reading
 * order ([backward]: each stretch is read from its end down, like a backward range).
 *
 * With [wraps] («كمّل من الدورة الجاية»), a session that reaches the end of a stretch before its amount
 * is filled up from the next stretch, or from the start of the next round: with five pages a session
 * and two pages left in a round, the last session reviews those two and the first three of the next
 * round, and the next round goes on from there. Without it every stretch is split on its own, so a
 * round's last session is what is left of it.
 */
class ReviewStream(private val p: PortionSpec, private val backward: Boolean, private val wraps: Boolean) {
    /** Sessions planned in the current stretch. */
    private val ahead = ArrayDeque<IntRange>()

    /** Stretches after it in the current round. */
    private val stretches = ArrayDeque<IntRange>()

    /** Makes [round] (stretches in reading order) the round in progress. */
    fun start(round: List<IntRange>) {
        ahead.clear()
        stretches.clear()
        round.filterNotTo(stretches) { it.isEmpty() }
    }

    /**
     * The next session as its parts in reading order (the last part is where the review now stands),
     * or empty when there is nothing to review. [nextRound] gives the stretches of a round that starts
     * with this session; it is called at most once.
     */
    fun next(nextRound: () -> List<IntRange>): List<IntRange> {
        var started = false
        if (ahead.isEmpty() && stretches.isEmpty()) {
            start(nextRound())
            started = true
        }
        while (ahead.isEmpty()) {
            val stretch = stretches.removeFirstOrNull() ?: return emptyList()
            ahead += sweep(stretch)
        }
        val parts = mutableListOf(ahead.removeFirst())
        if (!wraps) return parts
        // The session ran into the end of its stretch: fill it up from what comes next.
        while (ahead.isEmpty()) {
            val missing = PortionGenerator.missing(p, parts) ?: break
            if (stretches.isEmpty()) {
                if (started) break
                start(nextRound())
                started = true
            }
            val stretch = stretches.firstOrNull() ?: break
            // Never the same ayahs twice in one session: a short round stays short.
            val free = free(stretch, parts) ?: break
            val head = sweep(free, first = missing).firstOrNull() ?: break
            stretches.removeFirst()
            parts += head
            val rest = if (backward) stretch.first until head.first else (head.last + 1)..stretch.last
            if (!rest.isEmpty()) ahead += sweep(rest)
        }
        return parts
    }

    /** A short session at the end of a stretch is filled up from the next one, so it is not folded into the one before. */
    private fun sweep(stretch: IntRange, first: Double? = null): List<IntRange> =
        PortionGenerator.sweep(p, stretch, backward, first, fold = !wraps)

    /** The beginning of [stretch] (in reading order) up to the first ayah [parts] already have, or null. */
    private fun free(stretch: IntRange, parts: List<IntRange>): IntRange? {
        var r = stretch
        for (q in parts) {
            if (q.last < r.first || q.first > r.last) continue
            r = if (backward) (q.last + 1)..r.last else r.first until q.first
            if (r.isEmpty()) return null
        }
        return r
    }

    companion object {
        /**
         * [count] sessions going round [p]'s range again and again, each round's last session filled up
         * from the next, carrying on after [last] when given.
         */
        fun cycle(p: PortionSpec, last: IntRange?, count: Int): List<List<IntRange>> {
            val span = PortionGenerator.span(p)
            val backward = p.range.isBackward
            val stream = ReviewStream(p, backward, wraps = true)
            if (last != null) {
                val rest = if (backward) span.first..min(span.last, last.first - 1) else max(span.first, last.last + 1)..span.last
                stream.start(listOf(rest))
            }
            return List(count) { stream.next { listOf(span) } }
        }
    }
}
