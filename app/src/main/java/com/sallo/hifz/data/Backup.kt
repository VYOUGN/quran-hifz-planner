package com.sallo.hifz.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A plan and its rows, as in a backup file. */
class PlanBackup(val plan: PlanEntity, val sessions: List<SessionEntity>)

/**
 * «النسخ الاحتياطي»: every plan with its rows as JSON. Fields are written by name, so a backup still
 * reads after an update adds columns (missing ones take their defaults). Links to the phone's calendar
 * are not kept: they belong to the phone the backup was made on.
 */
object Backup {
    private const val APP = "com.sallo.hifz"
    const val FORMAT = 1

    sealed interface Result {
        class Plans(val plans: List<PlanBackup>) : Result
        /** Made by a newer version of the app. */
        data object TooNew : Result
        /** Not a backup of this app. */
        data object NotBackup : Result
    }

    fun write(plans: List<PlanBackup>, savedAt: Long): String {
        val list = JSONArray()
        for (b in plans) {
            val o = plan(b.plan)
            o.put("sessions", JSONArray().apply { b.sessions.forEach { put(session(it)) } })
            list.put(o)
        }
        return JSONObject()
            .put("app", APP)
            .put("format", FORMAT)
            .put("savedAt", savedAt)
            .put("plans", list)
            .toString()
    }

    fun read(text: String): Result = try {
        val root = JSONObject(text)
        when {
            root.optString("app") != APP -> Result.NotBackup
            root.optInt("format", 0) > FORMAT -> Result.TooNew
            else -> {
                val list = root.getJSONArray("plans")
                Result.Plans(
                    (0 until list.length()).map { i ->
                        val o = list.getJSONObject(i)
                        val rows = o.optJSONArray("sessions") ?: JSONArray()
                        PlanBackup(plan(o), (0 until rows.length()).map { session(rows.getJSONObject(it)) })
                    },
                )
            }
        }
    } catch (e: JSONException) {
        Result.NotBackup
    }

    private fun JSONObject.intOrNull(name: String): Int? = if (has(name) && !isNull(name)) getInt(name) else null
    private fun JSONObject.longOrNull(name: String): Long? = if (has(name) && !isNull(name)) getLong(name) else null
    private fun JSONObject.stringOrNull(name: String): String? = if (has(name) && !isNull(name)) getString(name) else null
    private fun JSONObject.putOrNull(name: String, value: Any?): JSONObject = put(name, value ?: JSONObject.NULL)

    private fun plan(p: PlanEntity): JSONObject = JSONObject()
        .put("name", p.name)
        .put("createdAt", p.createdAt)
        .put("template", p.template)
        .put("startDate", p.startDate)
        .put("scheduleMode", p.scheduleMode)
        .put("weekdaysMask", p.weekdaysMask)
        .put("intervalDays", p.intervalDays)
        .put("unit", p.unit)
        .put("amount", p.amount)
        .put("fromSurah", p.fromSurah)
        .put("fromAyah", p.fromAyah)
        .put("toSurah", p.toSurah)
        .put("toAyah", p.toAyah)
        .put("fromPage", p.fromPage)
        .put("toPage", p.toPage)
        .putOrNull("resumeIndex", p.resumeIndex)
        .put("minorMode", p.minorMode)
        .put("minorCount", p.minorCount)
        .put("prepEnabled", p.prepEnabled)
        .put("includeNew", p.includeNew)
        .put("includeMinor", p.includeMinor)
        .put("includeMajor", p.includeMajor)
        .put("newRangeType", p.newRangeType)
        .put("newFrom", p.newFrom)
        .put("newTo", p.newTo)
        .put("newFromAyah", p.newFromAyah)
        .put("newToAyah", p.newToAyah)
        .put("newAmount", p.newAmount)
        .put("newRounding", p.newRounding)
        .putOrNull("endDate", p.endDate)
        .put("majorAuto", p.majorAuto)
        .put("majorUnit", p.majorUnit)
        .put("majorAmount", p.majorAmount)
        .put("majorRangeType", p.majorRangeType)
        .put("majorFrom", p.majorFrom)
        .put("majorTo", p.majorTo)
        .put("majorFromAyah", p.majorFromAyah)
        .put("majorToAyah", p.majorToAyah)
        .put("majorRounding", p.majorRounding)
        .putOrNull("majorResume", p.majorResume)
        .put("behind", p.behind)
        .put("newCompleteSurah", p.newCompleteSurah)
        .put("majorCompleteSurah", p.majorCompleteSurah)
        .put("majorMemorized", p.majorMemorized)
        .put("majorPrior", p.majorPrior)
        .put("gradeScale", p.gradeScale)
        .put("separateSchedules", p.separateSchedules)
        .put("minorScheduleMode", p.minorScheduleMode)
        .put("minorWeekdaysMask", p.minorWeekdaysMask)
        .put("minorEvery", p.minorEvery)
        .put("majorScheduleMode", p.majorScheduleMode)
        .put("majorWeekdaysMask", p.majorWeekdaysMask)
        .put("majorEvery", p.majorEvery)
        .put("majorWrap", p.majorWrap)
        .put("vacations", p.vacations)

    private fun plan(o: JSONObject): PlanEntity {
        val d = PlanEntity(
            name = "", createdAt = 0, template = "", startDate = 0, scheduleMode = "", weekdaysMask = 0, intervalDays = 1,
            unit = "", amount = 0, fromSurah = 1, fromAyah = 1, toSurah = 1, toAyah = 1, fromPage = 1, toPage = 1,
            resumeIndex = null, minorMode = "", minorCount = 0, prepEnabled = false,
        )
        return PlanEntity(
            name = o.getString("name"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            template = o.optString("template", "CUSTOM"),
            startDate = o.getLong("startDate"),
            scheduleMode = o.optString("scheduleMode", "WEEKDAYS"),
            weekdaysMask = o.optInt("weekdaysMask", 0),
            intervalDays = o.optInt("intervalDays", 1),
            unit = o.optString("unit", "AYAH"),
            amount = o.optInt("amount", 10),
            fromSurah = o.optInt("fromSurah", 1),
            fromAyah = o.optInt("fromAyah", 1),
            toSurah = o.optInt("toSurah", 1),
            toAyah = o.optInt("toAyah", 1),
            fromPage = o.optInt("fromPage", 1),
            toPage = o.optInt("toPage", 1),
            resumeIndex = o.intOrNull("resumeIndex"),
            minorMode = o.optString("minorMode", "LAST_N"),
            minorCount = o.optInt("minorCount", 5),
            prepEnabled = o.optBoolean("prepEnabled", false),
            includeNew = o.optBoolean("includeNew", d.includeNew),
            includeMinor = o.optBoolean("includeMinor", d.includeMinor),
            includeMajor = o.optBoolean("includeMajor", d.includeMajor),
            newRangeType = o.optString("newRangeType", d.newRangeType),
            newFrom = o.optInt("newFrom", d.newFrom),
            newTo = o.optInt("newTo", d.newTo),
            newFromAyah = o.optInt("newFromAyah", d.newFromAyah),
            newToAyah = o.optInt("newToAyah", d.newToAyah),
            newAmount = o.optDouble("newAmount", d.newAmount),
            newRounding = o.optString("newRounding", d.newRounding),
            endDate = o.longOrNull("endDate"),
            majorAuto = o.optBoolean("majorAuto", d.majorAuto),
            majorUnit = o.optString("majorUnit", d.majorUnit),
            majorAmount = o.optDouble("majorAmount", d.majorAmount),
            majorRangeType = o.optString("majorRangeType", d.majorRangeType),
            majorFrom = o.optInt("majorFrom", d.majorFrom),
            majorTo = o.optInt("majorTo", d.majorTo),
            majorFromAyah = o.optInt("majorFromAyah", d.majorFromAyah),
            majorToAyah = o.optInt("majorToAyah", d.majorToAyah),
            majorRounding = o.optString("majorRounding", d.majorRounding),
            majorResume = o.intOrNull("majorResume"),
            behind = o.optString("behind", d.behind),
            newCompleteSurah = o.optBoolean("newCompleteSurah", d.newCompleteSurah),
            majorCompleteSurah = o.optBoolean("majorCompleteSurah", d.majorCompleteSurah),
            majorMemorized = o.optBoolean("majorMemorized", d.majorMemorized),
            majorPrior = o.optBoolean("majorPrior", d.majorPrior),
            gradeScale = o.optString("gradeScale", d.gradeScale),
            separateSchedules = o.optBoolean("separateSchedules", d.separateSchedules),
            minorScheduleMode = o.optString("minorScheduleMode", d.minorScheduleMode),
            minorWeekdaysMask = o.optInt("minorWeekdaysMask", d.minorWeekdaysMask),
            minorEvery = o.optInt("minorEvery", d.minorEvery),
            majorScheduleMode = o.optString("majorScheduleMode", d.majorScheduleMode),
            majorWeekdaysMask = o.optInt("majorWeekdaysMask", d.majorWeekdaysMask),
            majorEvery = o.optInt("majorEvery", d.majorEvery),
            majorWrap = o.optBoolean("majorWrap", d.majorWrap),
            vacations = o.optString("vacations", d.vacations),
        )
    }

    private fun session(s: SessionEntity): JSONObject = JSONObject()
        .put("idx", s.idx)
        .put("date", s.date)
        .put("lessonStart", s.lessonStart)
        .put("lessonEnd", s.lessonEnd)
        .put("minorRanges", s.minorRanges)
        .putOrNull("newText", s.newText)
        .putOrNull("minorText", s.minorText)
        .putOrNull("majorText", s.majorText)
        .putOrNull("prepText", s.prepText)
        .put("newDone", s.newDone)
        .put("minorDone", s.minorDone)
        .put("majorDone", s.majorDone)
        .put("prepDone", s.prepDone)
        .put("newGrade", s.newGrade)
        .put("minorGrade", s.minorGrade)
        .put("majorGrade", s.majorGrade)
        .put("prepGrade", s.prepGrade)
        .putOrNull("note", s.note)
        .putOrNull("majorStart", s.majorStart)
        .putOrNull("majorEnd", s.majorEnd)
        .put("tracks", s.tracks)
        .put("majorLead", s.majorLead)
        .putOrNull("newMistakes", s.newMistakes)
        .putOrNull("newAlerts", s.newAlerts)
        .putOrNull("minorMistakes", s.minorMistakes)
        .putOrNull("minorAlerts", s.minorAlerts)
        .putOrNull("majorMistakes", s.majorMistakes)
        .putOrNull("majorAlerts", s.majorAlerts)

    private fun session(o: JSONObject): SessionEntity = SessionEntity(
        planId = 0,
        idx = o.getInt("idx"),
        date = o.getLong("date"),
        lessonStart = o.optInt("lessonStart", -1),
        lessonEnd = o.optInt("lessonEnd", -1),
        minorRanges = o.optString("minorRanges", ""),
        newText = o.stringOrNull("newText"),
        minorText = o.stringOrNull("minorText"),
        majorText = o.stringOrNull("majorText"),
        prepText = o.stringOrNull("prepText"),
        newDone = o.optBoolean("newDone", false),
        minorDone = o.optBoolean("minorDone", false),
        majorDone = o.optBoolean("majorDone", false),
        prepDone = o.optBoolean("prepDone", false),
        newGrade = o.optInt("newGrade", 0),
        minorGrade = o.optInt("minorGrade", 0),
        majorGrade = o.optInt("majorGrade", 0),
        prepGrade = o.optInt("prepGrade", 0),
        note = o.stringOrNull("note"),
        majorStart = o.intOrNull("majorStart"),
        majorEnd = o.intOrNull("majorEnd"),
        tracks = o.optInt("tracks", com.sallo.hifz.plan.Tracks.ALL),
        majorLead = o.optString("majorLead", ""),
        newMistakes = o.intOrNull("newMistakes"),
        newAlerts = o.intOrNull("newAlerts"),
        minorMistakes = o.intOrNull("minorMistakes"),
        minorAlerts = o.intOrNull("minorAlerts"),
        majorMistakes = o.intOrNull("majorMistakes"),
        majorAlerts = o.intOrNull("majorAlerts"),
    )
}
