package com.sallo.hifz.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sallo.hifz.plan.GradeScale
import com.sallo.hifz.plan.Tracks

@Database(
    entities = [PlanEntity::class, SessionEntity::class, CalendarLinkEntity::class, CalendarEventEntity::class],
    version = 7,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun planDao(): PlanDao
    abstract fun calendarDao(): CalendarDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "hifz.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()

        /** v7: vacations, and the mistakes and alerts counted while reciting. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plans ADD COLUMN vacations TEXT NOT NULL DEFAULT ''")
                for (column in listOf("newMistakes", "newAlerts", "minorMistakes", "minorAlerts", "majorMistakes", "majorAlerts")) {
                    db.execSQL("ALTER TABLE sessions ADD COLUMN $column INTEGER")
                }
            }
        }

        /** v6: major review sessions that go on into the next round, and plans written to the phone's calendar. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plans ADD COLUMN majorWrap INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN majorLead TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `calendar_links` (`planId` INTEGER NOT NULL, `calendarId` INTEGER NOT NULL, " +
                        "`allDay` INTEGER NOT NULL, `minuteOfDay` INTEGER NOT NULL, `durationMinutes` INTEGER NOT NULL, " +
                        "`alertMinutes` INTEGER NOT NULL, PRIMARY KEY(`planId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `calendar_events` (`planId` INTEGER NOT NULL, `idx` INTEGER NOT NULL, " +
                        "`eventId` INTEGER NOT NULL, `hash` INTEGER NOT NULL, PRIMARY KEY(`planId`, `idx`))",
                )
            }
        }

        /** v5: each review on its own days, and the tracks each row has. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "separateSchedules INTEGER NOT NULL DEFAULT 0",
                    "minorScheduleMode TEXT NOT NULL DEFAULT 'WEEKDAYS'",
                    "minorWeekdaysMask INTEGER NOT NULL DEFAULT $ALL_DAYS_MASK",
                    "minorEvery INTEGER NOT NULL DEFAULT 1",
                    "majorScheduleMode TEXT NOT NULL DEFAULT 'WEEKDAYS'",
                    "majorWeekdaysMask INTEGER NOT NULL DEFAULT $ALL_DAYS_MASK",
                    "majorEvery INTEGER NOT NULL DEFAULT 1",
                ).forEach { db.execSQL("ALTER TABLE plans ADD COLUMN $it") }
                db.execSQL("ALTER TABLE sessions ADD COLUMN tracks INTEGER NOT NULL DEFAULT ${Tracks.ALL}")
            }
        }

        /** v4: grading systems. The four old grades (1 ممتاز … 4 يُعاد) become scores out of 100. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plans ADD COLUMN gradeScale TEXT NOT NULL DEFAULT 'WORDS'")
                for (column in listOf("newGrade", "minorGrade", "majorGrade", "prepGrade")) {
                    val cases = (1..4).joinToString(" ") { "WHEN $it THEN ${GradeScale.legacyScore(it)}" }
                    db.execSQL("UPDATE sessions SET $column = CASE $column $cases ELSE 0 END")
                }
            }
        }

        /** v3: completing surahs at session ends, and a major review that goes round what is memorized. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "newCompleteSurah INTEGER NOT NULL DEFAULT 0",
                    "majorCompleteSurah INTEGER NOT NULL DEFAULT 0",
                    "majorMemorized INTEGER NOT NULL DEFAULT 0",
                    "majorPrior INTEGER NOT NULL DEFAULT 1",
                ).forEach { db.execSQL("ALTER TABLE plans ADD COLUMN $it") }
            }
        }

        /** v2: selectable tracks, generic ranges and amounts, rounding, end date, generated major review. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val planColumns = listOf(
                    "includeNew INTEGER NOT NULL DEFAULT 1",
                    "includeMinor INTEGER NOT NULL DEFAULT 1",
                    "includeMajor INTEGER NOT NULL DEFAULT 1",
                    "newRangeType TEXT NOT NULL DEFAULT 'WHOLE'",
                    "newFrom INTEGER NOT NULL DEFAULT 1",
                    "newTo INTEGER NOT NULL DEFAULT 1",
                    "newFromAyah INTEGER NOT NULL DEFAULT 1",
                    "newToAyah INTEGER NOT NULL DEFAULT 1",
                    "newAmount REAL NOT NULL DEFAULT 10",
                    "newRounding TEXT NOT NULL DEFAULT 'NONE'",
                    "endDate INTEGER",
                    "majorAuto INTEGER NOT NULL DEFAULT 0",
                    "majorUnit TEXT NOT NULL DEFAULT 'PAGE'",
                    "majorAmount REAL NOT NULL DEFAULT 5",
                    "majorRangeType TEXT NOT NULL DEFAULT 'JUZ'",
                    "majorFrom INTEGER NOT NULL DEFAULT 30",
                    "majorTo INTEGER NOT NULL DEFAULT 30",
                    "majorFromAyah INTEGER NOT NULL DEFAULT 1",
                    "majorToAyah INTEGER NOT NULL DEFAULT 1",
                    "majorRounding TEXT NOT NULL DEFAULT 'NEAREST'",
                    "majorResume INTEGER",
                    "behind TEXT NOT NULL DEFAULT 'ASK'",
                )
                planColumns.forEach { db.execSQL("ALTER TABLE plans ADD COLUMN $it") }
                // v1 ranges were surah:ayah for ayah lessons and page numbers for page lessons.
                db.execSQL(
                    "UPDATE plans SET newRangeType = 'AYAHS', newFrom = fromSurah, newTo = toSurah, " +
                        "newFromAyah = fromAyah, newToAyah = toAyah, newAmount = amount WHERE unit = 'AYAH'",
                )
                db.execSQL(
                    "UPDATE plans SET newRangeType = 'PAGES', newFrom = fromPage, newTo = toPage, " +
                        "newAmount = amount / 2.0 WHERE unit = 'PAGE'",
                )
                // v1 resumed backward page plans differently; start such plans from their range again.
                db.execSQL("UPDATE plans SET resumeIndex = NULL WHERE unit = 'PAGE' AND fromPage > toPage")
                db.execSQL("ALTER TABLE sessions ADD COLUMN majorStart INTEGER")
                db.execSQL("ALTER TABLE sessions ADD COLUMN majorEnd INTEGER")
            }
        }
    }
}
