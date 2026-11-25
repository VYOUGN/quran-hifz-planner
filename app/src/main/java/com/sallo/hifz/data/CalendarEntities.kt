package com.sallo.hifz.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.sallo.hifz.calendar.CalendarStyle
import kotlinx.coroutines.flow.Flow

/**
 * A plan whose sessions are in a calendar on the phone («نقل إلى التقويم»), and how they look there.
 * Not tied to the plan by a foreign key: when the plan is deleted, its events still have to be taken
 * out of the calendar.
 */
@Entity(tableName = "calendar_links")
data class CalendarLinkEntity(
    @PrimaryKey val planId: Long,
    /** CalendarContract.Calendars._ID */
    val calendarId: Long,
    val allDay: Boolean,
    /** Start of a timed session, in minutes after midnight. */
    val minuteOfDay: Int,
    val durationMinutes: Int,
    /** Minutes before a timed session for the calendar's alert; -1 for none. */
    val alertMinutes: Int,
) {
    val style: CalendarStyle get() = CalendarStyle(allDay, minuteOfDay, durationMinutes, alertMinutes)

    companion object {
        fun of(planId: Long, calendarId: Long, style: CalendarStyle) =
            CalendarLinkEntity(planId, calendarId, style.allDay, style.minuteOfDay, style.durationMinutes, style.alertMinutes)
    }
}

/** The calendar event written for one row, and a hash of what it shows so unchanged rows are left alone. */
@Entity(tableName = "calendar_events", primaryKeys = ["planId", "idx"])
data class CalendarEventEntity(
    val planId: Long,
    val idx: Int,
    /** CalendarContract.Events._ID */
    val eventId: Long,
    val hash: Int,
)

@Dao
interface CalendarDao {
    @Query("SELECT * FROM calendar_links")
    suspend fun links(): List<CalendarLinkEntity>

    @Query("SELECT * FROM calendar_links WHERE planId = :planId")
    suspend fun link(planId: Long): CalendarLinkEntity?

    @Query("SELECT * FROM calendar_links WHERE planId = :planId")
    fun linkFlow(planId: Long): Flow<CalendarLinkEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLink(link: CalendarLinkEntity)

    @Query("DELETE FROM calendar_links WHERE planId = :planId")
    suspend fun deleteLink(planId: Long)

    @Query("SELECT * FROM calendar_events WHERE planId = :planId")
    suspend fun events(planId: Long): List<CalendarEventEntity>

    @Query("SELECT COUNT(*) FROM calendar_events WHERE planId = :planId")
    fun countFlow(planId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEvents(events: List<CalendarEventEntity>)

    @Query("DELETE FROM calendar_events WHERE planId = :planId AND idx IN (:idxs)")
    suspend fun deleteEvents(planId: Long, idxs: List<Int>)

    @Query("DELETE FROM calendar_events WHERE planId = :planId")
    suspend fun deleteAllEvents(planId: Long)
}
