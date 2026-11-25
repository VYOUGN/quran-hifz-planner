package com.sallo.hifz.plan

import java.time.LocalDate

/** Bits of the tracks a row has on its day. */
object Tracks {
    const val NEW = 1
    const val MINOR = 2
    const val MAJOR = 4
    const val PREP = 8
    const val ALL = NEW or MINOR or MAJOR or PREP
}

/** One generated table row before it is stored. */
data class RowDraft(
    val date: LocalDate,
    /** New-memorization lesson, or null in a review-only plan or on a review-only day. */
    val lesson: IntRange?,
    val minor: List<IntRange>,
    /** Generated major review, or null when it is written by hand or nothing is memorized to review yet. */
    val major: IntRange?,
    /** Tracks that fall on this day ([Tracks] bits); every track unless the tracks have their own days. */
    val tracks: Int = Tracks.ALL,
    /**
     * «كمّل من الدورة الجاية»: the end of the round before, reviewed just before [major] (which starts the
     * next round). Empty unless the session was filled up from the next round.
     */
    val majorLead: List<IntRange> = emptyList(),
) {
    /** The generated major review in reading order. */
    val majorParts: List<IntRange> get() = majorLead + listOfNotNull(major)
}

/** A draft's major review from its parts (the last part is where the review stands). */
private fun RowDraft.withMajor(parts: List<IntRange>?): RowDraft =
    if (parts.isNullOrEmpty()) copy(major = null, majorLead = emptyList()) else copy(major = parts.last(), majorLead = parts.dropLast(1))

object PlanBuilder {

    fun build(settings: PlanSettings): List<RowDraft> = extend(emptyList(), null, settings)

    /**
     * Rows that come after kept rows (the kept rows are not returned).
     * [keptLessons]: lessons of the kept rows, so the minor review can still reach back into them.
     * [lastMajor]: the last generated major review among the kept rows, so its cycle continues.
     * [sessionDates]: dates for the first sessions of the driving track instead of the plan's days
     * (missed lessons caught up), the plan's days carrying on after the last of them.
     */
    fun extend(keptLessons: List<IntRange>, lastMajor: IntRange?, s: PlanSettings, sessionDates: List<LocalDate>? = null): List<RowDraft> {
        if (s.hasSeparateSchedules) return extendSeparately(keptLessons, lastMajor, s, sessionDates)
        val limit = s.sessionLimit()
        val lessons = if (s.includeNew) s.newPortion.generate(limit) else null
        val ranged = s.hasAutoMajor && !s.hasMemorizedMajor
        // With new memorization a major review over a range goes round it while the plan lasts; rounds
        // that run into each other are planned as they go.
        val wrapping = ranged && lessons != null && s.wrapsMajor
        val majors = if (ranged && !wrapping) s.majorPortion.generate(if (lessons == null) limit else null) else null
        val count = lessons?.size ?: majors?.size ?: 0
        if (count == 0) return emptyList()
        val dates = if (s.hasDates) datesFor(s, count, sessionDates) else List(count) { s.startDate }
        val offset = if (lessons != null && majors != null && lastMajor != null) {
            nextIndex(majors, lastMajor, s.majorPortion.range.isBackward)
        } else {
            0
        }
        val all = keptLessons + lessons.orEmpty()
        val minors = List(count) { j ->
            if (lessons != null && s.includeMinor) MinorReview.window(all, keptLessons.size + j, s.minorMode, s.minorCount) else emptyList()
        }
        val reviews: List<List<IntRange>> = when {
            // «من محفوظي»: rounds over what is memorized, planned as each one starts.
            s.hasMemorizedMajor -> MemorizedReview.plan(all, List(count) { keptLessons.size + it }, minors, s, lastMajor)
            wrapping -> ReviewStream.cycle(s.majorPortion, lastMajor, count)
            majors.isNullOrEmpty() -> List(count) { emptyList() }
            lessons == null -> List(count) { j -> listOf(majors[j]) }
            else -> List(count) { j -> listOf(majors[(offset + j) % majors.size]) }
        }
        return (0 until count).map { j ->
            RowDraft(date = dates[j], lesson = lessons?.get(j), minor = minors[j], major = null).withMajor(reviews[j])
        }
    }

    /**
     * «لكل مسار أيامه»: lessons on the plan's days, and each review on its own days up to the last
     * lesson; a day with several tracks is one row. A day's minor review is the lessons memorized
     * before that day, and the major review leaves those out.
     */
    private fun extendSeparately(keptLessons: List<IntRange>, lastMajor: IntRange?, s: PlanSettings, sessionDates: List<LocalDate>?): List<RowDraft> {
        val lessons = s.newPortion.generate(s.sessionLimit())
        if (lessons.isEmpty()) return emptyList()
        val lessonDates = datesFor(s, lessons.size, sessionDates)
        val end = lessonDates.last()
        val minorDays = if (s.hasMinor) s.trackCalendar(s.minorSchedule).datesBetween(s.startDate, end).toSet() else emptySet()
        val majorDays = if (s.includeMajor) s.trackCalendar(s.majorSchedule).datesBetween(s.startDate, end).toSet() else emptySet()
        val lessonOn = lessonDates.withIndex().associate { (i, d) -> d to i }
        val days = (lessonDates.toSet() + minorDays + majorDays).sorted()
        val all = keptLessons + lessons

        // Lessons memorized before each day: the kept ones and the new ones on earlier days.
        var earlier = 0
        val positions = days.map { day ->
            while (earlier < lessonDates.size && lessonDates[earlier] < day) earlier++
            keptLessons.size + earlier
        }
        val windows = positions.map { i -> if (s.includeMinor) MinorReview.window(all, i, s.minorMode, s.minorCount) else emptyList() }

        val majorIndexes = days.indices.filter { days[it] in majorDays }
        val majors: List<List<IntRange>> = when {
            !s.hasAutoMajor -> List(majorIndexes.size) { emptyList() }
            s.hasMemorizedMajor -> MemorizedReview.plan(all, majorIndexes.map { positions[it] }, majorIndexes.map { windows[it] }, s, lastMajor)
            s.wrapsMajor -> ReviewStream.cycle(s.majorPortion, lastMajor, majorIndexes.size)
            else -> {
                val cycle = s.majorPortion.generate()
                if (cycle.isEmpty()) {
                    List(majorIndexes.size) { emptyList() }
                } else {
                    val offset = lastMajor?.let { nextIndex(cycle, it, s.majorPortion.range.isBackward) } ?: 0
                    List(majorIndexes.size) { k -> listOf(cycle[(offset + k) % cycle.size]) }
                }
            }
        }
        val majorOn = majorIndexes.withIndex().associate { (k, i) -> i to majors[k] }

        return days.mapIndexed { i, day ->
            val lesson = lessonOn[day]?.let { lessons[it] }
            val minorDay = day in minorDays
            val majorDay = day in majorDays
            RowDraft(
                date = day,
                lesson = lesson,
                minor = if (minorDay) windows[i] else emptyList(),
                major = null,
                tracks = (if (lesson != null) Tracks.NEW or Tracks.PREP else 0) or
                    (if (minorDay) Tracks.MINOR else 0) or
                    (if (majorDay) Tracks.MAJOR else 0),
            ).withMajor(majorOn[i])
        }
    }

    /**
     * Settings that continue the plan after its kept rows, or null when the driving track has
     * nothing left. [lastLesson] / [lastMajor] are the last kept lesson and generated major review.
     */
    fun continuation(s: PlanSettings, lastLesson: IntRange?, lastMajor: IntRange?): PlanSettings? =
        if (s.includeNew) {
            if (lastLesson == null) s else s.newPortion.nextResume(lastLesson)?.let { s.copy(newPortion = s.newPortion.copy(resume = it)) }
        } else {
            if (lastMajor == null) s else s.majorPortion.nextResume(lastMajor)?.let { s.copy(majorPortion = s.majorPortion.copy(resume = it)) }
        }

    /** [count] session dates: [given] ones first (when there are any), then the plan's days after the last of them. */
    private fun datesFor(s: PlanSettings, count: Int, given: List<LocalDate>?): List<LocalDate> = when {
        given.isNullOrEmpty() -> s.calendar.dates(s.startDate, count)
        given.size >= count -> given.take(count)
        else -> given + s.calendar.datesFrom(s.calendar.next(given.last()), count - given.size)
    }

    /** Index of the first portion after [last] in reading order (0 when the cycle starts over). */
    private fun nextIndex(list: List<IntRange>, last: IntRange, backward: Boolean): Int {
        val i = if (backward) list.indexOfFirst { it.last < last.first } else list.indexOfFirst { it.first > last.last }
        return if (i < 0) 0 else i
    }
}
