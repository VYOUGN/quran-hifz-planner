package com.sallo.hifz.plan

import com.sallo.hifz.quran.Quran

/** What a session's amount is measured in («القسم»). */
enum class MemUnit(val label: String) {
    AYAH("آيات"),
    PAGE("أوجه"),
    QUARTER("أرباع"),
    HIZB("أحزاب"),
    JUZ("أجزاء"),
    SURAH("سور"),

    /** Passages of «موسوعة التفسير» in الدرر السنية, e.g. المعارج 1-7، 8-18، 19-35، 36-44. */
    PASSAGE("مقاطع التفسير");

    val maxAmount: Int
        get() = when (this) {
            AYAH -> 300
            PAGE -> 60
            QUARTER -> Quran.QUARTER_COUNT
            HIZB -> Quran.HIZB_COUNT
            JUZ -> Quran.JUZ_COUNT
            SURAH -> Quran.SURAH_COUNT
            PASSAGE -> 20
        }

    /**
     * Sessions stay inside a surah and every surah is read from its beginning, even when the plan
     * goes backward through the mushaf; so a backward plan resumes at the first ayah still to read.
     */
    val bySurah: Boolean get() = this == AYAH || this == PASSAGE

    /**
     * A review session that runs short at the end of a round can be filled up from the next round
     * («كمّل من الدورة الجاية»). Sessions that follow the surahs cannot.
     */
    val wraps: Boolean get() = !bySurah

    val defaultAmount: Double
        get() = when (this) {
            AYAH -> 10.0
            PAGE -> 1.0
            else -> 1.0
        }
}

/** Kinds of range («المدى»). Two-ended kinds run backward when the start is after the end. */
enum class RangeType(val label: String, val twoEnds: Boolean) {
    WHOLE("القرآن كاملاً", false),
    SURAH("سورة", false),
    SURAHS("من سورة إلى سورة", true),
    JUZ("جزء", false),
    JUZS("من جزء إلى جزء", true),
    HIZB("حزب", false),
    HIZBS("من حزب إلى حزب", true),
    PAGES("من صفحة إلى صفحة", true),
    AYAHS("من آية إلى آية", true);

    /** Highest number a range end can take. */
    val maxNumber: Int
        get() = when (this) {
            WHOLE -> 1
            SURAH, SURAHS, AYAHS -> Quran.SURAH_COUNT
            JUZ, JUZS -> Quran.JUZ_COUNT
            HIZB, HIZBS -> Quran.HIZB_COUNT
            PAGES -> Quran.PAGE_COUNT
        }
}

/** Where a session ends when it does not end at a page end («السلوك التقريبي»). */
enum class Rounding(val label: String, val hint: String) {
    NONE("لا تعديل", "تبقى الكمية زي ما هي، حتى لو ما انتهت الجلسة عند آخر الصفحة"),
    DOWN("تقليل", "إذا ما انتهت الجلسة عند آخر الصفحة، تقل الكمية لين آخر صفحة كاملة"),
    NEAREST("الأقرب", "إذا ما انتهت الجلسة عند آخر الصفحة، تزيد أو تقل لأقرب نهاية صفحة"),
    UP("زيادة", "إذا ما انتهت الجلسة عند آخر الصفحة، تزيد الكمية لين تكمل الصفحة"),
}

data class RangeSpec(
    val type: RangeType = RangeType.WHOLE,
    /** Surah, juz, hizb or page number where the range starts (the surah for AYAHS). */
    val from: Int = 1,
    /** Where it ends; only for two-ended kinds. */
    val to: Int = 1,
    val fromAyah: Int = 1,
    val toAyah: Int = 1,
) {
    val isBackward: Boolean get() = type.twoEnds && from > to

    /** Lowest and highest ayah index the range covers. */
    fun bounds(): IntRange {
        val lo = minOf(from, to)
        val hi = maxOf(from, to)
        return when (type) {
            RangeType.WHOLE -> 0 until Quran.AYAH_COUNT
            RangeType.SURAH -> Quran.surahRange(from)
            RangeType.SURAHS -> Quran.surahRange(lo).first..Quran.surahRange(hi).last
            RangeType.JUZ -> Quran.juzRange(from)
            RangeType.JUZS -> Quran.juzRange(lo).first..Quran.juzRange(hi).last
            RangeType.HIZB -> Quran.hizbRange(from)
            RangeType.HIZBS -> Quran.hizbRange(lo).first..Quran.hizbRange(hi).last
            RangeType.PAGES -> Quran.pageRange(lo).first..Quran.pageRange(hi).last
            RangeType.AYAHS ->
                if (isBackward) {
                    Quran.surahRange(to).first..Quran.surahRange(from).last
                } else {
                    Quran.index(from, fromAyah)..Quran.index(to, toAyah)
                }
        }
    }

    /** A backward ayah range may start partway into its first surah. */
    fun startResume(): Int? =
        if (type == RangeType.AYAHS && isBackward && fromAyah > 1) Quran.index(from, fromAyah) else null

    /** Arabic error message, or null. */
    fun validate(): String? {
        if (type == RangeType.WHOLE) return null
        val max = type.maxNumber
        if (from !in 1..max) return "رقم البداية من 1 إلى $max"
        if (type.twoEnds && to !in 1..max) return "رقم النهاية من 1 إلى $max"
        if (type == RangeType.AYAHS) {
            if (fromAyah !in 1..Quran.ayahCount(from)) return "رقم آية البداية غير موجود في السورة"
            if (!isBackward) {
                if (toAyah !in 1..Quran.ayahCount(to)) return "رقم آية النهاية غير موجود في السورة"
                if (Quran.index(from, fromAyah) > Quran.index(to, toAyah)) return "بداية المدى بعد نهايته"
            }
        }
        return null
    }

    /** A range of kind [t] covering about the same span, in the same direction. */
    fun convertTo(t: RangeType): RangeSpec {
        if (t == type) return this
        val b = bounds()
        val startIndex = if (isBackward) b.last else b.first
        val endIndex = if (isBackward) b.first else b.last
        fun number(i: Int): Int = when (t) {
            RangeType.SURAH, RangeType.SURAHS, RangeType.AYAHS -> Quran.surahOf(i)
            RangeType.JUZ, RangeType.JUZS -> Quran.juzOf(i)
            RangeType.HIZB, RangeType.HIZBS -> Quran.hizbOf(i)
            RangeType.PAGES -> Quran.pageOf(i)
            RangeType.WHOLE -> 1
        }
        return when (t) {
            RangeType.WHOLE -> RangeSpec(RangeType.WHOLE)
            RangeType.SURAH, RangeType.JUZ, RangeType.HIZB -> RangeSpec(t, number(startIndex), number(startIndex))
            RangeType.SURAHS, RangeType.JUZS, RangeType.HIZBS, RangeType.PAGES ->
                RangeSpec(t, number(startIndex), number(endIndex))
            RangeType.AYAHS ->
                if (isBackward) {
                    RangeSpec(t, Quran.surahOf(startIndex), Quran.surahOf(endIndex), 1, 1)
                } else {
                    RangeSpec(t, Quran.surahOf(startIndex), Quran.surahOf(endIndex), Quran.ayahOf(startIndex), Quran.ayahOf(endIndex))
                }
        }
    }

    fun describe(): String = when (type) {
        RangeType.WHOLE -> "القرآن كاملاً"
        RangeType.SURAH -> "سورة ${Quran.surahName(from)}"
        RangeType.SURAHS -> "من ${Quran.surahName(from)} إلى ${Quran.surahName(to)}"
        RangeType.JUZ -> "الجزء $from"
        RangeType.JUZS -> "من الجزء $from إلى الجزء $to"
        RangeType.HIZB -> "الحزب $from"
        RangeType.HIZBS -> "من الحزب $from إلى الحزب $to"
        RangeType.PAGES -> "من ص $from إلى ص $to"
        RangeType.AYAHS -> {
            val start = if (fromAyah == 1) Quran.surahName(from) else "${Quran.surahName(from)} $fromAyah"
            val end = if (isBackward || toAyah == Quran.ayahCount(to)) Quran.surahName(to) else "${Quran.surahName(to)} $toAyah"
            "من $start إلى $end"
        }
    }

    companion object {
        /** The simplest forward range over exactly [r]: the whole Quran, a surah, whole surahs, or ayahs. */
        fun covering(r: IntRange): RangeSpec {
            val first = Quran.surahOf(r.first)
            val last = Quran.surahOf(r.last)
            val wholeSurahs = Quran.ayahOf(r.first) == 1 && r.last == Quran.surahRange(last).last
            return when {
                r.first == 0 && r.last == Quran.AYAH_COUNT - 1 -> RangeSpec(RangeType.WHOLE)
                wholeSurahs && first == last -> RangeSpec(RangeType.SURAH, first, first)
                wholeSurahs -> RangeSpec(RangeType.SURAHS, first, last)
                else -> RangeSpec(RangeType.AYAHS, first, last, Quran.ayahOf(r.first), Quran.ayahOf(r.last))
            }
        }
    }
}

/** One track of the table: how much per session («القسم») over which range («المدى»). */
data class PortionSpec(
    val unit: MemUnit = MemUnit.AYAH,
    val amount: Double = 10.0,
    val range: RangeSpec = RangeSpec(),
    val rounding: Rounding = Rounding.NEAREST,
    /**
     * Where to continue after editing a plan with progress. Forward, and for ayah sessions, it is the
     * first ayah still to read; in other backward ranges it is the highest ayah still to read.
     */
    val resume: Int? = null,
    /**
     * «راعِ حدود السور», for page sessions: a surah that begins in the middle or at the bottom of the
     * session's last page waits for the next session (one that begins at the top stays in), and a surah
     * the session stops inside runs on to its end when that is on the next page. Backward plans get the
     * mirror image at the session's first page.
     */
    val completeSurah: Boolean = false,
) {
    fun generate(sessions: Int? = null): List<IntRange> = PortionGenerator.generate(this, sessions)

    fun nextResume(last: IntRange): Int? = PortionGenerator.nextResume(this, last)

    /** Resume value that makes the plan start again exactly at [first], its first session. */
    fun resumeAt(first: IntRange): Int =
        if (!range.isBackward || unit.bySurah) first.first else first.last

    /** Pages still to cover. */
    fun totalPages(): Double = PortionGenerator.remaining(this).sumOf { Quran.weight(it) }

    /**
     * The same portion measured in [u]. Backward ayah sessions resume at the first unread ayah of a
     * surah while other backward sessions resume at the highest unread ayah, so the resume point is
     * moved to the matching edge of its surah.
     */
    fun withUnit(u: MemUnit): PortionSpec {
        if (u == unit) return this
        val r = resume
        val newResume = when {
            r == null || !range.isBackward || unit.bySurah == u.bySurah -> r
            u.bySurah -> Quran.surahRange(Quran.surahOf(r)).first
            else -> Quran.surahRange(Quran.surahOf(r)).last
        }
        return copy(unit = u, amount = u.defaultAmount, resume = newResume)
    }

    fun validate(): String? {
        range.validate()?.let { return it }
        if (amount <= 0.0) return "المقدار لازم يكون أكبر من صفر"
        if (unit == MemUnit.PAGE) {
            if (amount < 0.5 || amount > unit.maxAmount) return "عدد الأوجه من نصف وجه إلى ${unit.maxAmount}"
        } else if (amount.toInt() !in 1..unit.maxAmount) {
            return "المقدار من 1 إلى ${unit.maxAmount}"
        }
        val r = resume
        if (r != null && r !in range.bounds()) return "موضع البداية خارج المدى"
        return null
    }
}
