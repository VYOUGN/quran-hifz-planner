package com.sallo.hifz.widget

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.View
import android.widget.RemoteViews
import com.sallo.hifz.HifzApp
import com.sallo.hifz.MainActivity
import com.sallo.hifz.R
import com.sallo.hifz.common.PlanText
import com.sallo.hifz.common.Plurals
import com.sallo.hifz.data.Item
import com.sallo.hifz.data.PlanEntity
import com.sallo.hifz.data.Prefs
import com.sallo.hifz.data.SessionEntity
import com.sallo.hifz.data.ThemeMode
import com.sallo.hifz.data.isDark
import com.sallo.hifz.data.mainDone
import com.sallo.hifz.data.mainRange
import com.sallo.hifz.data.missedCount
import com.sallo.hifz.data.pending
import com.sallo.hifz.data.toSettings
import com.sallo.hifz.dates.DateText
import com.sallo.hifz.design.AppDesign
import com.sallo.hifz.design.PhoneDesigns
import com.sallo.hifz.design.WidgetColors
import com.sallo.hifz.design.WidgetLook
import com.sallo.hifz.data.planStats
import com.sallo.hifz.plan.PlanStats
import com.sallo.hifz.plan.StatRow
import com.sallo.hifz.plan.Stats
import com.sallo.hifz.quran.RangeFormatter
import com.sallo.hifz.reminder.QuickDone
import java.time.LocalDate
import kotlin.math.roundToInt

/** How a day of the week strip looks. */
internal enum class DayMark { NONE, DONE, MISSED, TODAY, UPCOMING }

/** What a widget shows of one plan today. */
internal class Glance(val plan: PlanEntity, val rows: List<SessionEntity>, val today: LocalDate) {
    val settings = plan.toSettings()

    /** Today's row, or the current one in a plan without dates. */
    val focus: SessionEntity? =
        if (settings.hasDates) rows.firstOrNull { it.localDate == today } else rows.firstOrNull { !it.mainDone(plan) }
    val next: SessionEntity? = if (settings.hasDates) rows.firstOrNull { it.localDate > today && it.pending(plan) } else null
    val complete: Boolean = rows.isNotEmpty() && rows.all { it.mainDone(plan) }
    val stats: PlanStats = planStats(plan, rows, today)
    val missed: Int = if (settings.hasDates) missedCount(plan, rows, today) else 0
    val percent: Int get() = (stats.progress * 100).roundToInt()

    /** Something of today is still to do. */
    val busyToday: Boolean get() = focus?.pending(plan) == true

    /** The items of [row] that fall on its day, with ✓ or ○; [withLesson] false leaves the lesson out. */
    fun items(row: SessionEntity, withLesson: Boolean = true): List<String> = buildList {
        val s = settings
        if (withLesson && s.includeNew && row.has(Item.NEW) && row.hasLesson) add("${mark(row.newDone)} الحفظ: ${PlanText.lesson(row)}")
        if (s.hasMinor && row.has(Item.MINOR)) {
            PlanText.minor(row).takeIf { it != RangeFormatter.NONE }?.let { add("${mark(row.minorDone)} الصغرى: $it") }
        }
        if (s.includeMajor && row.has(Item.MAJOR)) {
            PlanText.major(row).takeIf { it.isNotBlank() }?.let { add("${mark(row.majorDone)} الكبرى: $it") }
        }
    }

    /** A heading, and the session it is about: today's (with ✓ once all of it is done), the current one, or the next. */
    fun headline(): Pair<String, String> {
        val f = focus
        val n = next
        return when {
            f != null -> {
                val heading = if (settings.hasDates) "${PlanText.dayWord(f, plan)} اليوم" else "الجلسة الحالية"
                (if (f.pending(plan)) heading else "✓ $heading") to PlanText.main(f, plan)
            }
            complete -> "ما شاء الله" to "اكتملت الخطة"
            n != null -> "الجاية ${DateText.withWeekday(n.localDate)}" to PlanText.main(n, plan)
            else -> "ما فيه درس اليوم" to RangeFormatter.NONE
        }
    }

    /** The lines of the «قائمة الخطط» widget, as it has always shown them. */
    fun summary(): String {
        val lines = mutableListOf<String>()
        val f = focus
        val n = next
        if (f != null) {
            lines += items(f).ifEmpty { listOf(PlanText.main(f, plan)) }
        } else {
            lines += when {
                complete -> "اكتملت الخطة"
                n != null -> "ما فيه درس اليوم. الجاي ${DateText.withWeekday(n.localDate)}: ${PlanText.main(n, plan)}"
                else -> "ما فيه درس اليوم"
            }
        }
        if (missed > 0) lines += Plurals.missed(missed)
        return lines.joinToString("\n")
    }

    /**
     * Under the lesson: today's reviews, and the missed lessons. A day of reviews only already shows
     * them as its headline.
     */
    fun details(): String {
        val f = focus
        val lines = mutableListOf<String>()
        if (f != null && f.hasLesson) lines += items(f, withLesson = false)
        if (missed > 0) lines += Plurals.missed(missed)
        return lines.joinToString("\n")
    }

    /** The seven days of this week, Sunday first. */
    fun week(): List<Pair<LocalDate, DayMark>> {
        val start = today.minusDays((today.dayOfWeek.value % 7).toLong())
        return (0..6).map { i ->
            val day = start.plusDays(i.toLong())
            val row = if (settings.hasDates) rows.firstOrNull { it.localDate == day } else null
            day to when {
                row == null -> DayMark.NONE
                !row.pending(plan) -> DayMark.DONE
                day < today -> DayMark.MISSED
                day == today -> DayMark.TODAY
                else -> DayMark.UPCOMING
            }
        }
    }

    private fun mark(done: Boolean) = if (done) "✓" else "○"
}

/**
 * A widget's colors as calls on its views. With [night], the launcher picks the light or dark colors
 * by itself when the phone switches (Android 12 and later); otherwise [now] is set as it is.
 */
internal class Ink(private val now: WidgetColors, private val night: WidgetColors?) {
    private val dual: Boolean get() = night != null && Build.VERSION.SDK_INT >= 31

    fun text(v: RemoteViews, id: Int, pick: (WidgetColors) -> Int) {
        val n = night
        if (dual && n != null && Build.VERSION.SDK_INT >= 31) v.setColorInt(id, "setTextColor", pick(now), pick(n)) else v.setTextColor(id, pick(now))
    }

    /** Tints a white drawable: its color through a filter, its see-through through the image alpha. */
    fun tint(v: RemoteViews, id: Int, pick: (WidgetColors) -> Int) {
        val n = night
        val color = pick(now)
        if (dual && n != null && Build.VERSION.SDK_INT >= 31) {
            v.setColorInt(id, "setColorFilter", opaque(color), opaque(pick(n)))
        } else {
            v.setInt(id, "setColorFilter", opaque(color))
        }
        v.setInt(id, "setImageAlpha", color ushr 24)
    }

    fun picture(v: RemoteViews, id: Int, draw: (WidgetColors) -> Bitmap) {
        val n = night
        if (dual && n != null && Build.VERSION.SDK_INT >= 31) {
            v.setIcon(id, "setImageIcon", Icon.createWithBitmap(draw(now)), Icon.createWithBitmap(draw(n)))
        } else {
            v.setImageViewBitmap(id, draw(now))
        }
    }

    /** The widget's background: a tinted shape, the design's gradient, or dark glass. */
    fun background(v: RemoteViews, design: AppDesign, look: WidgetLook) {
        val gradient = if (look == WidgetLook.COLOR) gradientOf(design) else null
        if (gradient != null) {
            v.setImageViewResource(R.id.widget_bg, gradient)
            v.setInt(R.id.widget_bg, "setColorFilter", 0)
            v.setInt(R.id.widget_bg, "setImageAlpha", 255)
        } else {
            v.setImageViewResource(R.id.widget_bg, R.drawable.widget_bg)
            tint(v, R.id.widget_bg) { c -> if (look == WidgetLook.COLOR) c.backgrounds[1] else c.backgrounds[0] }
            // The glass shows the wallpaper through it.
            v.setInt(R.id.widget_bg, "setImageAlpha", now.backgroundAlpha)
        }
    }

    private fun opaque(color: Int) = color or (0xFF shl 24)

    companion object {
        fun of(context: Context, prefs: Prefs): Ink {
            val look = prefs.widgetLook
            fun colors(dark: Boolean) = WidgetColors.of(PhoneDesigns.palette(context, prefs.design, dark, prefs), look)
            // Only the app's own colors differ between light and dark.
            return if (Build.VERSION.SDK_INT >= 31 && prefs.theme == ThemeMode.SYSTEM && look == WidgetLook.APP) {
                Ink(colors(false), colors(true))
            } else {
                Ink(colors(prefs.theme.isDark(PhoneDesigns.phoneIsDark(context))), null)
            }
        }

        private fun gradientOf(design: AppDesign): Int? = when (design) {
            AppDesign.EMERALD -> R.drawable.widget_bg_emerald
            AppDesign.NIGHT -> R.drawable.widget_bg_night
            AppDesign.PAPER -> R.drawable.widget_bg_paper
            AppDesign.BURGUNDY -> R.drawable.widget_bg_burgundy
            AppDesign.TEAL -> R.drawable.widget_bg_teal
            AppDesign.GRAPHITE -> R.drawable.widget_bg_graphite
            AppDesign.DYNAMIC, AppDesign.CUSTOM -> null
        }
    }
}

/** Builds the views of every kind of widget from the plans. */
object WidgetRenderer {
    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    private val DAY_NAMES = intArrayOf(R.id.day1_name, R.id.day2_name, R.id.day3_name, R.id.day4_name, R.id.day5_name, R.id.day6_name, R.id.day7_name)
    private val DAY_DOTS = intArrayOf(R.id.day1_dot, R.id.day2_dot, R.id.day3_dot, R.id.day4_dot, R.id.day5_dot, R.id.day6_dot, R.id.day7_dot)
    private val DAY_LETTERS = listOf("ح", "ن", "ث", "ر", "خ", "ج", "س")
    private val BLOCKS = listOf(
        listOf(R.id.plan1, R.id.plan1_name, R.id.plan1_text, R.id.plan1_ring, R.id.plan1_percent),
        listOf(R.id.plan2, R.id.plan2_name, R.id.plan2_text, R.id.plan2_ring, R.id.plan2_percent),
        listOf(R.id.plan3, R.id.plan3_name, R.id.plan3_text, R.id.plan3_ring, R.id.plan3_percent),
    )
    private val CHECKS = intArrayOf(R.id.plan1_check, R.id.plan2_check, R.id.plan3_check)

    /** The widget [widgetId] of [kind] as it should look now (0 for a preview). */
    suspend fun build(app: HifzApp, kind: WidgetKind, widgetId: Int): RemoteViews {
        val prefs = app.prefs.values.value
        val today = LocalDate.now()
        val glances = app.repository.allPlans().map { Glance(it, app.repository.getSessions(it.id), today) }
        val ink = Ink.of(app, prefs)
        val views = RemoteViews(app.packageName, kind.layout)
        ink.background(views, prefs.design, prefs.widgetLook)
        val density = app.resources.displayMetrics.density
        val plan = if (kind.singlePlan) pick(glances, if (widgetId == 0) 0 else WidgetPlans.chosen(app, widgetId)) else null
        // «إخفاء الأرقام»: no percentages, counts or streaks; the ring and the bar stay as drawings.
        val hide = prefs.hideNumbers
        when (kind) {
            WidgetKind.LIST -> list(app, views, ink, glances, today, density, hide)
            WidgetKind.COMPACT -> compact(app, views, ink, plan, density, hide)
            WidgetKind.RING -> ring(views, ink, plan, density, hide)
            WidgetKind.WEEK -> week(views, ink, plan)
            WidgetKind.BOARD -> board(app, views, ink, plan, today, density, hide)
        }
        val open = MainActivity.intent(app, plan?.plan?.id)
        views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(app, 300 + kind.ordinal, open, FLAGS))
        return views
    }

    /** The chosen plan; otherwise the one with something left today, then one with a session today, then any. */
    private fun pick(glances: List<Glance>, chosen: Long): Glance? =
        glances.firstOrNull { it.plan.id == chosen }
            ?: glances.firstOrNull { it.busyToday }
            ?: glances.firstOrNull { it.focus != null }
            ?: glances.firstOrNull { !it.complete }
            ?: glances.firstOrNull()

    private fun count(g: Glance) = "${g.stats.done} من ${Plurals.sessions(g.stats.total)}"

    /**
     * «تم ✓» for [g]'s session of today (the current one in a plan without dates): a tap ticks all of
     * it, and once it is done opens the plan. Hidden when there is none.
     */
    private fun check(app: HifzApp, v: RemoteViews, ink: Ink, id: Int, g: Glance?, sizeDp: Int, density: Float) {
        val row = g?.focus
        if (g == null || row == null) {
            v.setViewVisibility(id, View.GONE)
            return
        }
        val done = !row.pending(g.plan)
        v.setViewVisibility(id, View.VISIBLE)
        ink.picture(v, id) { c ->
            val under = c.seenBackgrounds[c.seenBackgrounds.size / 2] or (0xFF shl 24)
            WidgetArt.check(done, c.done, under, (sizeDp * density).toInt())
        }
        v.setContentDescription(id, if (done) "${g.plan.name}: تم ✓" else "علّم ${g.plan.name}: تم")
        val tap = if (done) {
            PendingIntent.getActivity(app, 0, MainActivity.intent(app, g.plan.id), FLAGS)
        } else {
            PendingIntent.getBroadcast(app, 0, QuickDone.intent(app, row.id), FLAGS)
        }
        v.setOnClickPendingIntent(id, tap)
    }

    private fun list(app: HifzApp, v: RemoteViews, ink: Ink, glances: List<Glance>, today: LocalDate, density: Float, hide: Boolean) {
        v.setTextViewText(R.id.widget_date, DateText.withWeekday(today))
        ink.text(v, R.id.widget_title) { it.title }
        ink.text(v, R.id.widget_date) { it.muted }
        ink.text(v, R.id.widget_empty) { it.text }
        v.setViewVisibility(R.id.widget_empty, if (glances.isEmpty()) View.VISIBLE else View.GONE)
        BLOCKS.forEachIndexed { i, (block, name, text, ring, percent) ->
            val g = glances.getOrNull(i)
            if (g == null) {
                v.setViewVisibility(block, View.GONE)
                return@forEachIndexed
            }
            v.setViewVisibility(block, View.VISIBLE)
            v.setTextViewText(name, g.plan.name)
            v.setTextViewText(text, g.summary())
            v.setTextViewText(percent, if (hide) "" else "${g.percent}")
            ink.text(v, name) { it.text }
            ink.text(v, text) { it.muted }
            ink.text(v, percent) { it.text }
            ink.picture(v, ring) { WidgetArt.ring(g.stats.progress, it.arc, it.track, (36 * density).toInt()) }
            v.setOnClickPendingIntent(block, PendingIntent.getActivity(app, 201 + i, MainActivity.intent(app, g.plan.id), FLAGS))
            check(app, v, ink, CHECKS[i], g, 28, density)
        }
    }

    private fun compact(app: HifzApp, v: RemoteViews, ink: Ink, g: Glance?, density: Float, hide: Boolean) {
        listOf(R.id.compact_plan, R.id.compact_detail, R.id.compact_count).forEach { id -> ink.text(v, id) { it.muted } }
        ink.text(v, R.id.compact_lesson) { it.text }
        if (g == null) {
            v.setTextViewText(R.id.compact_plan, "جدول الحفظ")
            v.setTextViewText(R.id.compact_lesson, "ما عندك خطط للحين")
            v.setTextViewText(R.id.compact_detail, "افتح التطبيق وأنشئ خطتك")
            v.setTextViewText(R.id.compact_count, "")
            v.setViewVisibility(R.id.compact_bar, View.GONE)
            v.setViewVisibility(R.id.compact_check, View.GONE)
            return
        }
        val (heading, lesson) = g.headline()
        v.setTextViewText(R.id.compact_plan, "${g.plan.name} · $heading")
        v.setTextViewText(R.id.compact_lesson, lesson)
        v.setTextViewText(R.id.compact_detail, g.details())
        v.setTextViewText(R.id.compact_count, count(g))
        v.setViewVisibility(R.id.compact_count, if (hide) View.GONE else View.VISIBLE)
        v.setViewVisibility(R.id.compact_bar, View.VISIBLE)
        ink.picture(v, R.id.compact_bar) { WidgetArt.bar(g.stats.progress, it.arc, it.track) }
        check(app, v, ink, R.id.compact_check, g, 32, density)
    }

    private fun ring(v: RemoteViews, ink: Ink, g: Glance?, density: Float, hide: Boolean) {
        ink.text(v, R.id.ring_percent) { it.text }
        ink.text(v, R.id.ring_name) { it.text }
        ink.text(v, R.id.ring_count) { it.muted }
        val progress = g?.stats?.progress ?: 0f
        v.setTextViewText(R.id.ring_percent, if (hide) "" else "${g?.percent ?: 0}%")
        v.setTextViewText(R.id.ring_name, g?.plan?.name ?: "جدول الحفظ")
        v.setTextViewText(R.id.ring_count, g?.let { count(it) } ?: "ما عندك خطط للحين")
        v.setViewVisibility(R.id.ring_count, if (hide && g != null) View.GONE else View.VISIBLE)
        ink.picture(v, R.id.ring_ring) { WidgetArt.ring(progress, it.arc, it.track, (76 * density).toInt()) }
    }

    private fun week(v: RemoteViews, ink: Ink, g: Glance?) {
        ink.text(v, R.id.week_lesson) { it.text }
        ink.text(v, R.id.week_plan) { it.muted }
        if (g == null) {
            v.setTextViewText(R.id.week_lesson, "ما عندك خطط للحين")
            v.setTextViewText(R.id.week_plan, "")
        } else {
            val (heading, lesson) = g.headline()
            v.setTextViewText(R.id.week_lesson, "$heading: $lesson")
            v.setTextViewText(R.id.week_plan, g.plan.name)
        }
        days(v, ink, g)
    }

    private fun board(app: HifzApp, v: RemoteViews, ink: Ink, g: Glance?, today: LocalDate, density: Float, hide: Boolean) {
        ink.text(v, R.id.board_plan) { it.title }
        listOf(R.id.board_date, R.id.board_heading, R.id.board_items, R.id.board_footer).forEach { id -> ink.text(v, id) { it.muted } }
        ink.text(v, R.id.board_lesson) { it.text }
        ink.text(v, R.id.board_percent) { it.text }
        v.setTextViewText(R.id.board_date, DateText.withWeekday(today))
        if (g == null) {
            v.setTextViewText(R.id.board_plan, "جدول الحفظ")
            v.setTextViewText(R.id.board_heading, "")
            v.setTextViewText(R.id.board_lesson, "ما عندك خطط للحين")
            v.setTextViewText(R.id.board_items, "افتح التطبيق وأنشئ خطتك")
            v.setTextViewText(R.id.board_footer, "")
            v.setTextViewText(R.id.board_percent, "")
        } else {
            val (heading, lesson) = g.headline()
            v.setTextViewText(R.id.board_plan, g.plan.name)
            v.setTextViewText(R.id.board_heading, heading)
            v.setTextViewText(R.id.board_lesson, lesson)
            v.setTextViewText(R.id.board_items, g.details())
            v.setTextViewText(R.id.board_percent, if (hide) "" else "${g.percent}%")
            val streak = g.stats.currentStreak
            v.setTextViewText(R.id.board_footer, count(g) + if (g.settings.hasDates && streak > 1) " · 🔥 $streak متتالية" else "")
        }
        v.setViewVisibility(R.id.board_footer, if (hide && g != null) View.GONE else View.VISIBLE)
        check(app, v, ink, R.id.board_check, g, 32, density)
        val progress = g?.stats?.progress ?: 0f
        ink.picture(v, R.id.board_ring) { WidgetArt.ring(progress, it.arc, it.track, (64 * density).toInt()) }
        days(v, ink, g)
    }

    /** The week strip: a letter and a dot for each day, today's letter bold. */
    private fun days(v: RemoteViews, ink: Ink, g: Glance?) {
        val marks = g?.week() ?: List(7) { LocalDate.now() to DayMark.NONE }
        marks.forEachIndexed { i, (_, mark) ->
            val name = DAY_NAMES[i]
            val dot = DAY_DOTS[i]
            val letter = DAY_LETTERS[i]
            val isToday = g != null && i == (g.today.dayOfWeek.value % 7)
            v.setTextViewText(name, if (isToday) bold(letter) else letter)
            ink.text(v, name) { if (isToday) it.today else it.muted }
            v.setImageViewResource(dot, if (mark == DayMark.UPCOMING) R.drawable.widget_dot_ring else R.drawable.widget_dot)
            ink.tint(v, dot) { c ->
                when (mark) {
                    DayMark.DONE -> c.done
                    DayMark.MISSED -> c.missed
                    DayMark.TODAY -> c.today
                    DayMark.UPCOMING -> c.muted
                    DayMark.NONE -> c.idle
                }
            }
        }
    }

    private fun bold(text: String): CharSequence =
        SpannableString(text).apply { setSpan(StyleSpan(Typeface.BOLD), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
}
