package app.tabunugoku.koepocket.ui

import android.content.Context
import app.tabunugoku.koepocket.R

/**
 * サイトが日本語で返す時間の表記 (「3分」「1分7秒」「18分前」「2日前」) を、アプリの表示言語にする。
 * 解釈できない表記 (日付の「12/11/17」など) は、そのまま返す。
 */
object SiteText {
    private val DURATION_PART = Regex("""(\d+)\s*(時間|分|秒)""")
    private val AGO = Regex("""^(\d+)\s*(秒|分|時間|日|週間|週|か月|ヶ月|カ月|ケ月|年)\s*前$""")

    fun duration(ctx: Context, text: String): String {
        val parts = DURATION_PART.findAll(text).toList()
        if (parts.isEmpty() || parts.joinToString("") { it.value.filterNot(Char::isWhitespace) } != text.filterNot(Char::isWhitespace)) return text
        return parts.joinToString(ctx.getString(R.string.dur_sep)) {
            val n = it.groupValues[1].toInt()
            when (it.groupValues[2]) {
                "時間" -> ctx.getString(R.string.dur_hour, n)
                "分" -> ctx.getString(R.string.dur_min, n)
                else -> ctx.getString(R.string.dur_sec, n)
            }
        }
    }

    fun ago(ctx: Context, text: String): String {
        val m = AGO.find(text.trim()) ?: return text
        val n = m.groupValues[1].toIntOrNull() ?: return text
        // 「0分前」「0秒前」は、そのまま訳すと不自然なので「たった今」にする
        if (n == 0 && m.groupValues[2] in listOf("秒", "分")) return ctx.getString(R.string.ago_now)
        val res = when (m.groupValues[2]) {
            "秒" -> R.plurals.ago_seconds
            "分" -> R.plurals.ago_minutes
            "時間" -> R.plurals.ago_hours
            "日" -> R.plurals.ago_days
            "週間", "週" -> R.plurals.ago_weeks
            "年" -> R.plurals.ago_years
            else -> R.plurals.ago_months
        }
        return ctx.resources.getQuantityString(res, n, n)
    }
}
