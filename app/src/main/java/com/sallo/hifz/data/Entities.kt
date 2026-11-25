package com.sallo.hifz.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sallo.hifz.quran.Ranges
import java.time.LocalDate

/**
 * A plan's settings. Columns marked "v1" are kept only so databases from version 1 still match;
 * the app reads the v2 columns.
 */
@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val template: String,
    val startDate: Long,
    val scheduleMode: String,
    val weekdaysMask: Int,
    /** N in «every N days / weeks / months». */
    val intervalDays: Int,
    /** Unit of the new-memorization amount. */
    val unit: String,
    /** v1: ayahs or half-pages per lesson. */
    val amount: Int,
    val fromSurah: Int,
    val fromAyah: Int,
    val toSurah: Int,
    val toAyah: Int,
    val fromPage: Int,
    val toPage: Int,
    /** Resume point of the new-memorization track. */
    val resumeIndex: Int?,
    val minorMode: String,
    val minorCount: Int,
    val prepEnabled: Boolean,
    // v2
    val includeNew: Boolean = true,
    val includeMinor: Boolean = true,
    val includeMajor: Boolean = true,
    val newRangeType: String = "WHOLE",
    val newFrom: Int = 1,
    val newTo: Int = 1,
    val newFromAyah: Int = 1,
    val newToAyah: Int = 1,
    val newAmount: Double = 10.0,
    val newRounding: String = "NEAREST",
    val endDate: Long? = null,
    val majorAuto: Boolean = false,
    val majorUnit: String = "PAGE",
    val majorAmount: Double = 5.0,
    val majorRangeType: String = "JUZ",
    val majorFrom: Int = 30,
    val majorTo: Int = 30,
    val majorFromAyah: Int = 1,
    val majorToAyah: Int = 1,
    val majorRounding: String = "NEAREST",
    val majorResume: Int? = null,
    val behind: String = "ASK",
    // v3
    val newCompleteSurah: Boolean = false,
    val majorCompleteSurah: Boolean = false,
    /** The generated major review goes round what is memorized («من محفوظي»), not a fixed range. */
    val majorMemorized: Boolean = false,
    /** «من محفوظي»: the major range is what was memorized before the plan; false when nothing was. */
    val majorPrior: Boolean = true,
    // v4
    /** How grades are shown («نظام التقييم»); the rows store scores out of 100. */
    val gradeScale: String = "WORDS",
    // v5
    /** The reviews follow their own days («لكل مسار أيامه»). */
    val separateSchedules: Boolean = false,
    val minorScheduleMode: String = "WEEKDAYS",
    val minorWeekdaysMask: Int = ALL_DAYS_MASK,
    val minorEvery: Int = 1,
    val majorScheduleMode: String = "WEEKDAYS",
    val majorWeekdaysMask: Int = ALL_DAYS_MASK,
    val majorEvery: Int = 1,
    // v6
    /** A round's last major review session is filled up from the next round («كمّل من الدورة الجاية»). */
    val majorWrap: Boolean = false,
    // v7
    /** Vacations («إجازة»), "startEpochDay-endEpochDay;…": no session falls on them. */
    val vacations: String = "",
)

/** Every day of the week, as stored in the weekday masks (bit = DayOfWeek.value). */
const val ALL_DAYS_MASK = 0b11111110

/** One row of the table: a date, its lesson and reviews, and what was done. */
@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["planId", "idx"], unique = true)],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val idx: Int,
    /** Epoch day. */
    val date: Long,
    /** New-memorization lesson; -1 when the plan has no new memorization. */
    val lessonStart: Int,
    val lessonEnd: Int,
    /** Minor review ranges, "a-b;c-d". */
    val minorRanges: String,
    val newText: String? = null,
    val minorText: String? = null,
    /** Major review written by hand, or a correction of the generated one. */
    val majorText: String? = null,
    val prepText: String? = null,
    val newDone: Boolean = false,
    val minorDone: Boolean = false,
    val majorDone: Boolean = false,
    val prepDone: Boolean = false,
    /** Grades as scores out of 100 (0 = not graded); shown in the plan's [PlanEntity.gradeScale]. */
    val newGrade: Int = 0,
    val minorGrade: Int = 0,
    val majorGrade: Int = 0,
    val prepGrade: Int = 0,
    val note: String? = null,
    /** Generated major review (v2). */
    val majorStart: Int? = null,
    val majorEnd: Int? = null,
    /** Tracks that fall on this row's day (v5, [com.sallo.hifz.plan.Tracks] bits); all of them unless the reviews have their own days. */
    val tracks: Int = com.sallo.hifz.plan.Tracks.ALL,
    /**
     * v6: the end of the round before, reviewed just before [majorRange] ("a-b;c-d"), when the session
     * was filled up from the next round. [majorRange] stays where the review now stands.
     */
    val majorLead: String = "",
    // v7: mistakes and alerts counted while reciting («عدد الأخطاء»); null when not recorded.
    val newMistakes: Int? = null,
    val newAlerts: Int? = null,
    val minorMistakes: Int? = null,
    val minorAlerts: Int? = null,
    val majorMistakes: Int? = null,
    val majorAlerts: Int? = null,
) {
    /** The generated major review in reading order. */
    val majorParts: List<IntRange> get() = Ranges.decode(majorLead) + listOfNotNull(majorRange)

    /** Whether [item] falls on this row's day. */
    fun has(item: Item): Boolean = tracks and item.bit != 0

    val hasLesson: Boolean get() = lessonStart >= 0
    val lesson: IntRange get() = lessonStart..lessonEnd
    val lessonOrNull: IntRange? get() = if (hasLesson) lesson else null
    val majorRange: IntRange? get() = if (majorStart != null && majorEnd != null) majorStart..majorEnd else null
    val localDate: LocalDate get() = LocalDate.ofEpochDay(date)

    /** Anything the user did or wrote on this row, other than the major-review text. */
    fun hasProgress(): Boolean =
        newDone || minorDone || majorDone || prepDone ||
            newGrade != 0 || minorGrade != 0 || majorGrade != 0 || prepGrade != 0 ||
            !note.isNullOrBlank() || newText != null || minorText != null || prepText != null ||
            Item.entries.any { mistakes(it) != null || alerts(it) != null }

    /** Mistakes counted on [item] while reciting, or null when not recorded (preparation has none). */
    fun mistakes(item: Item): Int? = when (item) {
        Item.NEW -> newMistakes
        Item.MINOR -> minorMistakes
        Item.MAJOR -> majorMistakes
        Item.PREP -> null
    }

    /** Alerts («تنبيهات») counted on [item], or null when not recorded. */
    fun alerts(item: Item): Int? = when (item) {
        Item.NEW -> newAlerts
        Item.MINOR -> minorAlerts
        Item.MAJOR -> majorAlerts
        Item.PREP -> null
    }

    fun withCounts(item: Item, mistakes: Int?, alerts: Int?): SessionEntity = when (item) {
        Item.NEW -> copy(newMistakes = mistakes, newAlerts = alerts)
        Item.MINOR -> copy(minorMistakes = mistakes, minorAlerts = alerts)
        Item.MAJOR -> copy(majorMistakes = mistakes, majorAlerts = alerts)
        Item.PREP -> this
    }

    /** Mistakes recorded on any item, or null when none was recorded. */
    val totalMistakes: Int? get() = Item.entries.mapNotNull { mistakes(it) }.takeIf { it.isNotEmpty() }?.sum()

    fun done(item: Item): Boolean = when (item) {
        Item.NEW -> newDone
        Item.MINOR -> minorDone
        Item.MAJOR -> majorDone
        Item.PREP -> prepDone
    }

    /** The item's grade as a score out of 100, or 0 when it is not graded. */
    fun score(item: Item): Int = when (item) {
        Item.NEW -> newGrade
        Item.MINOR -> minorGrade
        Item.MAJOR -> majorGrade
        Item.PREP -> prepGrade
    }

    fun withDone(item: Item, value: Boolean): SessionEntity = when (item) {
        Item.NEW -> copy(newDone = value)
        Item.MINOR -> copy(minorDone = value)
        Item.MAJOR -> copy(majorDone = value)
        Item.PREP -> copy(prepDone = value)
    }
}

/** The four kinds of cell in a row, with their [com.sallo.hifz.plan.Tracks] bit. */
enum class Item(val label: String, val bit: Int) {
    NEW("الحفظ", com.sallo.hifz.plan.Tracks.NEW),
    MINOR("المراجعة الصغرى", com.sallo.hifz.plan.Tracks.MINOR),
    MAJOR("المراجعة الكبرى", com.sallo.hifz.plan.Tracks.MAJOR),
    PREP("التحضير", com.sallo.hifz.plan.Tracks.PREP),
}
