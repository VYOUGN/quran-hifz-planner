package com.sallo.hifz.calendar

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import android.util.Log
import androidx.core.content.ContextCompat
import com.sallo.hifz.data.AppDatabase
import com.sallo.hifz.data.CalendarEventEntity
import com.sallo.hifz.data.CalendarLinkEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId
import java.util.TimeZone

/** A calendar on the phone that sessions can be written to. */
data class DeviceCalendar(
    val id: Long,
    val name: String,
    val account: String,
    val color: Int,
    /** On this phone only, not synced to an account. */
    val local: Boolean,
    val primary: Boolean,
)

/**
 * «نقل إلى التقويم»: keeps an event in a calendar on the phone for every row of the plans linked to one.
 * After any change to the tables the events are brought up to date (only the rows whose text, date or
 * ticks changed), and a deleted plan's events are taken out.
 */
class CalendarSync(private val context: Context, db: AppDatabase) {
    private val dao = db.calendarDao()
    private val plans = db.planDao()
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (request in requests) {
                // Let a run of quick changes (ticking several items) settle into one update.
                delay(SETTLE_MS)
                try {
                    syncAll()
                } catch (e: Exception) {
                    Log.w(TAG, "calendar update failed", e)
                }
            }
        }
    }

    /** Brings the calendar up to date with the tables, a moment after the last change. */
    fun requestSync() {
        requests.trySend(Unit)
    }

    fun hasPermission(): Boolean = hasPermission(context)

    /** Calendars the sessions can be written to: primary ones first. */
    fun calendars(): List<DeviceCalendar> {
        if (!hasPermission()) return emptyList()
        val projection = arrayOf(
            Calendars._ID,
            Calendars.CALENDAR_DISPLAY_NAME,
            Calendars.ACCOUNT_NAME,
            Calendars.ACCOUNT_TYPE,
            Calendars.CALENDAR_COLOR,
            Calendars.IS_PRIMARY,
        )
        val out = ArrayList<DeviceCalendar>()
        context.contentResolver.query(
            Calendars.CONTENT_URI,
            projection,
            "${Calendars.CALENDAR_ACCESS_LEVEL} >= ${Calendars.CAL_ACCESS_CONTRIBUTOR}",
            null,
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                out += DeviceCalendar(
                    id = c.getLong(0),
                    name = c.getString(1).orEmpty(),
                    account = c.getString(2).orEmpty(),
                    color = c.getInt(4),
                    local = c.getString(3) == CalendarContract.ACCOUNT_TYPE_LOCAL,
                    primary = !c.isNull(5) && c.getInt(5) == 1,
                )
            }
        }
        return out.sortedWith(compareByDescending<DeviceCalendar> { it.primary && !it.local }.thenBy { it.account }.thenBy { it.name })
    }

    /** A calendar «جدول الحفظ» on this phone only (no account), made the first time it is asked for. */
    fun localCalendar(): Long? {
        if (!hasPermission()) return null
        calendars().firstOrNull { it.local && it.account == LOCAL_ACCOUNT }?.let { return it.id }
        val uri = Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT)
            .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, LOCAL_ACCOUNT)
            put(Calendars.CALENDAR_DISPLAY_NAME, LOCAL_ACCOUNT)
            put(Calendars.CALENDAR_COLOR, LOCAL_COLOR)
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, LOCAL_ACCOUNT)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
        }
        return context.contentResolver.insert(uri, values)?.let { ContentUris.parseId(it) }
    }

    /**
     * Writes the plan's sessions into [calendarId] looking like [style], or updates them there. Moving
     * to another calendar takes them out of the old one. Returns how many sessions are in the calendar.
     */
    suspend fun link(planId: Long, calendarId: Long, style: CalendarStyle): Int = unstoppable {
        val old = dao.link(planId)
        if (old != null && old.calendarId != calendarId) clear(planId)
        val link = CalendarLinkEntity.of(planId, calendarId, style)
        dao.saveLink(link)
        sync(link)
    }

    /** Takes the plan's sessions out of the calendar. */
    suspend fun unlink(planId: Long) = unstoppable {
        clear(planId)
        dao.deleteLink(planId)
    }

    /**
     * Runs [block] in the app's own scope, one at a time: leaving the screen that asked for it never
     * stops it half way, with events written that the app has not recorded yet.
     */
    private suspend fun <T> unstoppable(block: suspend () -> T): T = scope.async { mutex.withLock { block() } }.await()

    private suspend fun syncAll() = mutex.withLock { syncEverything() }

    /** Brings the calendar up to date now and waits for it (from a receiver that is about to finish). */
    suspend fun syncNow() {
        unstoppable { syncEverything() }
    }

    private suspend fun syncEverything() {
        if (!hasPermission()) return
        for (link in dao.links()) {
            try {
                sync(link)
            } catch (e: Exception) {
                Log.w(TAG, "calendar update failed for plan ${link.planId}", e)
            }
        }
    }

    /** Brings one plan's events up to date. Returns how many sessions are in the calendar. */
    private suspend fun sync(link: CalendarLinkEntity): Int {
        val planId = link.planId
        val plan = plans.plan(planId)
        if (plan == null) {
            // The plan was deleted: so are its events.
            clear(planId)
            dao.deleteLink(planId)
            return 0
        }
        if (!calendarExists(link.calendarId)) {
            // The calendar (or its account) is gone, and its events with it.
            dao.deleteAllEvents(planId)
            dao.deleteLink(planId)
            return 0
        }
        val drafts = CalendarEvents.drafts(plan, plans.sessions(planId), link.style, ZoneId.systemDefault())
        val stored = dao.events(planId).associateBy { it.idx }
        val alive = aliveEvents(link.calendarId)
        for (chunk in drafts.chunked(BATCH)) {
            val ops = ArrayList<ContentProviderOperation>()
            val inserted = ArrayList<Pair<Int, EventDraft>>()
            val changed = ArrayList<CalendarEventEntity>()
            for (d in chunk) {
                // An event deleted in the calendar app is written again.
                val known = stored[d.idx]?.takeIf { it.eventId in alive }
                when {
                    known == null -> {
                        inserted += ops.size to d
                        val values = values(d, planId).apply { put(Events.CALENDAR_ID, link.calendarId) }
                        ops += ContentProviderOperation.newInsert(Events.CONTENT_URI).withValues(values).build()
                        if (d.alertMinutes >= 0) {
                            ops += reminder(d.alertMinutes).withValueBackReference(Reminders.EVENT_ID, ops.size - 1).build()
                        }
                    }
                    known.hash != d.hash -> {
                        val id = known.eventId
                        ops += ContentProviderOperation.newUpdate(ContentUris.withAppendedId(Events.CONTENT_URI, id))
                            .withValues(values(d, planId)).build()
                        ops += ContentProviderOperation.newDelete(Reminders.CONTENT_URI)
                            .withSelection("${Reminders.EVENT_ID} = ?", arrayOf(id.toString())).build()
                        if (d.alertMinutes >= 0) ops += reminder(d.alertMinutes).withValue(Reminders.EVENT_ID, id).build()
                        changed += known.copy(hash = d.hash)
                    }
                }
            }
            if (ops.isEmpty()) continue
            val results = context.contentResolver.applyBatch(CalendarContract.AUTHORITY, ops)
            for ((i, d) in inserted) {
                val uri = results.getOrNull(i)?.uri ?: continue
                changed += CalendarEventEntity(planId, d.idx, ContentUris.parseId(uri), d.hash)
            }
            // Saved batch by batch, so a failure part way never leaves events the app does not know about.
            dao.saveEvents(changed)
        }
        // Rows that are no longer in the table.
        val wanted = drafts.mapTo(HashSet()) { it.idx }
        val gone = stored.values.filter { it.idx !in wanted }
        deleteEvents(gone.map { it.eventId }.filter { it in alive })
        gone.map { it.idx }.chunked(BATCH).forEach { dao.deleteEvents(planId, it) }
        return drafts.size
    }

    /** Takes all of the plan's events out of the calendar and forgets them. */
    private suspend fun clear(planId: Long) {
        val stored = dao.events(planId)
        if (stored.isNotEmpty() && hasPermission()) deleteEvents(stored.map { it.eventId })
        dao.deleteAllEvents(planId)
    }

    private fun deleteEvents(ids: List<Long>) {
        for (chunk in ids.chunked(BATCH)) {
            val ops = chunk.mapTo(ArrayList()) { ContentProviderOperation.newDelete(ContentUris.withAppendedId(Events.CONTENT_URI, it)).build() }
            context.contentResolver.applyBatch(CalendarContract.AUTHORITY, ops)
        }
    }

    private fun values(d: EventDraft, planId: Long) = ContentValues().apply {
        put(Events.TITLE, d.title)
        put(Events.DESCRIPTION, d.description)
        put(Events.DTSTART, d.start)
        put(Events.DTEND, d.end)
        put(Events.ALL_DAY, if (d.allDay) 1 else 0)
        put(Events.EVENT_TIMEZONE, d.timeZone)
        put(Events.HAS_ALARM, if (d.alertMinutes >= 0) 1 else 0)
        // A whole-day reminder should not show the day as busy.
        put(Events.AVAILABILITY, if (d.allDay) Events.AVAILABILITY_FREE else Events.AVAILABILITY_BUSY)
        // Lets calendar apps that support it open the plan from the event.
        put(Events.CUSTOM_APP_PACKAGE, context.packageName)
        put(Events.CUSTOM_APP_URI, "hifz://plan/$planId")
    }

    private fun reminder(minutes: Int): ContentProviderOperation.Builder =
        ContentProviderOperation.newInsert(Reminders.CONTENT_URI)
            .withValue(Reminders.MINUTES, minutes)
            .withValue(Reminders.METHOD, Reminders.METHOD_ALERT)

    private fun calendarExists(id: Long): Boolean =
        context.contentResolver.query(ContentUris.withAppendedId(Calendars.CONTENT_URI, id), arrayOf(Calendars._ID), null, null, null)
            ?.use { it.count > 0 } ?: false

    /** Events of the calendar that are still there (not deleted in the calendar app). */
    private fun aliveEvents(calendarId: Long): Set<Long> {
        val out = HashSet<Long>()
        context.contentResolver.query(
            Events.CONTENT_URI,
            arrayOf(Events._ID),
            "${Events.CALENDAR_ID} = ? AND ${Events.DELETED} = 0",
            arrayOf(calendarId.toString()),
            null,
        )?.use { c -> while (c.moveToNext()) out += c.getLong(0) }
        return out
    }

    companion object {
        private const val TAG = "CalendarSync"
        private const val SETTLE_MS = 600L
        private const val BATCH = 60

        /** Name of the calendar the app makes on the phone. */
        const val LOCAL_ACCOUNT = "جدول الحفظ"
        private const val LOCAL_COLOR = 0xFF1F7A5A.toInt()

        val PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

        fun hasPermission(context: Context): Boolean =
            PERMISSIONS.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }
}
