package com.sallo.hifz.dates

import android.icu.util.Calendar
import android.icu.util.IslamicCalendar
import android.icu.util.TimeZone
import android.icu.util.ULocale
import java.time.LocalDate
import java.time.ZoneOffset

/** Umm al-Qura dates through Android's ICU calendar. */
object Hijri {
    private val cache = HashMap<LocalDate, HijriDate>()

    /** «تعديل التاريخ الهجري»: days added before converting, where the month was sighted a day apart. */
    @Volatile
    var offsetDays: Int = 0

    fun of(date: LocalDate): HijriDate = convert(date.plusDays(offsetDays.toLong()))

    @Synchronized
    private fun convert(date: LocalDate): HijriDate = cache.getOrPut(date) {
        val cal = IslamicCalendar(TimeZone.getTimeZone("UTC"), ULocale.ROOT)
        cal.calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        cal.timeInMillis = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        HijriDate(
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH) + 1,
            day = cal.get(Calendar.DAY_OF_MONTH),
        )
    }
}
