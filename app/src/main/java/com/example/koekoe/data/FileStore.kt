package com.example.koekoe.data

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

    private fun safeName(title: String) = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60)

    /** 書きかけのファイルにつける拡張子 (SAF・アプリ専用フォルダ)。完了時に .mp3 へ改名する。 */
    private const val PART_EXT = ".part"

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
        val out = ctx.contentResolver.openOutputStream(doc.uri) ?: return null
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
        // 同名の自分のファイルが残っていれば先に消す (消えないと「名前 (1).mp3」になる)
        runCatching {
            resolver.delete(
                collection,
                "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} = ?",
                arrayOf("$base.mp3", relative),
            )
        }
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$base.mp3")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, relative)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return null
        val out = resolver.openOutputStream(uri) ?: return null
        Target(
            out,
            commit = {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
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
     * 対象は、このアプリが付けた印 (MediaStore の IS_PENDING / 拡張子 .part) のあるファイルだけ。
     */
    fun cleanupOrphans(ctx: Context, settings: AppSettings) = runCatching {
        if (hasMediaStore) {
            val resolver = ctx.contentResolver
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val args = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.Audio.Media.RELATIVE_PATH} = ?")
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf("${Environment.DIRECTORY_MUSIC}/$DEFAULT_DIR_NAME/"))
                putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_ONLY)
            }
            val ids = mutableListOf<Long>()
            resolver.query(collection, arrayOf(MediaStore.Audio.Media._ID), args, null)?.use { c ->
                while (c.moveToNext()) ids += c.getLong(0)
            }
            ids.forEach { resolver.delete(ContentUris.withAppendedId(collection, it), null, null) }
        }
        ctx.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.listFiles { f -> f.name.endsWith(PART_EXT) }?.forEach { it.delete() }
        settings.downloadTreeUri?.let { tree ->
            DocumentFile.fromTreeUri(ctx, Uri.parse(tree))?.listFiles()?.filter { it.name?.endsWith(PART_EXT) == true }?.forEach { it.delete() }
        }
    }

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
            return if (hasMediaStore) "Music/$DEFAULT_DIR_NAME (既定)" else "アプリ専用フォルダ(既定)"
        }
        val dir = runCatching { DocumentFile.fromTreeUri(ctx, Uri.parse(treeUri)) }.getOrNull()
        return dir?.name ?: treeUri
    }
}
