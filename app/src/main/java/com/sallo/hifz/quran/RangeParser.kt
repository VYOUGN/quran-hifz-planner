package com.sallo.hifz.quran

/**
 * Reads back passages written the way [RangeFormatter] writes them, so a lesson or review typed by
 * hand still gets its pages: «العصر», «البقرة 6-16», «البقرة 7», «الرحمن - الواقعة»,
 * «البقرة 280 - آل عمران 5», several joined by «،». «سورة» before a name and Arabic digits are fine.
 */
object RangeParser {
    /** The ranges of [text], or null when any part of it is not a passage. */
    fun parse(text: String?): List<IntRange>? {
        if (text.isNullOrBlank()) return null
        val normal = text.map { if (it in '٠'..'٩') '0' + (it - '٠') else it }.joinToString("")
        val parts = normal.split('،', ',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        return parts.map { part(it) ?: return null }
    }

    private class Side(val surah: Int?, val from: Int?, val to: Int?)

    private val SIDE = Regex("""^(.*?)\s*(?:(\d+)(?:\s*-\s*(\d+))?)?$""")

    private fun side(text: String): Side? {
        val m = SIDE.matchEntire(text.trim()) ?: return null
        val name = m.groupValues[1].trim().removePrefix("سورة").trim()
        val surah = if (name.isEmpty()) null else surahNamed(name) ?: return null
        return Side(surah, m.groupValues[2].toIntOrNull(), m.groupValues[3].toIntOrNull())
    }

    private fun part(text: String): IntRange? {
        val sides = text.split(Regex("""\s+-\s+"""))
        return when (sides.size) {
            1 -> {
                val s = side(sides[0]) ?: return null
                val surah = s.surah ?: return null
                val from = s.from ?: 1
                val to = s.to ?: if (s.from == null) Quran.ayahCount(surah) else from
                span(surah, from, surah, to)
            }
            2 -> {
                var a = side(sides[0]) ?: return null
                var b = side(sides[1]) ?: return null
                // «القيامة - المزمل»: written backward, it is still from the earlier surah.
                if (a.surah != null && b.surah != null && b.surah!! < a.surah!!) a = b.also { b = a }
                val s1 = a.surah ?: return null
                if (a.to != null || b.to != null) return null
                val s2 = b.surah ?: s1
                if (b.surah == null && b.from == null) return null
                span(s1, a.from ?: 1, s2, b.from ?: Quran.ayahCount(s2))
            }
            else -> null
        }
    }

    private fun span(s1: Int, a1: Int, s2: Int, a2: Int): IntRange? {
        if (a1 !in 1..Quran.ayahCount(s1) || a2 !in 1..Quran.ayahCount(s2)) return null
        val x = Quran.index(s1, a1)
        val y = Quran.index(s2, a2)
        return minOf(x, y)..maxOf(x, y)
    }

    private val names: Map<String, Int> by lazy { (1..114).associateBy { Quran.surahName(it) } }

    private fun surahNamed(name: String): Int? = names[name.replace(Regex("""\s+"""), " ")]
}
