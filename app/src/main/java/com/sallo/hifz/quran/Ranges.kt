package com.sallo.hifz.quran

/** Helpers for lists of ayah ranges. */
object Ranges {
    /** Sorts and joins ranges that overlap or touch, so the result reads as few spans as possible. */
    fun merge(ranges: List<IntRange>): List<IntRange> {
        if (ranges.isEmpty()) return emptyList()
        val sorted = ranges.sortedBy { it.first }
        val out = ArrayList<IntRange>()
        var current = sorted[0]
        for (r in sorted.drop(1)) {
            current = if (r.first <= current.last + 1) {
                current.first..maxOf(current.last, r.last)
            } else {
                out += current
                r
            }
        }
        out += current
        return out
    }

    /** The ayahs of [ranges] that are not in [remove], merged and in order. */
    fun subtract(ranges: List<IntRange>, remove: List<IntRange>): List<IntRange> {
        var out = merge(ranges)
        for (r in merge(remove)) {
            out = out.flatMap { a ->
                if (r.last < a.first || r.first > a.last) {
                    listOf(a)
                } else {
                    listOf(a.first until r.first, (r.last + 1)..a.last).filterNot { it.isEmpty() }
                }
            }
        }
        return out
    }

    /** "a-b;c-d" */
    fun encode(ranges: List<IntRange>): String = ranges.joinToString(";") { "${it.first}-${it.last}" }

    fun decode(text: String): List<IntRange> =
        if (text.isBlank()) {
            emptyList()
        } else {
            text.split(';').map { part ->
                val (a, b) = part.split('-')
                a.toInt()..b.toInt()
            }
        }
}
