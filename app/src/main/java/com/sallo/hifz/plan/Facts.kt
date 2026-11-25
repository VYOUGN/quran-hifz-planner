package com.sallo.hifz.plan

import java.time.LocalDate

/** One day of a plan's table, for the calendar of days: everything done, something done. */
data class DayRow(val date: LocalDate, val done: Boolean, val partly: Boolean)

/** Mistakes and alerts recorded on a session («عدد الأخطاء»), over [items] recited items. */
data class MistakeRow(
    val planId: Long,
    val planName: String,
    val idx: Int,
    val date: LocalDate,
    val lesson: String,
    val mistakes: Int,
    val alerts: Int,
    val items: Int,
)

/** What the numbers across plans need from one plan, worked out once from its rows. */
class PlanFacts(
    val planId: Long,
    val name: String,
    /** The plan memorizes (its main track is new memorization). */
    val memorizes: Boolean,
    /** The main track's rows, for progress, streaks and juz. */
    val stats: List<StatRow>,
    /** Every row's day, for the calendar of days. */
    val days: List<DayRow>,
    /** The plan's study days with its vacations; null without dates. */
    val calendar: StudyCalendar?,
    val mistakes: List<MistakeRow>,
)
