package com.sallo.hifz.plan

import com.sallo.hifz.quran.Quran

/** Surah-based ayah lessons; kept as a small entry point for the paper-style plans. */
object LessonGenerator {
    /**
     * Lessons of about [perLesson] ayahs that never cross a surah. Backward (fromSurah > toSurah)
     * walks surahs downwards, each surah from its start, and includes all of [toSurah].
     */
    fun byAyahs(fromSurah: Int, fromAyah: Int, toSurah: Int, toAyah: Int, perLesson: Int): List<IntRange> =
        PortionSpec(
            unit = MemUnit.AYAH,
            amount = perLesson.toDouble(),
            range = RangeSpec(RangeType.AYAHS, fromSurah, toSurah, fromAyah, if (fromSurah > toSurah) 1 else toAyah),
        ).generate()

    /** Whole-page lessons between two page numbers (either direction). */
    fun byPages(fromPage: Int, toPage: Int, pagesPerLesson: Int): List<IntRange> =
        PortionSpec(
            unit = MemUnit.PAGE,
            amount = pagesPerLesson.toDouble(),
            range = RangeSpec(RangeType.PAGES, fromPage, toPage),
        ).generate()

    /** Size of a range in surahs, for display. */
    fun surahCount(range: IntRange): Int = Quran.surahOf(range.last) - Quran.surahOf(range.first) + 1
}
