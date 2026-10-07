package app.tabunugoku.koepocket.data

import app.tabunugoku.koepocket.R
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.OutputStream

/**
 * ダウンロードしたファイルの保存・再生 URI・削除。
 *
 * 保存先は次の優先順位:
 * 1. 設定で選んだフォルダ (SAF) …… [AppSettings.downloadTreeUri]
 * 2. 既定: 端末の `Music/KoePocket` (Android 10 以上。MediaStore 経由なので権限は不要)
 * 3. Android 9 以下: アプリ専用フォルダ
 *
 * [Downloaded.path] は絶対パス(アプリ専用フォルダ)か content:// の URI。
 */
object FileStore {
    /** Music 直下に作る、このアプリ専用のフォルダ名。 */
    const val DEFAULT_DIR_NAME = "KoePocket"

    private val hasMediaStore get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    private fun safeName(title: String): String {
        val s = title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
        // 絵文字などを途中で切らないよう、文字数はコードポイントで数える
        return if (s.codePointCount(0, s.length) <= MAX_NAME_CHARS) s else s.substring(0, s.offsetByCodePoints(0, MAX_NAME_CHARS))
    }

    private const val MAX_NAME_CHARS = 60

    /** 書きかけのファイルにつける拡張子 (SAF・アプリ専用フォルダ)。完了時に .mp3 へ改名する。 */
    private const val PART_EXT = ".part"

    /** MediaStore で書き込み中のファイルにつける名前の印。確定時に外す。 */
    private const val TMP_SUFFIX = ".tmp"

    /** 書き込み先。書き終えたら [commit] (保存先の最終的なパス/URI を返す)、失敗したら [discard] を呼ぶ。 */
    class Target(val out: OutputStream, val commit: () -> String, val discard: () -> Unit)

    fun open(ctx: Context, settings: AppSettings, id: Long, title: String): Target {
        val base = "${id}_${safeName(title)}"
        settings.downloadTreeUri?.let { tree -> openInTree(ctx, tree, base)?.let { return it } }
        if (hasMediaStore) openInMusic(ctx, base)?.let { return it }
        return openInAppDir(ctx, base)
    }

    private fun openInTree(ctx: Context, tree: String, base: String): Target? {
        val dir = runCatching { DocumentFile.fromTreeUri(ctx, Uri.parse(tree)) }.getOrNull()
        if (dir == null || !dir.canWrite()) return null
        dir.findFile("$base$PART_EXT")?.delete()
        val doc = dir.createFile("application/octet-stream", base + PART_EXT) ?: return null
        val out = runCatching { ctx.contentResolver.openOutputStream(doc.uri) }.getOrNull()
        if (out == null) {
            // 書き込めないなら、作った書きかけのファイルを残さない
            doc.delete()
            return null
        }
        return Target(
            out,
            commit = {
                dir.findFile("$base.mp3")?.delete()
                // 改名できなければ .part のまま残す (再生はできる)。renameTo は成功すると doc.uri を新しい URI に更新する
                doc.renameTo("$base.mp3")
                doc.uri.toString()
            },
            discard = { doc.delete() },
        )
    }

    /** Music/KoePocket に MediaStore で保存する。フォルダが無ければ自動で作られる。書き込み中は IS_PENDING で隠す。 */
    private fun openInMusic(ctx: Context, base: String): Target? = runCatching {
        val resolver = ctx.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relative = "${Environment.DIRECTORY_MUSIC}/$DEFAULT_DIR_NAME/"
        // 書き込み中は別名にしておく。同名の前のファイルは、書き終えて確定するときに入れ替える
        // (先に消すと、取り直しに失敗したとき保存済みの記録だけが残ってしまう)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$base$TMP_SUFFIX.mp3")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, relative)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return null
        val out = runCatching { resolver.openOutputStream(uri) }.getOrNull()
        if (out == null) {
            // 書き込めないなら、作った書き込み中の行を残さない
            runCatching { resolver.delete(uri, null, null) }
            return null
        }
        Target(
            out,
            commit = {
                // 更新できないと端末の音楽に出ず再生もできないので、失敗として扱う (呼び出し側で discard される)
                val updated = resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
                check(updated > 0) { "IS_PENDING update failed" }
                // 同名の前のファイルを消して、正式な名前にする。ここの失敗は無視する (別名のままでも再生はできる)
                runCatching {
                    resolver.delete(
                        collection,
                        "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} = ?",
                        arrayOf("$base.mp3", relative),
                    )
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.DISPLAY_NAME, "$base.mp3") }, null, null)
                }
                uri.toString()
            },
            discard = { runCatching { resolver.delete(uri, null, null) } },
        )
    }.getOrNull()

    private fun openInAppDir(ctx: Context, base: String): Target {
        val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: ctx.filesDir
        val part = File(dir, "$base.mp3$PART_EXT")
        val file = File(dir, "$base.mp3")
        return Target(
            part.outputStream(),
            commit = {
                file.delete()
                if (part.renameTo(file)) file.absolutePath else part.absolutePath
            },
            discard = { part.delete() },
        )
    }

    /** 初回起動時に Music/KoePocket を作る (できなければ最初のダウンロード時に自動で作られる)。 */
    fun ensureDefaultDir(ctx: Context) {
        if (!hasMediaStore) return
        runCatching {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), DEFAULT_DIR_NAME).mkdirs()
        }
    }

    /**
     * 中断されて残った書きかけのファイルを消す。ダウンロードが動いていないときだけ呼ぶこと。
     * 対象は、このアプリが付けた印 (MediaStore の IS_PENDING / 「ID_」で始まる .part) のあるファイルだけ。
     * [keep] は保存済みとして記録されているパス。改名に失敗して .part のまま残した再生可能なファイルを消さないために使う。
     * [olderThanMs] より前に作られたファイルだけを消す。掃除の最中に始まったダウンロードの書きかけを巻き込まないために、
     * 「ダウンロードが動いていない」ことを確かめる前の時刻を渡す。
     */
    fun cleanupOrphans(
        ctx: Context,
        settings: AppSettings,
        keep: Set<String> = emptySet(),
        olderThanMs: Long = Long.MAX_VALUE,
    ) = runCatching {
        if (hasMediaStore) {
            val resolver = ctx.contentResolver
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            // DATE_ADDED は秒単位
            val beforeSec = if (olderThanMs == Long.MAX_VALUE) Long.MAX_VALUE else olderThanMs / 1000
            val args = Bundle().apply {
                putString(
                    ContentResolver.QUERY_ARG_SQL_SELECTION,
                    "${MediaStore.Audio.Media.RELATIVE_PATH} = ? AND ${MediaStore.Audio.Media.DATE_ADDED} < ?",
                )
                putStringArray(
                    ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                    arrayOf("${Environment.DIRECTORY_MUSIC}/$DEFAULT_DIR_NAME/", beforeSec.toString()),
                )
                putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_ONLY)
            }
            val ids = mutableListOf<Long>()
            resolver.query(collection, arrayOf(MediaStore.Audio.Media._ID), args, null)?.use { c ->
                while (c.moveToNext()) ids += c.getLong(0)
            }
            ids.forEach { resolver.delete(ContentUris.withAppendedId(collection, it), null, null) }
        }
        ctx.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?.listFiles { f -> isOwnPart(f.name) && f.absolutePath !in keep && isOlder(f.lastModified(), olderThanMs) }
            ?.forEach { it.delete() }
        settings.downloadTreeUri?.let { tree ->
            DocumentFile.fromTreeUri(ctx, Uri.parse(tree))?.listFiles()
                ?.filter { isOwnPart(it.name) && it.uri.toString() !in keep && isOlder(it.lastModified(), olderThanMs) }
                ?.forEach { it.delete() }
        }
    }

    /**
     * [olderThanMs] より前に更新されたファイルか。更新時刻が分からない (0) 場合は、書き込み中かもしれないので古いとはみなさない。
     * [olderThanMs] が最大値のときは、時刻を問わずすべて対象にする。
     */
    private fun isOlder(lastModified: Long, olderThanMs: Long) =
        olderThanMs == Long.MAX_VALUE || lastModified in 1 until olderThanMs

    /** このアプリが書きかけに付けた名前 (「ID_題名….part」) か。保存先が共有フォルダでも、他アプリの .part を巻き込まない。 */
    private fun isOwnPart(name: String?) = name != null && PART_NAME.matches(name)

    private val PART_NAME = Regex("^\\d+_.*" + Regex.escape(PART_EXT) + "$")

    fun isContent(path: String) = path.startsWith("content:")

    /** プレーヤーに渡す URI。 */
    fun playUri(path: String): String = if (isContent(path)) path else File(path).toURI().toString()

    fun delete(ctx: Context, path: String) {
        runCatching {
            if (!isContent(path)) {
                File(path).delete()
            } else {
                val uri = Uri.parse(path)
                if (uri.authority == MediaStore.AUTHORITY) ctx.contentResolver.delete(uri, null, null)
                else DocumentFile.fromSingleUri(ctx, uri)?.delete()
            }
        }
    }

    /** 設定画面に出す、保存先の表示名。 */
    fun displayName(ctx: Context, treeUri: String?): String {
        if (treeUri == null) {
            return if (hasMediaStore) ctx.getString(R.string.dir_default_media, DEFAULT_DIR_NAME) else ctx.getString(R.string.dir_default_app)
        }
        val dir = runCatching { DocumentFile.fromTreeUri(ctx, Uri.parse(treeUri)) }.getOrNull()
        return dir?.name ?: treeUri
    }
}
