package com.sallo.hifz.data

import android.content.Context
import com.sallo.hifz.dates.Hijri
import com.sallo.hifz.design.AppDesign
import com.sallo.hifz.design.AppFont
import com.sallo.hifz.design.HeroStyle
import com.sallo.hifz.design.WidgetLook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek

/** Light or dark look («المظهر»). */
enum class ThemeMode(val label: String) { SYSTEM("حسب الجوال"), LIGHT("فاتح"), DARK("داكن") }

/** Text size («حجم الخط»): a factor on the phone's own text size. */
enum class TextSize(val label: String, val scale: Float) {
    SMALL("صغير", 0.9f),
    NORMAL("عادي", 1f),
    LARGE("كبير", 1.15f),
    LARGER("أكبر", 1.3f),
}

data class Prefs(
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 17,
    val reminderMinute: Int = 0,
    /** A second reminder later in the day while today's session is still not done. */
    val followUpEnabled: Boolean = false,
    val followUpHour: Int = 21,
    val followUpMinute: Int = 0,
    val showHijri: Boolean = true,
    /** Days added to the Umm al-Qura date, to match where the month was sighted. */
    val hijriOffset: Int = 0,
    /** Table screen: timeline cards (true) or the paper-style grid (false). */
    val cardsView: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val textSize: TextSize = TextSize.NORMAL,
    /** Confetti and a word of encouragement when a session is done. */
    val celebrate: Boolean = true,
    /** A light vibration when an item is ticked. */
    val haptics: Boolean = true,
    /** «إذا تأخرت» for new plans: move missed lessons by themselves. */
    val autoCatchUp: Boolean = false,
    /** When the last backup was saved (epoch milliseconds), or 0. */
    val lastBackup: Long = 0,
    /** «التصميم»: colors of the whole app and the style of its hero card. */
    val design: AppDesign = AppDesign.EMERALD,
    /** «الخط» inside the app. */
    val font: AppFont = AppFont.SYSTEM,
    /** «هيئة الودجت», the same for every home-screen widget. */
    val widgetLook: WidgetLook = WidgetLook.APP,
    /** The name written on certificates («شهادة إتمام»). */
    val certificateName: String = "",
    /** «إخفاء الأرقام»: no percentages, counts or streaks on the home screen, the plan page and the widgets. */
    val hideNumbers: Boolean = false,
    /** «التقرير الأسبوعي»: a summary of the last seven days on [weeklyDay] at [weeklyHour]:[weeklyMinute]. */
    val weeklyReport: Boolean = false,
    val weeklyDay: DayOfWeek = DayOfWeek.FRIDAY,
    val weeklyHour: Int = 20,
    val weeklyMinute: Int = 0,
    /** «ألوانك»: the custom design's main color, accent and hero card. */
    val customPrimary: Int = 0xFF2F5D8A.toInt(),
    val customAccent: Int = 0xFFE8C15A.toInt(),
    val customHero: HeroStyle = HeroStyle.GRADIENT,
)

/** Whether the app shows its dark colors in the phone's current mode ([phoneDark]). */
fun ThemeMode.isDark(phoneDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> phoneDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** App settings in SharedPreferences, exposed as a StateFlow. */
class AppPrefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val values: StateFlow<Prefs> = state.asStateFlow()

    init {
        Hijri.offsetDays = state.value.hijriOffset
    }

    fun update(transform: (Prefs) -> Prefs) {
        val next = transform(state.value)
        sp.edit()
            .putBoolean(REMINDER, next.reminderEnabled)
            .putInt(HOUR, next.reminderHour)
            .putInt(MINUTE, next.reminderMinute)
            .putBoolean(FOLLOW_UP, next.followUpEnabled)
            .putInt(FOLLOW_UP_HOUR, next.followUpHour)
            .putInt(FOLLOW_UP_MINUTE, next.followUpMinute)
            .putBoolean(HIJRI, next.showHijri)
            .putInt(HIJRI_OFFSET, next.hijriOffset)
            .putBoolean(CARDS, next.cardsView)
            .putString(THEME, next.theme.name)
            .putString(TEXT_SIZE, next.textSize.name)
            .putBoolean(CELEBRATE, next.celebrate)
            .putBoolean(HAPTICS, next.haptics)
            .putBoolean(AUTO_CATCH_UP, next.autoCatchUp)
            .putLong(LAST_BACKUP, next.lastBackup)
            .putString(DESIGN, next.design.name)
            .putString(FONT, next.font.name)
            .putString(WIDGET_LOOK, next.widgetLook.name)
            .putString(CERTIFICATE_NAME, next.certificateName)
            .putBoolean(HIDE_NUMBERS, next.hideNumbers)
            .putBoolean(WEEKLY, next.weeklyReport)
            .putString(WEEKLY_DAY, next.weeklyDay.name)
            .putInt(WEEKLY_HOUR, next.weeklyHour)
            .putInt(WEEKLY_MINUTE, next.weeklyMinute)
            .putInt(CUSTOM_PRIMARY, next.customPrimary)
            .putInt(CUSTOM_ACCENT, next.customAccent)
            .putString(CUSTOM_HERO, next.customHero.name)
            .apply()
        // Before the new values go out, so every screen redrawn for them sees the new Hijri dates.
        Hijri.offsetDays = next.hijriOffset
        state.value = next
    }

    private fun read(): Prefs {
        val d = Prefs()
        return Prefs(
            reminderEnabled = sp.getBoolean(REMINDER, d.reminderEnabled),
            reminderHour = sp.getInt(HOUR, d.reminderHour),
            reminderMinute = sp.getInt(MINUTE, d.reminderMinute),
            followUpEnabled = sp.getBoolean(FOLLOW_UP, d.followUpEnabled),
            followUpHour = sp.getInt(FOLLOW_UP_HOUR, d.followUpHour),
            followUpMinute = sp.getInt(FOLLOW_UP_MINUTE, d.followUpMinute),
            showHijri = sp.getBoolean(HIJRI, d.showHijri),
            hijriOffset = sp.getInt(HIJRI_OFFSET, d.hijriOffset).coerceIn(-2, 2),
            cardsView = sp.getBoolean(CARDS, d.cardsView),
            theme = ThemeMode.entries.firstOrNull { it.name == sp.getString(THEME, null) } ?: d.theme,
            textSize = TextSize.entries.firstOrNull { it.name == sp.getString(TEXT_SIZE, null) } ?: d.textSize,
            celebrate = sp.getBoolean(CELEBRATE, d.celebrate),
            haptics = sp.getBoolean(HAPTICS, d.haptics),
            autoCatchUp = sp.getBoolean(AUTO_CATCH_UP, d.autoCatchUp),
            lastBackup = sp.getLong(LAST_BACKUP, d.lastBackup),
            design = AppDesign.entries.firstOrNull { it.name == sp.getString(DESIGN, null) } ?: d.design,
            font = AppFont.entries.firstOrNull { it.name == sp.getString(FONT, null) } ?: d.font,
            widgetLook = WidgetLook.entries.firstOrNull { it.name == sp.getString(WIDGET_LOOK, null) } ?: d.widgetLook,
            certificateName = sp.getString(CERTIFICATE_NAME, null) ?: d.certificateName,
            hideNumbers = sp.getBoolean(HIDE_NUMBERS, d.hideNumbers),
            weeklyReport = sp.getBoolean(WEEKLY, d.weeklyReport),
            weeklyDay = DayOfWeek.entries.firstOrNull { it.name == sp.getString(WEEKLY_DAY, null) } ?: d.weeklyDay,
            weeklyHour = sp.getInt(WEEKLY_HOUR, d.weeklyHour),
            weeklyMinute = sp.getInt(WEEKLY_MINUTE, d.weeklyMinute),
            customPrimary = sp.getInt(CUSTOM_PRIMARY, d.customPrimary),
            customAccent = sp.getInt(CUSTOM_ACCENT, d.customAccent),
            customHero = HeroStyle.entries.firstOrNull { it.name == sp.getString(CUSTOM_HERO, null) } ?: d.customHero,
        )
    }

    private companion object {
        const val REMINDER = "reminderEnabled"
        const val HOUR = "reminderHour"
        const val MINUTE = "reminderMinute"
        const val FOLLOW_UP = "followUpEnabled"
        const val FOLLOW_UP_HOUR = "followUpHour"
        const val FOLLOW_UP_MINUTE = "followUpMinute"
        const val HIJRI = "showHijri"
        const val HIJRI_OFFSET = "hijriOffset"
        const val CARDS = "cardsView"
        const val THEME = "theme"
        const val TEXT_SIZE = "textSize"
        const val CELEBRATE = "celebrate"
        const val HAPTICS = "haptics"
        const val AUTO_CATCH_UP = "autoCatchUp"
        const val LAST_BACKUP = "lastBackup"
        const val DESIGN = "design"
        const val FONT = "font"
        const val WIDGET_LOOK = "widgetLook"
        const val CERTIFICATE_NAME = "certificateName"
        const val HIDE_NUMBERS = "hideNumbers"
        const val WEEKLY = "weeklyReport"
        const val WEEKLY_DAY = "weeklyDay"
        const val WEEKLY_HOUR = "weeklyHour"
        const val WEEKLY_MINUTE = "weeklyMinute"
        const val CUSTOM_PRIMARY = "customPrimary"
        const val CUSTOM_ACCENT = "customAccent"
        const val CUSTOM_HERO = "customHero"
    }
}
