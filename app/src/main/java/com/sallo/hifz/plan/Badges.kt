package com.sallo.hifz.plan

import java.time.LocalDate

/** What the badges are measured by, across every plan. */
data class BadgeInput(
    val sessionsDone: Int,
    val bestStreak: Int,
    val juz: Int,
    val perfectWeeks: Int,
    val perfectMonths: Int,
)

/** «أوسمة الالتزام»: earned once and kept, even if a session is unticked later. */
enum class Badge(val title: String, val hint: String, val emoji: String, val target: Int) {
    FIRST("البداية", "أول جلسة تنجزها", "🌱", 1),
    STREAK_7("أسبوع متواصل", "7 جلسات متتالية", "🔥", 7),
    STREAK_30("شهر متواصل", "30 جلسة متتالية", "⚡", 30),
    STREAK_100("مئة متتالية", "100 جلسة متتالية", "💎", 100),
    SESSIONS_10("عشر جلسات", "10 جلسات منجزة", "📗", 10),
    SESSIONS_50("خمسون جلسة", "50 جلسة منجزة", "📘", 50),
    SESSIONS_100("مئة جلسة", "100 جلسة منجزة", "📙", 100),
    SESSIONS_500("خمسمئة جلسة", "500 جلسة منجزة", "📚", 500),
    JUZ_1("أول جزء", "تحفظ جزءاً كاملاً", "🌙", 1),
    JUZ_5("خمسة أجزاء", "تحفظ 5 أجزاء", "✨", 5),
    JUZ_10("عشرة أجزاء", "تحفظ 10 أجزاء", "🌟", 10),
    JUZ_15("نصف القرآن", "تحفظ 15 جزءاً", "🕌", 15),
    JUZ_30("الختمة", "تحفظ القرآن كاملاً", "👑", 30),
    PERFECT_WEEK("أسبوع كامل", "أسبوع بدون أي درس فائت", "⭐", 1),
    PERFECT_MONTH("شهر كامل", "شهر بدون أي درس فائت", "🏆", 1),
    FOUR_WEEKS("أربعة أسابيع كاملة", "4 أسابيع بدون أي درس فائت", "🏅", 4),
    ;

    /** How far [input] is towards the badge, capped at [target]. */
    fun progress(input: BadgeInput): Int = when (this) {
        FIRST, SESSIONS_10, SESSIONS_50, SESSIONS_100, SESSIONS_500 -> input.sessionsDone
        STREAK_7, STREAK_30, STREAK_100 -> input.bestStreak
        JUZ_1, JUZ_5, JUZ_10, JUZ_15, JUZ_30 -> input.juz
        PERFECT_WEEK, FOUR_WEEKS -> input.perfectWeeks
        PERFECT_MONTH -> input.perfectMonths
    }.coerceAtMost(target)

    fun earned(input: BadgeInput): Boolean = progress(input) >= target

    companion object {
        fun fromName(name: String): Badge? = entries.firstOrNull { it.name == name }
    }
}

object Badges {
    /** The badges' measures from every plan. */
    fun input(plans: List<PlanFacts>, today: LocalDate): BadgeInput {
        val sessions = plans.sumOf { p -> p.stats.count { it.done } }
        val best = plans.filter { it.calendar != null }.maxOfOrNull { p -> Stats.compute(p.stats, today, p.calendar).bestStreak } ?: 0
        val juz = Achievements.juzDone(plans.filter { it.memorizes }.flatMap { it.stats }).size
        val days = Consistency.combine(plans.filter { it.calendar != null }.map { Consistency.days(it.days, it.calendar, today) })
        return BadgeInput(
            sessionsDone = sessions,
            bestStreak = best,
            juz = juz,
            perfectWeeks = Consistency.perfectWeeks(days, today),
            perfectMonths = Consistency.perfectMonths(days, today),
        )
    }
}
