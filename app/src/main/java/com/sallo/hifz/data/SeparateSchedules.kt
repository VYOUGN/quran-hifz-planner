package com.sallo.hifz.data

import com.sallo.hifz.plan.PlanBuilder
import com.sallo.hifz.plan.PlanSettings
import com.sallo.hifz.plan.RowDraft
import com.sallo.hifz.plan.Tracks
import java.time.LocalDate

/** The table after «ترحيل»: rows kept from [from] on (re-numbered from there), then new rows. */
data class Rescheduled(val from: Int, val kept: List<SessionEntity>, val added: List<RowDraft>, val missed: Int)

/**
 * «ترحيل» when the reviews have their own days. The lessons from the first missed one (after the last
 * memorized lesson) start again today or on the next lesson day, or on [lessonDates] when given, and
 * every day from today is planned again. Days before today keep their reviews and what was done on
 * them, without the lessons they missed. Null when no lesson was missed.
 */
fun rescheduleSeparately(
    settings: PlanSettings,
    rows: List<SessionEntity>,
    today: LocalDate,
    lessonDates: List<LocalDate>? = null,
): Rescheduled? {
    val lessonRows = rows.withIndex().filter { it.value.hasLesson }
    val firstMissed = lessonRows.getOrNull(lessonRows.indexOfLast { it.value.newDone } + 1) ?: return null
    if (firstMissed.value.localDate >= today) return null
    val from = firstMissed.index
    val missed = lessonRows.count { it.index >= from && it.value.localDate < today }

    val past = rows.filter { it.localDate < today }
    val keptLessons = rows.take(from).mapNotNull { it.lessonOrNull }
    val lastMajor = past.lastOrNull { it.majorRange != null }?.majorRange
    val continued = settings.copy(
        startDate = today,
        newPortion = settings.newPortion.copy(resume = settings.newPortion.resumeAt(firstMissed.value.lesson)),
    )
    val added = PlanBuilder.extend(keptLessons, lastMajor, continued, lessonDates)

    val lessonBits = Tracks.NEW or Tracks.PREP
    val kept = past.drop(from).mapNotNull { row ->
        if (!row.hasLesson) return@mapNotNull row
        val without = row.copy(
            lessonStart = -1,
            lessonEnd = -1,
            newText = null,
            newGrade = 0,
            prepText = null,
            prepDone = false,
            prepGrade = 0,
            tracks = row.tracks and lessonBits.inv(),
        )
        // A day that only had the missed lesson goes away.
        without.takeIf { it.tracks and (Tracks.MINOR or Tracks.MAJOR) != 0 || it.hasProgress() }
    }
    return Rescheduled(from, kept, added, missed)
}
