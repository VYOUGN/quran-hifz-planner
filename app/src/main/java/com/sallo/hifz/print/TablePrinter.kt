package com.sallo.hifz.print

import android.app.Activity
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.sallo.hifz.MainActivity
import com.sallo.hifz.plan.GradeLevel
import com.sallo.hifz.data.Item
import com.sallo.hifz.data.PlanEntity
import com.sallo.hifz.ui.table.RowUi

/** Prints the table like the paper version (separate ✓ columns) through Android's print dialog, which can also save a PDF. */
object TablePrinter {
    /** Keeps the WebView alive until the page is handed to the print framework. */
    private var pending: WebView? = null

    /** [landscape]: an A4 page on its side, as a certificate is. */
    fun print(activity: Activity, planName: String, html: String, landscape: Boolean = false) {
        val view = WebView(activity)
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(webView: WebView, url: String?) {
                val job = "جدول الحفظ - $planName"
                val manager = (activity as? MainActivity)?.printManager()
                    ?: activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
                manager.print(
                    job,
                    webView.createPrintDocumentAdapter(job),
                    PrintAttributes.Builder()
                        .setMediaSize(if (landscape) PrintAttributes.MediaSize.ISO_A4.asLandscape() else PrintAttributes.MediaSize.ISO_A4)
                        .build(),
                )
                pending = null
            }
        }
        pending = view
        view.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    fun html(plan: PlanEntity, description: String, rows: List<RowUi>, showHijri: Boolean): String {
        val columns = rows.firstOrNull()?.items?.map { it.item }.orEmpty()
        val out = StringBuilder()
        out.append(
            """<!doctype html><html dir="rtl" lang="ar"><head><meta charset="utf-8"><style>
@page { size: A4; margin: 12mm; }
body { font-family: sans-serif; color: #111; }
h1 { font-size: 17pt; margin: 0 0 4px; }
.meta { color: #555; font-size: 10pt; margin-bottom: 10px; }
table { width: 100%; border-collapse: collapse; font-size: 10.5pt; }
th, td { border: 1px solid #777; padding: 5px 4px; text-align: center; vertical-align: middle; }
th { background: #e3efe8; }
th.c, td.c { width: 9mm; }
td.d { width: 21mm; white-space: nowrap; }
small, .sub { color: #666; font-size: 8.5pt; }
tr { page-break-inside: avoid; }
thead { display: table-header-group; }
</style></head><body>""",
        )
        out.append("<h1>").append(esc(plan.name)).append("</h1>")
        out.append("<div class=\"meta\">").append(esc(description)).append("</div>")
        out.append("<table><thead><tr><th>").append(if (rows.firstOrNull()?.hasDate == false) "الجلسة" else "التاريخ").append("</th>")
        columns.forEach { out.append("<th>").append(it.label).append("</th><th class=\"c\">✓</th>") }
        out.append("</tr></thead><tbody>")
        for (r in rows) {
            out.append("<tr><td class=\"d\">")
            if (r.hasDate) {
                out.append(esc(r.weekday)).append("<br>").append(r.dateShort)
                if (showHijri && r.hijriShort != null) out.append("<br><small>").append(r.hijriShort).append(" هـ</small>")
            } else {
                out.append(r.index + 1)
            }
            out.append("</td>")
            for (item in r.items) {
                out.append("<td>").append(esc(if (item.empty && item.item == Item.MAJOR) "" else item.text))
                item.sub?.let { out.append("<div class=\"sub\">").append(esc(it)).append("</div>") }
                out.append("</td>").append(checkCell(item.done, item.grade))
            }
            out.append("</tr>")
        }
        out.append("</tbody></table></body></html>")
        return out.toString()
    }

    private fun checkCell(done: Boolean, grade: GradeLevel?): String {
        val parts = mutableListOf<String>()
        if (done) parts += "✓"
        if (grade != null) parts += "<small>${esc(grade.short)}</small>"
        return "<td class=\"c\">${parts.joinToString("<br>")}</td>"
    }

    private fun esc(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
