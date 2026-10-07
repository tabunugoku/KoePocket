package app.tabunugoku.koepocket.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URLEncoder

/** 投稿が存在しない (削除された) ことを表す。 */
class NotFoundException : Exception("not found")

/** サイトへの負荷を抑えるため、HTMLリクエストの間隔に下限を設けている。 */
class KoeKoeApi(
    cacheDir: File,
    // テストで差し替えられるようにしている
    private val baseUrl: String = KoeKoeParser.BASE,
    private val minIntervalMs: Long = MIN_INTERVAL_MS,
) {
    val client: OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(cacheDir, "http"), 20L * 1024 * 1024))
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

    /** 音声の取得 (ダウンロード・再生) 用。音声を HTTP キャッシュに書き込まず、一覧や詳細のキャッシュを押し出さない。 */
    val audioClient: OkHttpClient = client.newBuilder()
        .cache(null)
        // リダイレクト先も含め、すべての通信先が信頼できる音声の取得先であることを確かめる
        .addNetworkInterceptor { chain ->
            val url = chain.request().url.toString()
            if (!KoeKoeParser.isTrustedAudioUrl(url)) throw IOException("untrusted audio host")
            chain.proceed(chain.request())
        }
        .build()

    private val gate = Mutex()
    private var last = 0L

    private suspend fun getHtml(url: String, fresh: Boolean = false): String = withContext(Dispatchers.IO) {
        gate.withLock {
            val wait = minIntervalMs - (System.currentTimeMillis() - last)
            if (wait > 0) delay(wait)
            try {
                client.newCall(Request.Builder().url(url).apply { if (fresh) cacheControl(CacheControl.FORCE_NETWORK) }.build()).execute().use { res ->
                    // 投稿が削除されたページは 404 (または 410) になる
                    if (res.code == 404 || res.code == 410) throw NotFoundException()
                    if (!res.isSuccessful) error("HTTP ${res.code}")
                    (res.body ?: error("empty body")).string()
                }
            } finally {
                last = System.currentTimeMillis()
            }
        }
    }

    /** [fresh] が true のときは、HTTPキャッシュを使わず必ずサイトから取得する (更新操作用)。 */
    suspend fun list(path: String, page: Int, fresh: Boolean = false): VoiceListPage {
        val url = baseUrl + path + if (page > 1) (if ('?' in path) "&p=$page" else "?p=$page") else ""
        return KoeKoeParser.parseList(getHtml(url, fresh))
    }

    private val tagPages = HashMap<Int, List<String>>()

    /**
     * サイトのタグ一覧 (all_tag.php) の [page] ページ目 (1始まり、1ページ60件、人気順)。
     * 全部で約360ページあるので、必要になったぶんだけ取得する。空リストは最後のページを過ぎたこと。
     */
    suspend fun tagsPage(page: Int): List<String> =
        tagPages[page] ?: KoeKoeParser.parseTags(
            getHtml("${baseUrl}all_tag.php" + if (page > 1) "?p=$page" else ""),
        ).also { if (it.isNotEmpty()) tagPages[page] = it }

    suspend fun detail(id: Long): VoiceDetail? =
        KoeKoeParser.parseDetail(getHtml("${baseUrl}detail.php?n=$id"), id)

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) KoeKoeViewer/0.1"
        private const val MIN_INTERVAL_MS = 400L

        /** [g] は性別の絞り込み (1=女性, 2=男性, 3=カップル)。サイト側では1種類しか指定できない。 */
        fun searchPath(word: String, g: Int? = null) =
            "search.php?word=" + URLEncoder.encode(word, "UTF-8") + if (g != null) "&g=$g" else ""

        fun tagPath(tag: String) = "tag_list.php?tag=" + URLEncoder.encode(tag, "UTF-8")
    }
}
