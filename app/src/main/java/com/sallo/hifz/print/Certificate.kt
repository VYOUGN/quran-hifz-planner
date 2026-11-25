package com.sallo.hifz.print

import com.sallo.hifz.common.Plurals
import com.sallo.hifz.dates.DateText
import com.sallo.hifz.dates.Hijri
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** «شهادة إتمام»: an A4 landscape page with a double frame, printed or saved as PDF. */
object Certificate {
    /** «الجزء الثلاثين»: the juz by its ordinal, as said in a sentence. */
    private val ORDINALS = listOf(
        "الأول", "الثاني", "الثالث", "الرابع", "الخامس", "السادس", "السابع", "الثامن", "التاسع", "العاشر",
        "الحادي عشر", "الثاني عشر", "الثالث عشر", "الرابع عشر", "الخامس عشر", "السادس عشر", "السابع عشر",
        "الثامن عشر", "التاسع عشر", "العشرين", "الحادي والعشرين", "الثاني والعشرين", "الثالث والعشرين",
        "الرابع والعشرين", "الخامس والعشرين", "السادس والعشرين", "السابع والعشرين", "الثامن والعشرين",
        "التاسع والعشرين", "الثلاثين",
    )

    fun juz(n: Int): String = "الجزء ${ORDINALS.getOrElse(n - 1) { n.toString() }}"

    /**
     * [what]: what was completed, such as «حفظ الجزء الثلاثين». [name] may be blank. The colors are the
     * design's: [accent] for the frame and [ink] for the title.
     */
    fun html(name: String, what: String, from: LocalDate, to: LocalDate, sessions: Int, accent: Int, ink: Int, today: LocalDate): String {
        val days = (ChronoUnit.DAYS.between(from, to) + 1).toInt().coerceAtLeast(1)
        val gold = css(accent)
        val title = css(ink)
        val who = if (name.isBlank()) "نبارك لك" else "نبارك لـ <span class=\"name\">${esc(name.trim())}</span>"
        return """
            <!DOCTYPE html>
            <html dir="rtl" lang="ar"><head><meta charset="utf-8">
            <style>
              @page { size: A4 landscape; margin: 0; }
              html, body { margin: 0; padding: 0; }
              body { font-family: serif; color: #222; }
              .page { width: 297mm; height: 210mm; box-sizing: border-box; padding: 12mm; }
              .frame { height: 100%; box-sizing: border-box; border: 3mm double $gold; padding: 4mm; }
              .inner { height: 100%; box-sizing: border-box; border: 0.6mm solid $gold; display: flex;
                       flex-direction: column; align-items: center; justify-content: center; text-align: center; padding: 8mm 20mm; }
              .orn { color: $gold; font-size: 26pt; letter-spacing: 6mm; }
              h1 { color: $title; font-size: 44pt; margin: 4mm 0 6mm; }
              .lead { font-size: 20pt; margin: 0; }
              .name { color: $title; font-size: 30pt; font-weight: bold; }
              .what { font-size: 24pt; margin: 6mm 0; font-weight: bold; }
              .dates { font-size: 15pt; color: #444; margin: 2mm 0; line-height: 1.7; }
              .hadith { margin-top: 8mm; font-size: 17pt; color: $title; }
              .hadith span { font-size: 12pt; color: #666; }
              .foot { margin-top: 6mm; font-size: 11pt; color: #777; }
            </style></head><body>
            <div class="page"><div class="frame"><div class="inner">
              <div class="orn">۞ ۞ ۞</div>
              <h1>شهادة إتمام</h1>
              <p class="lead">$who</p>
              <p class="what">بمناسبة إتمام ${esc(what)}</p>
              <p class="dates">من ${esc(DateText.long(from))} إلى ${esc(DateText.long(to))}<br>
                ${esc(Hijri.of(from).long)} – ${esc(Hijri.of(to).long)}<br>
                ${esc(Plurals.sessions(sessions))} في ${esc(Plurals.days(days))}</p>
              <p class="hadith">«خيرُكم من تعلَّم القرآنَ وعلَّمه» <span>رواه البخاري</span></p>
              <p class="foot">جدول الحفظ · ${esc(DateText.long(today))}</p>
            </div></div></div>
            </body></html>
        """.trimIndent()
    }

    private fun css(argb: Int): String = String.format("#%06X", argb and 0xFFFFFF)

    private fun esc(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
