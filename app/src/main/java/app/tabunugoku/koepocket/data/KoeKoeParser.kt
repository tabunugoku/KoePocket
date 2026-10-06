package app.tabunugoku.koepocket.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object KoeKoeParser {
    const val BASE = "https://koe-koe.com/"

    fun parseList(html: String): VoiceListPage {
        val doc = Jsoup.parse(html, BASE)
        val items = doc.select("div.content > a[href^=detail.php?n=]").mapNotNull(::parseItem)
        val hasNext = doc.select("a.prev_next[href]").any { it.select(".pager_next_text").isNotEmpty() }
        return VoiceListPage(items.distinctBy { it.id }, hasNext)
    }

    private fun parseItem(a: Element): VoiceItem? {
        val id = a.attr("href").substringAfter("n=").takeWhile { it.isDigit() }.toLongOrNull() ?: return null
        val line = a.selectFirst("p.desc_auth_title") ?: return null
        val author = line.selectFirst(".entry_auth")?.text().orEmpty().trim()
        val title = line.text().removePrefix(author).trimStart().removePrefix(":").trim()
        val gender = a.selectFirst("div.icon")?.classNames()
            ?.firstOrNull { it.startsWith("icon_") }?.removePrefix("icon_").orEmpty()
        val meta = a.selectFirst("p.meta")?.text().orEmpty()
        return VoiceItem(
            id = id,
            title = title,
            author = author,
            gender = gender,
            duration = a.selectFirst(".audioTime")?.text().orEmpty(),
            comments = Regex("""コメ\s*:\s*(\d+)""").find(meta)?.groupValues?.get(1)?.toIntOrNull() ?: 0,
            likes = Regex("""いいね\s*:\s*(\d+)""").find(meta)?.groupValues?.get(1)?.toIntOrNull() ?: 0,
            postedAgo = Regex("""@(\S+)""").find(meta)?.groupValues?.get(1).orEmpty(),
        )
    }

    /** 詳細ページ本体の長さは audioTime_<数字>。関連投稿の一覧は audioTime_female 等なので区別する。 */
    private fun duration(doc: org.jsoup.nodes.Document): String =
        doc.select("div.audioTime").firstOrNull { el -> el.classNames().any { it.matches(Regex("""audioTime_\d+""")) } }
            ?.text().orEmpty()

    fun parseDetail(html: String, id: Long): VoiceDetail? {
        val doc = Jsoup.parse(html, BASE)
        val src = doc.selectFirst("audio[src], audio source[src]")?.attr("src") ?: return null
        val audioUrl = when {
            src.startsWith("//") -> "https:$src"
            src.startsWith("http") -> src
            else -> BASE + src.removePrefix("./")
        }
        if (!isTrustedAudioUrl(audioUrl)) return null
        val title = doc.title().substringBefore(" - Koe-Koe").replace(Regex("""\s*\[\d+]$"""), "").trim()
        val tags = doc.select("#tag a[href^=tag_list.php]").map { it.text() }
        val comments = doc.select("p:has(> span.entry_auth)").mapNotNull { p ->
            val m = Regex("""^(\d+)\.\s*(.*?)\s*:\s*(.*)$""", RegexOption.DOT_MATCHES_ALL).find(p.text()) ?: return@mapNotNull null
            // 投稿日時は直後の <p>@12/11/17 …</p> にある
            val posted = p.nextElementSibling()?.text()?.trim().orEmpty().takeIf { it.startsWith("@") }
                ?.removePrefix("@")?.trim().orEmpty()
            VoiceComment(m.groupValues[1].toInt(), p.selectFirst(".entry_auth")!!.text(), m.groupValues[3], posted)
        }
        val desc = doc.selectFirst("div.desc.detail")
        val authorLink = desc?.selectFirst("a[href*=m=1]")
        val author = authorLink?.selectFirst(".user_name")?.text() ?: authorLink?.text().orEmpty()
        val genreLink = desc?.selectFirst("p.meta a[href^=list.php]")
        val body = desc?.selectFirst("p:not(.meta):not(.b_btn)")?.also { it.select("br").append("\\n") }
        val description = body?.text().orEmpty().replace("\\n", "\n").lines()
            .joinToString("\n") { it.trim() }.trim()
            .removePrefix(author).trimStart().removePrefix(":").trim()
        return VoiceDetail(
            id, title, audioUrl, duration(doc), tags, comments,
            author = author,
            authorPath = authorLink?.attr("href")?.takeIf { it.isNotBlank() },
            genre = genreLink?.text().orEmpty(),
            genrePath = genreLink?.attr("href")?.takeIf { it.isNotBlank() },
            description = description,
            // 性別は、投稿者・ジャンルのリンクの g パラメータにある
            gender = genderFromG(
                Regex("""[?&]g=(\d)""").find(authorLink?.attr("href").orEmpty() + "&" + genreLink?.attr("href").orEmpty())
                    ?.groupValues?.get(1)?.toIntOrNull(),
            ),
        )
    }

    /** 音声の取得先は、koe-koe.com とそのサブドメイン (file.koe-koe.com など) の https だけに限る。 */
    fun isTrustedAudioUrl(url: String): Boolean {
        val u = url.toHttpUrlOrNull() ?: return false
        return u.isHttps && (u.host == "koe-koe.com" || u.host.endsWith(".koe-koe.com"))
    }

    /** all_tag.php のタグ一覧 (重複なし、出現順)。 */
    fun parseTags(html: String): List<String> =
        Jsoup.parse(html, BASE).select("a[href^=tag_list.php?tag=]").map { it.text().trim() }
            .filter { it.isNotEmpty() }.distinct()
}
