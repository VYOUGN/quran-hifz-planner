package com.sallo.hifz.quran

/** Arabic text for ayah ranges, in the style of the paper table. */
object RangeFormatter {
    const val NONE = "—"

    /**
     * «العصر» for a whole surah, «الرحمن - الواقعة» for whole surahs, «البقرة 6-16» inside a
     * surah, and «البقرة 280 - آل عمران 5» across surahs.
     */
    fun format(range: IntRange): String {
        val s1 = Quran.surahOf(range.first)
        val s2 = Quran.surahOf(range.last)
        val a1 = Quran.ayahOf(range.first)
        val a2 = Quran.ayahOf(range.last)
        val wholeStart = a1 == 1
        val wholeEnd = a2 == Quran.ayahCount(s2)
        if (s1 == s2) {
            val name = Quran.surahName(s1)
            return when {
                wholeStart && wholeEnd -> name
                a1 == a2 -> "$name $a1"
                else -> "$name $a1-$a2"
            }
        }
        val left = if (wholeStart) Quran.surahName(s1) else "${Quran.surahName(s1)} $a1"
        val right = if (wholeEnd) Quran.surahName(s2) else "${Quran.surahName(s2)} $a2"
        return "$left - $right"
    }

    fun formatAll(ranges: List<IntRange>): String =
        if (ranges.isEmpty()) NONE else ranges.joinToString("، ") { format(it) }

    /** «ص 604», «ص 22-23», or «ص 2 (النصف الأول)» for half-page lessons. */
    fun pageLabel(range: IntRange, halves: Boolean): String {
        val p1 = Quran.pageOf(range.first)
        val p2 = Quran.pageOf(range.last)
        if (p1 != p2) return "ص $p1-$p2"
        if (!halves) return "ص $p1"
        val page = Quran.pageRange(p1)
        return when {
            range.first == page.first && range.last == page.last -> "ص $p1"
            range.first == page.first -> "ص $p1 (النصف الأول)"
            range.last == page.last -> "ص $p1 (النصف الثاني)"
            else -> "ص $p1"
        }
    }

    /** Text for a span of whole surahs, in either order: «الرحمن - الواقعة». */
    fun surahSpan(a: Int, b: Int): String {
        val lo = minOf(a, b)
        val hi = maxOf(a, b)
        return format(Quran.surahRange(lo).first..Quran.surahRange(hi).last)
    }
}
