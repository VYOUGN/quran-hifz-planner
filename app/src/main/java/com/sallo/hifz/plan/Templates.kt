package com.sallo.hifz.plan

/** Ready-made settings for well-known ways of organizing memorization. */
enum class PlanTemplate(val label: String, val hint: String) {
    MY_TABLE(
        "جدولي",
        "مثل ورقتك: حفظ 10 آيات، ومراجعة صغرى لآخر 5 دروس، ومراجعة كبرى تكتبها بنفسك، أحد وثلاثاء وخميس",
    ),
    PASSAGES(
        "مقاطع التفسير",
        "حفظ مقطع لكل جلسة حسب موسوعة التفسير (الدرر السنية)، ومراجعة صغرى لآخر 5 دروس، ومراجعة كبرى من محفوظك",
    ),
    FIVE_FORTS(
        "الحصون الخمسة",
        "حفظ وجه، وتحضير الدرس الجاي، ومراجعة القريب آخر 20 درس، ومراجعة البعيد 5 أوجه تدور على كل محفوظك",
    ),
    CUMULATIVE("الربط التراكمي", "حفظ وجه، وتراجع كل اللي حفظته من بداية الجزء"),
    REVIEW("مراجعة فقط", "مراجعة كبرى 5 أوجه كل يومين، بدون حفظ جديد"),
    CUSTOM("مخصص", "تحدد كل شيء بنفسك");

    fun applyTo(s: PlanSettings): PlanSettings = when (this) {
        MY_TABLE -> s.copy(
            includeNew = true,
            newPortion = s.newPortion.copy(unit = MemUnit.AYAH, amount = 10.0),
            includeMinor = true,
            minorMode = MinorMode.LAST_N,
            minorCount = 5,
            includeMajor = true,
            majorMode = MajorMode.MANUAL,
            prepEnabled = false,
            scheduleMode = ScheduleMode.WEEKDAYS,
            weekdays = PlanSettings.DEFAULT_DAYS,
        )
        PASSAGES -> s.copy(
            includeNew = true,
            newPortion = s.newPortion.withUnit(MemUnit.PASSAGE).copy(amount = 1.0),
            includeMinor = true,
            minorMode = MinorMode.LAST_N,
            minorCount = 5,
            includeMajor = true,
            majorMode = MajorMode.MEMORIZED,
            majorPortion = s.majorPortion.copy(unit = MemUnit.PAGE, amount = 5.0, rounding = Rounding.NEAREST, completeSurah = true),
            prepEnabled = false,
        ).withSuggestedPrior()
        FIVE_FORTS -> s.copy(
            includeNew = true,
            newPortion = s.newPortion.copy(unit = MemUnit.PAGE, amount = 1.0, rounding = Rounding.NEAREST),
            includeMinor = true,
            minorMode = MinorMode.LAST_N,
            minorCount = 20,
            includeMajor = true,
            majorMode = MajorMode.MEMORIZED,
            majorPortion = s.majorPortion.copy(unit = MemUnit.PAGE, amount = 5.0, rounding = Rounding.NEAREST, completeSurah = true),
            prepEnabled = true,
        ).withSuggestedPrior()
        CUMULATIVE -> s.copy(
            includeNew = true,
            newPortion = s.newPortion.copy(unit = MemUnit.PAGE, amount = 1.0, rounding = Rounding.NEAREST),
            includeMinor = true,
            minorMode = MinorMode.CUMULATIVE_JUZ,
            prepEnabled = false,
        )
        REVIEW -> s.copy(
            includeNew = false,
            includeMinor = false,
            includeMajor = true,
            majorMode = MajorMode.RANGE,
            majorPortion = s.majorPortion.copy(unit = MemUnit.PAGE, amount = 5.0, rounding = Rounding.NEAREST),
            prepEnabled = false,
            scheduleMode = ScheduleMode.INTERVAL,
            every = 2,
        )
        CUSTOM -> s
    }

    companion object {
        fun fromName(name: String?): PlanTemplate = entries.firstOrNull { it.name == name } ?: CUSTOM
    }
}
