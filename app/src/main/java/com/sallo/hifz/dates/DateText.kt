package com.sallo.hifz.dates

import java.time.DayOfWeek
import java.time.LocalDate

/** Arabic date text. Numbers stay in Western digits, like the paper table. */
object DateText {
    private const val RLM = "‏"

    /** Week order used in Saudi Arabia. */
    val WEEK_ORDER: List<DayOfWeek> = listOf(
        DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
    )

    private val WEEKDAY_NAMES = mapOf(
        DayOfWeek.SUNDAY to "الأحد",
        DayOfWeek.MONDAY to "الاثنين",
        DayOfWeek.TUESDAY to "الثلاثاء",
        DayOfWeek.WEDNESDAY to "الأربعاء",
        DayOfWeek.THURSDAY to "الخميس",
        DayOfWeek.FRIDAY to "الجمعة",
        DayOfWeek.SATURDAY to "السبت",
    )

    private val GREGORIAN_MONTHS = listOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر",
    )

    val HIJRI_MONTHS = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة",
    )

    fun weekday(day: DayOfWeek): String = WEEKDAY_NAMES.getValue(day)

    fun weekday(date: LocalDate): String = weekday(date.dayOfWeek)

    /**
     * Day and month joined by right-to-left marks, so the day sits on the right:
     * 1 November reads «11/1» on screen, exactly as written on the paper.
     */
    fun dayMonth(day: Int, month: Int): String = "$day$RLM/$RLM$month"

    fun short(date: LocalDate): String = dayMonth(date.dayOfMonth, date.monthValue)

    fun long(date: LocalDate): String =
        "${weekday(date)} ${date.dayOfMonth} ${GREGORIAN_MONTHS[date.monthValue - 1]} ${date.year}"

    /** «نوفمبر 2026». */
    fun monthYear(month: java.time.YearMonth): String = "${GREGORIAN_MONTHS[month.monthValue - 1]} ${month.year}"

    fun withWeekday(date: LocalDate): String = "${weekday(date)} ${short(date)}"

    /** «26 نوفمبر», with the year when it is not this year. */
    fun dayAndMonth(date: LocalDate): String {
        val text = "${date.dayOfMonth} ${GREGORIAN_MONTHS[date.monthValue - 1]}"
        return if (date.year == LocalDate.now().year) text else "$text ${date.year}"
    }
}

data class HijriDate(val year: Int, val month: Int, val day: Int) {
    val short: String get() = DateText.dayMonth(day, month)
    val long: String get() = "$day ${DateText.HIJRI_MONTHS[month - 1]} $year هـ"
}
