package com.sallo.hifz.quran

import kotlin.math.abs

/**
 * Structure of the Madani mushaf. Every ayah has a global 0-based index (0..6235) in mushaf
 * order, and a run of ayahs is an [IntRange] over that index.
 */
object Quran {
    const val AYAH_COUNT = 6236
    const val PAGE_COUNT = 604
    const val SURAH_COUNT = 114
    const val JUZ_COUNT = 30
    private const val LINES_PER_PAGE = 15

    private val surahStarts = IntArray(SURAH_COUNT + 1).also { starts ->
        for (s in 0 until SURAH_COUNT) starts[s + 1] = starts[s] + QuranData.SURAH_AYAHS[s]
    }
    private val juzStarts = IntArray(JUZ_COUNT + 1) { if (it < JUZ_COUNT) QuranData.JUZ_STARTS[it] else AYAH_COUNT }
    private val pageStarts: IntArray by lazy {
        val parts = QuranData.PAGE_STARTS.split(',')
        IntArray(PAGE_COUNT + 1) { if (it < PAGE_COUNT) parts[it].toInt() else AYAH_COUNT }
    }
    private val ayahLetters: IntArray by lazy {
        QuranData.AYAH_LETTERS.split(',').map { it.toInt() }.toIntArray()
    }

    /** quarterStarts[q - 1] is the first ayah of hizb quarter q; quarterStarts[240] == 6236. */
    private val quarterStarts: IntArray by lazy {
        val parts = QuranData.QUARTER_STARTS.split(',')
        IntArray(QUARTER_COUNT + 1) { if (it < QUARTER_COUNT) parts[it].toInt() else AYAH_COUNT }
    }

    /**
     * Share of its page taken by each ayah. Letters measure the text, and every surah header
     * (title line + basmala) counts as that many lines of text, so a page always sums to 1.
     */
    private val weights: DoubleArray by lazy {
        val w = DoubleArray(AYAH_COUNT)
        for (p in 1..PAGE_COUNT) {
            val r = pageRange(p)
            var headerLineCount = 0
            for (i in r) if (ayahOf(i) == 1) headerLineCount += headerLines(surahOf(i))
            val textLines = (LINES_PER_PAGE - headerLineCount).coerceAtLeast(3)
            val perLine = letters(r).toDouble() / textLines
            var total = 0.0
            for (i in r) {
                w[i] = ayahLetters[i] + if (ayahOf(i) == 1) headerLines(surahOf(i)) * perLine else 0.0
                total += w[i]
            }
            for (i in r) w[i] /= total
        }
        w
    }

    /** prefixWeights[x] = pages before position x (ayahs 0 until x). */
    private val prefixWeights: DoubleArray by lazy {
        val p = DoubleArray(AYAH_COUNT + 1)
        for (i in 0 until AYAH_COUNT) p[i + 1] = p[i] + weights[i]
        p
    }

    const val QUARTER_COUNT = 240
    const val HIZB_COUNT = 60

    fun quarterRange(quarter: Int): IntRange = quarterStarts[quarter - 1] until quarterStarts[quarter]
    fun hizbRange(hizb: Int): IntRange = quarterStarts[(hizb - 1) * 4] until quarterStarts[hizb * 4]
    fun quarterOf(index: Int): Int = bucket(quarterStarts, index)
    fun hizbOf(index: Int): Int = (quarterOf(index) - 1) / 4 + 1

    val surahs: List<IntRange> by lazy { (1..SURAH_COUNT).map { surahRange(it) } }
    val juzs: List<IntRange> by lazy { (1..JUZ_COUNT).map { juzRange(it) } }
    val hizbs: List<IntRange> by lazy { (1..HIZB_COUNT).map { hizbRange(it) } }
    val quarters: List<IntRange> by lazy { (1..QUARTER_COUNT).map { quarterRange(it) } }

    /** passageStarts[n - 1] is the first ayah of passage n; the last entry is 6236. */
    private val passageStarts: IntArray by lazy {
        val parts = QuranData.PASSAGE_STARTS.split(',').filter { it.isNotEmpty() }.map { it.toInt() }
        (parts + AYAH_COUNT).toIntArray()
    }

    /** Passages of «موسوعة التفسير» (dorar.net), in mushaf order; each lies inside one surah. */
    val passages: List<IntRange> by lazy { (1 until passageStarts.size).map { passageStarts[it - 1] until passageStarts[it] } }

    val hasPassages: Boolean get() = passageStarts.size > 1

    fun passageOf(index: Int): Int = bucket(passageStarts, index)

    /** Pages before [position] (a position sits between ayahs: 0..6236). */
    fun prefix(position: Int): Double = prefixWeights[position]

    /** Size of [range] in pages. */
    fun weight(range: IntRange): Double =
        if (range.isEmpty()) 0.0 else prefixWeights[range.last + 1] - prefixWeights[range.first]

    /** True when [position] is the start of a page (or the very end of the mushaf). */
    fun isPageStart(position: Int): Boolean =
        position == AYAH_COUNT || pageRange(pageOf(position)).first == position

    fun surahName(surah: Int): String = QuranData.SURAH_NAMES[surah - 1]
    fun ayahCount(surah: Int): Int = QuranData.SURAH_AYAHS[surah - 1]

    fun index(surah: Int, ayah: Int): Int {
        require(surah in 1..SURAH_COUNT) { "surah $surah" }
        require(ayah in 1..ayahCount(surah)) { "ayah $surah:$ayah" }
        return surahStarts[surah - 1] + ayah - 1
    }

    fun surahOf(index: Int): Int = bucket(surahStarts, index)
    fun ayahOf(index: Int): Int = index - surahStarts[surahOf(index) - 1] + 1
    fun pageOf(index: Int): Int = bucket(pageStarts, index)
    fun juzOf(index: Int): Int = bucket(juzStarts, index)

    fun surahRange(surah: Int): IntRange = surahStarts[surah - 1] until surahStarts[surah]
    fun pageRange(page: Int): IntRange = pageStarts[page - 1] until pageStarts[page]
    fun juzRange(juz: Int): IntRange = juzStarts[juz - 1] until juzStarts[juz]

    /** Name of the surah where [page] starts, for page pickers. */
    fun surahAtPage(page: Int): String = surahName(surahOf(pageRange(page).first))

    fun letters(index: Int): Int = ayahLetters[index]
    fun letters(range: IntRange): Int = range.sumOf { ayahLetters[it] }

    /** Size of [range] in pages. */
    fun pages(range: IntRange): Double = weight(range)

    /**
     * Approximate half-page boundary: the index of the first ayah of the page's second half, or
     * null when the page holds a single ayah. Ayahs weigh their letter count, and each surah
     * header (title line + basmala) weighs as many lines of text.
     */
    fun halfPageSplit(page: Int): Int? {
        val r = pageRange(page)
        if (r.last <= r.first) return null
        var headerLineCount = 0
        for (i in r) if (ayahOf(i) == 1) headerLineCount += headerLines(surahOf(i))
        val textLines = (LINES_PER_PAGE - headerLineCount).coerceAtLeast(3)
        val perLine = letters(r).toDouble() / textLines
        fun weight(i: Int): Double =
            ayahLetters[i] + if (ayahOf(i) == 1) headerLines(surahOf(i)) * perLine else 0.0

        val half = r.sumOf { weight(it) } / 2
        var best = r.first + 1
        var bestDiff = Double.MAX_VALUE
        var before = 0.0
        for (k in r.first + 1..r.last) {
            before += weight(k - 1)
            val diff = abs(before - half)
            if (diff < bestDiff) {
                bestDiff = diff
                best = k
            }
        }
        return best
    }

    /** Al-Fatiha's basmala is its first ayah and At-Tawbah has none, so both headers are one line. */
    private fun headerLines(surah: Int): Int = if (surah == 1 || surah == 9) 1 else 2

    /** 1-based bucket b with starts[b - 1] <= value < starts[b]. */
    private fun bucket(starts: IntArray, value: Int): Int {
        require(value in 0 until AYAH_COUNT) { "index $value" }
        var lo = 0
        var hi = starts.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (starts[mid] <= value) lo = mid else hi = mid
        }
        return lo + 1
    }
}
