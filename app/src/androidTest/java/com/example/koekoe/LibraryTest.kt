package com.example.koekoe

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.koekoe.data.AppDb
import com.example.koekoe.data.AppSettings
import com.example.koekoe.data.Library
import com.example.koekoe.data.VoiceComment
import com.example.koekoe.data.VoiceDetail
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryTest {
    private lateinit var db: AppDb
    private lateinit var settings: AppSettings
    private lateinit var library: Library

    private fun detail(id: Long, comments: Int = 2) = VoiceDetail(
        id, "題名$id", "https://file.koe-koe.com/$id.mp3", "1分", emptyList(),
        List(comments) { VoiceComment(it + 1, "a", "b") },
        author = "投稿者", gender = "female",
    )

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDb::class.java).allowMainThreadQueries().build()
        settings = AppSettings(ctx).also { it.syncFolders = true }
        library = Library(db, settings)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val dao get() = db.dao()

    @Test
    fun movingFavoriteMovesDownloadWhenSynced() = runBlocking {
        library.addFavorite(detail(1))
        library.addDownload(detail(1), "p")
        val f = library.createFolder("F")
        library.moveFavorites(setOf(1), f)
        assertEquals(f, dao.favorite(1)!!.folderId)
        assertEquals(f, dao.downloaded(1)!!.folderId)
    }

    @Test
    fun movingDoesNotTouchOtherSideWhenSyncOff() = runBlocking {
        settings.syncFolders = false
        library.addFavorite(detail(1))
        library.addDownload(detail(1), "p")
        val f = library.createFolder("F")
        library.moveFavorites(setOf(1), f)
        assertEquals(f, dao.favorite(1)!!.folderId)
        assertNull(dao.downloaded(1)!!.folderId)
    }

    @Test
    fun newDownloadInheritsFavoriteFolderWhenSynced() = runBlocking {
        library.addFavorite(detail(1))
        val f = library.createFolder("F")
        library.moveFavorites(setOf(1), f)
        library.addDownload(detail(1), "p")
        assertEquals(f, dao.downloaded(1)!!.folderId)
    }

    @Test
    fun redownloadKeepsExistingFolderWhenSyncOff() = runBlocking {
        settings.syncFolders = false
        library.addDownload(detail(1), "p1")
        val f = library.createFolder("F")
        library.moveDownloads(setOf(1), f)
        library.addDownload(detail(1), "p2")
        assertEquals(f, dao.downloaded(1)!!.folderId)
        assertEquals("p2", dao.downloaded(1)!!.path)
    }

    @Test
    fun partialFavoriteDoesNotRecordCommentCount() = runBlocking {
        library.addFavorite(detail(1, comments = 0), partial = true)
        assertNull(dao.favorite(1)!!.commentCount)
        library.addFavorite(detail(2, comments = 3))
        assertEquals(3, dao.favorite(2)!!.commentCount)
    }

    @Test
    fun deletingFolderReturnsItemsToUncategorized() = runBlocking {
        library.addFavorite(detail(1))
        library.addDownload(detail(1), "p")
        val f = library.createFolder("F")
        library.moveFavorites(setOf(1), f)
        library.deleteFolder(f)
        assertNull(dao.favorite(1)!!.folderId)
        assertNull(dao.downloaded(1)!!.folderId)
    }

    @Test
    fun enablingSyncAlignsDownloadsToFavorites() = runBlocking {
        settings.syncFolders = false
        library.addFavorite(detail(1))
        library.addDownload(detail(1), "p")
        val f = library.createFolder("F")
        library.moveFavorites(setOf(1), f)
        assertNull(dao.downloaded(1)!!.folderId)
        library.syncDownloadsFromFavorites()
        assertEquals(f, dao.downloaded(1)!!.folderId)
    }

    @Test
    fun refreshClearsRemovedMarkAndFillsGender() = runBlocking {
        library.addFavorite(detail(1).copy(gender = ""))
        library.markRemoved(1)
        assertEquals(true, dao.favorite(1)!!.removed)
        library.refresh(detail(1))
        assertEquals(false, dao.favorite(1)!!.removed)
        assertEquals("female", dao.favorite(1)!!.gender)
    }
}
