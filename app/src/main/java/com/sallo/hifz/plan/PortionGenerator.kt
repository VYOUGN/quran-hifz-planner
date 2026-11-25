package com.sallo.hifz.plan

import com.sallo.hifz.quran.Quran
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Splits a [PortionSpec] into sessions. Every session is one contiguous ayah range. */
object PortionGenerator {
    private const val EPS = 1e-9

    /** A remainder smaller than this share of a session is folded into the session before it. */
    private const val SMALL_REMAINDER = 0.3

    /** A surah that begins above this share of a page begins «at the top» of it. */
    private const val TOP_OF_PAGE = 1.0 / 3

    /** A page session short by less than this (in pages) is full enough, and is not filled up. */
    private const val MIN_FILL = 0.5

    /**
     * Sessions in reading order. With [sessions], the range is divided into at most that many
     * sessions instead of using the portion's amount («حسب تاريخ الختم»).
     */
    fun generate(p: PortionSpec, sessions: Int? = null): List<IntRange> {
        if (sessions != null && sessions <= 0) return emptyList()
        return split(p, p.range.bounds(), p.range.isBackward, p.resume ?: p.range.startResume(), sessions)
    }

    /** Sessions over [bounds] in mushaf order, sized like [p]; [p]'s own range and resume are not used. */
    fun over(p: PortionSpec, bounds: IntRange): List<IntRange> = split(p, bounds, false, null, null)

    /**
     * Sessions over [bounds], sized like [p] ([backward]: from the end of [bounds] down). With [first], the
     * first session is only that much (in [p]'s unit), as it fills up a session begun before [bounds].
     * Without [fold], a small remainder at the end stays a session of its own (to be filled up later)
     * instead of joining the session before it.
     */
    fun sweep(p: PortionSpec, bounds: IntRange, backward: Boolean, first: Double? = null, fold: Boolean = true): List<IntRange> =
        split(p, bounds, backward, null, null, first, fold)

    /** The ayahs [p]'s range goes over, from where it starts (a backward ayah range may start inside a surah). */
    fun span(p: PortionSpec): IntRange {
        val (lo, hi) = clip(p.range.bounds(), p.range.isBackward, p.range.startResume())
        return lo..hi
    }

    /**
     * How much a session made of [parts] lacks of a full session of [p], or null when it is (nearly) full.
     * Sessions that follow the surahs are never filled up.
     */
    fun missing(p: PortionSpec, parts: List<IntRange>): Double? = when {
        !p.unit.wraps -> null
        p.unit == MemUnit.PAGE -> (p.amount - parts.sumOf { Quran.weight(it) }).takeIf { it >= min(MIN_FILL, p.amount / 2) }
        else -> (p.amount.roundToInt().coerceAtLeast(1) - parts.sumOf { unitsIn(p.unit, it) }).takeIf { it >= 1 }?.toDouble()
    }

    /** Quarters, hizbs, juz or surahs that [range] reaches into. */
    private fun unitsIn(unit: MemUnit, range: IntRange): Int = when (unit) {
        MemUnit.QUARTER -> Quran.quarterOf(range.last) - Quran.quarterOf(range.first) + 1
        MemUnit.HIZB -> Quran.hizbOf(range.last) - Quran.hizbOf(range.first) + 1
        MemUnit.JUZ -> Quran.juzOf(range.last) - Quran.juzOf(range.first) + 1
        else -> Quran.surahOf(range.last) - Quran.surahOf(range.first) + 1
    }

    private fun split(
        p: PortionSpec,
        bounds: IntRange,
        backward: Boolean,
        resume: Int?,
        sessions: Int?,
        first: Double? = null,
        fold: Boolean = true,
    ): List<IntRange> {
        val n = p.amount.roundToInt().coerceAtLeast(1)
        return when (p.unit) {
            MemUnit.AYAH -> ayahs(bounds, backward, resume, n, sessions)
            MemUnit.PASSAGE -> passages(bounds, backward, resume, n, sessions)
            MemUnit.PAGE -> pages(bounds, backward, resume, p.amount, p.rounding, p.completeSurah, sessions, first, fold)
            else -> units(p.unit, bounds, backward, resume, n, sessions, first?.roundToInt()?.coerceIn(1, n))
        }
    }

    /** What is left to cover, as ranges (used for page totals). */
    fun remaining(p: PortionSpec): List<IntRange> {
        val bounds = p.range.bounds()
        val backward = p.range.isBackward
        val resume = p.resume ?: p.range.startResume()
        if (p.unit.bySurah) return surahAtoms(bounds, backward, resume)
        val (lo, hi) = clip(bounds, backward, resume)
        return if (lo <= hi) listOf(lo..hi) else emptyList()
    }

    /** The resume value for the session after [last], or null when [last] ends the range. */
    fun nextResume(p: PortionSpec, last: IntRange): Int? {
        val b = p.range.bounds()
        if (!p.range.isBackward) return (last.last + 1).takeIf { it <= b.last }
        if (p.unit.bySurah) {
            val surah = Quran.surahOf(last.last)
            val surahEnd = min(Quran.surahRange(surah).last, b.last)
            if (last.last < surahEnd) return last.last + 1
            val previous = Quran.surahOf(last.first) - 1
            return if (previous >= Quran.surahOf(b.first)) max(Quran.surahRange(previous).first, b.first) else null
        }
        return (last.first - 1).takeIf { it >= b.first }
    }

    // ----- ayahs: never across a surah; short surahs are a whole session -----

    private fun surahAtoms(bounds: IntRange, backward: Boolean, resume: Int?): List<IntRange> {
        val first = Quran.surahOf(bounds.first)
        val last = Quran.surahOf(bounds.last)
        val order = if (backward) (last downTo first) else (first..last)
        var atoms = order.map { s ->
            val r = Quran.surahRange(s)
            max(r.first, bounds.first)..min(r.last, bounds.last)
        }
        if (resume != null) {
            val i = atoms.indexOfFirst { resume in it }
            if (i >= 0) atoms = listOf(resume..atoms[i].last) + atoms.drop(i + 1)
        }
        return atoms
    }

    private fun ayahs(bounds: IntRange, backward: Boolean, resume: Int?, n: Int, sessions: Int?): List<IntRange> {
        val atoms = surahAtoms(bounds, backward, resume)
        if (atoms.isEmpty()) return emptyList()
        if (sessions == null) return atoms.flatMap { balanced(it, n) }
        // Finish within [sessions]: grow the size, letting whole short surahs share a session.
        val total = atoms.sumOf { it.count() }
        var size = max(1, ceil(total.toDouble() / sessions).toInt())
        while (true) {
            val out = mergedChunks(atoms, size)
            if (out.size <= sessions || size >= total) return out
            size = max(size + 1, (size * 1.1).toInt())
        }
    }

    /** round(length / n) nearly equal sessions, so no session is a lone leftover ayah. */
    internal fun balanced(atom: IntRange, n: Int): List<IntRange> {
        val length = atom.count()
        val chunks = ((2 * length + n) / (2 * n)).coerceAtLeast(1)
        val out = ArrayList<IntRange>(chunks)
        var start = atom.first
        for (c in 0 until chunks) {
            val size = length / chunks + if (c < length % chunks) 1 else 0
            out += start..(start + size - 1)
            start += size
        }
        return out
    }

    private fun mergedChunks(atoms: List<IntRange>, n: Int): List<IntRange> {
        val out = ArrayList<IntRange>()
        var group: IntRange? = null
        for (a in atoms) {
            val g = group
            if (a.count() > n) {
                if (g != null) out += g
                group = null
                out += balanced(a, n)
                continue
            }
            group = when {
                g == null -> a
                (g.last + 1 == a.first || a.last + 1 == g.first) && (g.count() + a.count()) <= n ->
                    min(g.first, a.first)..max(g.last, a.last)
                else -> {
                    out += g
                    a
                }
            }
        }
        group?.let { out += it }
        return out
    }

    // ----- tafsir passages: whole passages, each surah from its beginning -----

    private fun passages(bounds: IntRange, backward: Boolean, resume: Int?, n: Int, sessions: Int?): List<IntRange> {
        val all = Quran.passages
        // The passages of each surah in reading order, cut to the range.
        val atoms = surahAtoms(bounds, backward, resume).flatMap { surah ->
            buildList {
                var i = Quran.passageOf(surah.first) - 1
                while (i < all.size && all[i].first <= surah.last) {
                    add(max(all[i].first, surah.first)..min(all[i].last, surah.last))
                    i++
                }
            }
        }
        if (atoms.isEmpty()) return emptyList()
        if (sessions == null) return joinedGroups(atoms, n)
        // Finish within [sessions]: take more passages per session until they fit.
        var size = max(1, ceil(atoms.size.toDouble() / sessions).toInt())
        while (true) {
            val out = joinedGroups(atoms, size)
            if (out.size <= sessions || size >= atoms.size) return out
            size++
        }
    }

    /** Up to [n] passages per session, as long as they join into one stretch of the mushaf. */
    private fun joinedGroups(atoms: List<IntRange>, n: Int): List<IntRange> {
        val out = ArrayList<IntRange>()
        var group: IntRange? = null
        var count = 0
        for (a in atoms) {
            val g = group
            group = if (g != null && count < n && (g.last + 1 == a.first || a.last + 1 == g.first)) {
                count++
                min(g.first, a.first)..max(g.last, a.last)
            } else {
                g?.let { out += it }
                count = 1
                a
            }
        }
        group?.let { out += it }
        return out
    }

    // ----- pages: measured by page weight, ends adjusted by the rounding rule -----

    private fun clip(bounds: IntRange, backward: Boolean, resume: Int?): Pair<Int, Int> {
        var lo = bounds.first
        var hi = bounds.last
        if (resume != null && resume in bounds) {
            if (backward) hi = resume else lo = resume
        }
        return lo to hi
    }

    private fun pages(
        bounds: IntRange,
        backward: Boolean,
        resume: Int?,
        amount: Double,
        rounding: Rounding,
        completeSurah: Boolean,
        sessions: Int?,
        first: Double? = null,
        fold: Boolean = true,
    ): List<IntRange> {
        val (lo, hi) = clip(bounds, backward, resume)
        if (lo > hi) return emptyList()
        val total = Quran.weight(lo..hi)
        var step = if (sessions != null) total / sessions else amount
        if (step <= EPS || total <= EPS) return listOf(lo..hi)
        // Page ends and surah ends only matter for sessions of a page or more.
        val whole = step >= 1.0 - EPS
        val snapping = if (whole) rounding else Rounding.NONE
        val completing = whole && completeSurah
        // Positions sit between ayahs. Forward sweeps go up from lo; backward sweeps go down from hi + 1.
        val start = if (backward) hi + 1 else lo
        val end = if (backward) lo else hi + 1
        val out = ArrayList<IntRange>()
        var prev = start
        // Targets are cumulative from an anchor, so rounding never drifts the plan. The anchor moves to
        // the first session's end when that is a page end, so a range that starts mid-page has
        // page-aligned sessions. A session stretched to its surah's end moves the anchor there, and
        // the next page end after it moves the anchor again. A shorter [first] session moves it too.
        var anchor = start
        var midPage = false
        var k = 1
        while (prev != end) {
            var cut = if (sessions != null && out.size == sessions - 1) {
                end
            } else {
                val ahead = if (first != null && out.isEmpty()) first else k * step
                val target = if (backward) Quran.prefix(anchor) - ahead else Quran.prefix(anchor) + ahead
                val raw = if (backward) nearest(target, lo, prev - 1) else nearest(target, prev + 1, hi + 1)
                snap(raw, lo, hi, backward, prev, snapping)
            }
            if (backward) {
                if (cut >= prev) cut = prev - 1
            } else {
                if (cut <= prev) cut = prev + 1
            }
            val unadjusted = cut
            if (completing && cut != end) cut = surahBoundary(cut, prev, lo, hi, backward)
            val adjusted = cut != unadjusted
            val rest = if (backward) Quran.weight(lo until cut) else Quran.weight(cut..hi)
            if (fold && rest < SMALL_REMAINDER * step) cut = end
            out += if (backward) cut until prev else prev until cut
            prev = cut
            val realign = cut != end &&
                (adjusted || (first != null && out.size == 1) || (Quran.isPageStart(cut) && (out.size == 1 || midPage)))
            if (realign) {
                anchor = cut
                k = 1
                midPage = !Quran.isPageStart(cut)
                if (sessions != null && sessions > out.size) step = rest / (sessions - out.size)
            } else {
                k++
            }
        }
        return out
    }

    /**
     * «راعِ حدود السور»: where a session that would stop inside a surah ends instead.
     * - A surah that begins in the middle or at the bottom of the session's last page is left whole for
     *   the next session; one that begins in the top third of that page stays in.
     * - Otherwise the session runs on to the end of the surah when that end is on the next page.
     * Going backward the session's first ayah is the loose end, so the mirror image applies: the short end
     * of a surah at the top of the first page is left for the next session (which has its beginning), and
     * a surah whose beginning is on the page before is started from its beginning.
     */
    private fun surahBoundary(cut: Int, prev: Int, lo: Int, hi: Int, backward: Boolean): Int {
        if (Quran.ayahOf(cut) == 1) return cut
        val surah = Quran.surahRange(Quran.surahOf(cut))
        return if (backward) {
            val firstPage = Quran.pageOf(cut)
            val endsHigh = surah.last + 1 < prev && Quran.pageOf(surah.last) == firstPage &&
                downThePage(surah.last + 1, firstPage) <= 1 - TOP_OF_PAGE
            when {
                endsHigh -> surah.last + 1
                Quran.pageOf(surah.first) >= firstPage - 1 -> max(surah.first, lo)
                else -> cut
            }
        } else {
            val lastPage = Quran.pageOf(cut - 1)
            val beginsLow = surah.first > prev && Quran.pageOf(surah.first) == lastPage &&
                downThePage(surah.first, lastPage) >= TOP_OF_PAGE
            when {
                beginsLow -> surah.first
                Quran.pageOf(surah.last) <= lastPage + 1 -> min(surah.last + 1, hi + 1)
                else -> cut
            }
        }
    }

    /** How far down [page] the position sits: 0 at its top, 1 at its bottom. */
    private fun downThePage(position: Int, page: Int): Double = Quran.prefix(position) - Quran.prefix(Quran.pageRange(page).first)

    /** Position in [from]..[to] whose page prefix is closest to [target]. */
    private fun nearest(target: Double, from: Int, to: Int): Int {
        var a = from
        var b = to
        while (a < b) {
            val m = (a + b) ushr 1
            if (Quran.prefix(m) < target) a = m + 1 else b = m
        }
        if (a > from && abs(Quran.prefix(a - 1) - target) < abs(Quran.prefix(a) - target)) return a - 1
        return a
    }

    /** Moves a cut to a page end according to [rounding], keeping the session non-empty. */
    private fun snap(cut: Int, lo: Int, hi: Int, backward: Boolean, prev: Int, rounding: Rounding): Int {
        if (rounding == Rounding.NONE) return cut
        if (backward && cut <= lo) return cut
        if (!backward && cut > hi) return cut
        if (Quran.isPageStart(cut)) return cut
        val page = Quran.pageRange(Quran.pageOf(cut))
        val below = page.first
        val above = page.last + 1
        val less = if (backward) above else below
        val more = if (backward) max(below, lo) else min(above, hi + 1)
        val lessOk = if (backward) less < prev else less > prev
        return when (rounding) {
            Rounding.DOWN -> if (lessOk) less else cut
            Rounding.UP -> more
            Rounding.NEAREST -> {
                val included = if (backward) Quran.prefix(above) - Quran.prefix(cut) else Quran.prefix(cut) - Quran.prefix(below)
                val missing = Quran.weight(page) - included
                if (included >= missing || !lessOk) more else less
            }
            Rounding.NONE -> cut
        }
    }

    // ----- quarters, hizbs, juz and surahs: whole units -----

    private fun units(unit: MemUnit, bounds: IntRange, backward: Boolean, resume: Int?, n: Int, sessions: Int?, first: Int? = null): List<IntRange> {
        val (lo, hi) = clip(bounds, backward, resume)
        if (lo > hi) return emptyList()
        val all = when (unit) {
            MemUnit.QUARTER -> Quran.quarters
            MemUnit.HIZB -> Quran.hizbs
            MemUnit.JUZ -> Quran.juzs
            else -> Quran.surahs
        }
        val atoms = all.filter { it.last >= lo && it.first <= hi }.map { max(it.first, lo)..min(it.last, hi) }
        val ordered = if (backward) atoms.asReversed() else atoms
        val groups = when {
            sessions != null -> balancedGroups(ordered, min(sessions, ordered.size))
            first != null -> (listOf(ordered.take(first)) + ordered.drop(first).chunked(n)).filter { it.isNotEmpty() }
            else -> ordered.chunked(n)
        }
        return groups.map { g -> g.minOf { it.first }..g.maxOf { it.last } }
    }

    private fun <T> balancedGroups(items: List<T>, groups: Int): List<List<T>> {
        if (groups <= 0) return emptyList()
        val out = ArrayList<List<T>>(groups)
        var start = 0
        for (g in 0 until groups) {
            val size = items.size / groups + if (g < items.size % groups) 1 else 0
            out += items.subList(start, start + size)
            start += size
        }
        return out
    }
}
