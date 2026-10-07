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
