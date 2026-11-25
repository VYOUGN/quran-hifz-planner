package com.sallo.hifz.plan

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One mark of a grading system. Grades are stored as [score] (out of 100, 0 = no grade), so a plan
 * can switch systems and its grades are shown as the nearest mark of the new one.
 */
data class GradeLevel(val label: String, val short: String, val score: Int) {
    /** Below this the item is to be repeated, so grading it does not tick it. */
    val passed: Boolean get() = score >= PASS

    companion object {
        const val PASS = 50
    }
}

/** Ways to grade a memorization or a review («نظام التقييم»). */
enum class GradeScale(val label: String, val levels: List<GradeLevel>) {
    WORDS(
        "التقديرات",
        listOf(
            GradeLevel("ممتاز", "م", 95),
            GradeLevel("جيد جداً", "ج ج", 85),
            GradeLevel("جيد", "ج", 75),
            GradeLevel("مقبول", "مق", 60),
            GradeLevel("يُعاد", "يُعاد", 40),
        ),
    ),
    LETTERS(
        "الحروف",
        listOf(
            GradeLevel("أ+", "أ+", 97),
            GradeLevel("أ", "أ", 92),
            GradeLevel("ب", "ب", 82),
            GradeLevel("ج", "ج", 72),
            GradeLevel("د", "د", 62),
            GradeLevel("هـ (يُعاد)", "هـ", 40),
        ),
    ),
    MISTAKES(
        "عدد الأخطاء",
        listOf(
            GradeLevel("بلا أخطاء", "0 خطأ", 100),
            GradeLevel("خطأ واحد", "خطأ", 90),
            GradeLevel("خطآن", "خطآن", 80),
            GradeLevel("3 أخطاء", "3 أخطاء", 70),
            GradeLevel("4 فأكثر (يُعاد)", "+4", 45),
        ),
    ),
    STARS(
        "النجوم",
        listOf(
            GradeLevel("★★★★★", "5★", 100),
            GradeLevel("★★★★", "4★", 85),
            GradeLevel("★★★", "3★", 70),
            GradeLevel("★★", "2★", 55),
            GradeLevel("★ (يُعاد)", "1★", 40),
        ),
    ),
    TEN("الدرجة من 10", (10 downTo 1).map { GradeLevel("$it", "$it/10", it * 10) }),
    LIGHTS(
        "الإشارات",
        listOf(
            GradeLevel("🟢 متقن", "متقن", 92),
            GradeLevel("🟡 يحتاج تثبيت", "تثبيت", 70),
            GradeLevel("🔴 يُعاد", "يُعاد", 40),
        ),
    );

    /** The mark shown for a stored [score]: the nearest level, or null when there is no grade. */
    fun levelOf(score: Int): GradeLevel? = if (score <= 0) null else levels.minBy { abs(it.score - score) }

    /** «ممتاز، جيد جداً، جيد…» */
    val sample: String get() = levels.joinToString("، ") { it.label }

    companion object {
        fun fromName(name: String?): GradeScale = entries.firstOrNull { it.name == name } ?: WORDS

        /** Average of the given scores, ignoring ungraded ones, or null. */
        fun average(scores: List<Int>): Int? = scores.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()

        /** Scores of the four grades of version 3 and before (ممتاز، جيد جداً، جيد، يُعاد). */
        fun legacyScore(code: Int): Int = when (code) {
            1 -> 95
            2 -> 85
            3 -> 75
            4 -> 40
            else -> 0
        }
    }
}
