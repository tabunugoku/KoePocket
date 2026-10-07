package app.tabunugoku.koepocket

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tabunugoku.koepocket.data.AppSettings
import app.tabunugoku.koepocket.data.FileStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 既定の保存先 (Music/KoePocket) への書き込み・確定・中断後の掃除。 */
@RunWith(AndroidJUnit4::class)
class FileStoreTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var settings: AppSettings
    private val committed = mutableListOf<String>()

    @Before
    fun setUp() {
        settings = AppSettings(ctx).also { it.downloadTreeUri = null }
    }

    @After
    fun tearDown() {
        committed.forEach { FileStore.delete(ctx, it) }
    }

    private fun readBytes(path: String): ByteArray =
        ctx.contentResolver.openInputStream(Uri.parse(FileStore.playUri(path)))!!.use { it.readBytes() }

    @Test
    fun committedFileIsReadableAndSurvivesCleanup() {
        val t = FileStore.open(ctx, settings, 990001, "commit-test")
        t.out.use { it.write(byteArrayOf(1, 2, 3)) }
        val path = t.commit().also { committed += it }
        FileStore.cleanupOrphans(ctx, settings)
        assertEquals(listOf<Byte>(1, 2, 3), readBytes(path).toList())
    }

    /** Music/KoePocket にある、表示名が [prefix] で始まるファイルの表示名 (書き込み中のものも含む)。 */
    private fun namesStartingWith(prefix: String): List<String> {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?")
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf("$prefix%"))
            putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
        }
        return ctx.contentResolver.query(collection, arrayOf(MediaStore.Audio.Media.DISPLAY_NAME), args, null)!!.use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
    }

    @Test
    fun redownloadReplacesPreviousFileWithoutDuplicateName() {
        val first = FileStore.open(ctx, settings, 990003, "again").also { t -> t.out.use { it.write(byteArrayOf(1)) } }.commit()
        val second = FileStore.open(ctx, settings, 990003, "again").also { t -> t.out.use { it.write(byteArrayOf(2, 2)) } }.commit()
        committed += second
        assertEquals(listOf("990003_again.mp3"), namesStartingWith("990003_"))
        assertEquals(listOf<Byte>(2, 2), readBytes(second).toList())
        // 前のファイルは入れ替えで消えている
        assertEquals(true, runCatching { readBytes(first) }.isFailure)
    }

    @Test
    fun failedRedownloadKeepsPreviousFile() {
        val first = FileStore.open(ctx, settings, 990004, "keep").also { t -> t.out.use { it.write(byteArrayOf(7)) } }.commit()
        committed += first
        val retry = FileStore.open(ctx, settings, 990004, "keep")
        retry.out.close()
        retry.discard()
        assertEquals(listOf<Byte>(7), readBytes(first).toList())
        assertEquals(listOf("990004_keep.mp3"), namesStartingWith("990004_"))
    }

    @Test
    fun cleanupSkipsFilesCreatedAfterTheCutoff() {
        val t = FileStore.open(ctx, settings, 990005, "running")
        t.out.use { it.write(byteArrayOf(1)) }
        // 1分前を基準にした掃除では、いま作った書きかけは消さない
        FileStore.cleanupOrphans(ctx, settings, olderThanMs = System.currentTimeMillis() - 60_000)
        assertEquals(1, namesStartingWith("990005_").size)
        // 基準を未来にすれば消える
        FileStore.cleanupOrphans(ctx, settings, olderThanMs = System.currentTimeMillis() + 60_000)
        assertEquals(0, namesStartingWith("990005_").size)
    }

    @Test
    fun longTitleWithEmojiIsNotCutInsideSurrogatePair() {
        val title = "a".repeat(59) + "😀" + "tail"
        val t = FileStore.open(ctx, settings, 990006, title)
        t.out.use { it.write(byteArrayOf(1)) }
        committed += t.commit()
        val name = namesStartingWith("990006_").single()
        assertEquals(true, name.codePoints().noneMatch { it in 0xD800..0xDFFF })
        assertEquals(true, name.contains("😀"))
    }

    @Test
    fun interruptedDownloadIsRemovedByCleanup() {
        // 書き込み中 (IS_PENDING) のまま中断された状態を作る
        val t = FileStore.open(ctx, settings, 990002, "pending-test")
        t.out.use { it.write(byteArrayOf(9)) }
        FileStore.cleanupOrphans(ctx, settings)
        // 掃除後は、書き込み中のものも含めて MediaStore に残っていない
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?")
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf("990002_pending-test%"))
            putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
        }
        val left = ctx.contentResolver.query(collection, arrayOf(MediaStore.Audio.Media._ID), args, null)!!.use { it.count }
        assertEquals(0, left)
    }
}
