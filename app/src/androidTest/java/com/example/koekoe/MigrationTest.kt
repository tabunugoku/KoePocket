package com.example.koekoe

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.koekoe.data.AppDb
import com.example.koekoe.data.Folder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 初版 (DB version 1) のデータベースを手で作り、1→2→3→4 のマイグレーションを通して、
 * データが残ることと、Room が期待するスキーマと一致することを確かめる。
 * (スキーマの JSON は version 4 からしかないので、version 1 は SQL で再現している)
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    @After
    fun tearDown() {
        ctx.deleteDatabase(name)
    }

    private fun createVersion1() {
        ctx.deleteDatabase(name)
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `Favorite` (`id` INTEGER NOT NULL, `title` TEXT NOT NULL, `duration` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `Downloaded` (`id` INTEGER NOT NULL, `title` TEXT NOT NULL, `path` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("INSERT INTO Favorite VALUES (1, '題名A', '3分', 100)")
                db.execSQL("INSERT INTO Downloaded VALUES (2, '題名B', '/path/b.mp3', 200)")
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx).name(name).callback(callback).build(),
        )
        helper.writableDatabase.close()
        helper.close()
    }

    @Test
    fun migratesFromVersion1ToLatest() = runBlocking {
        createVersion1()
        val db = Room.databaseBuilder(ctx, AppDb::class.java, name)
            .addMigrations(AppDb.MIGRATION_1_2, AppDb.MIGRATION_2_3, AppDb.MIGRATION_3_4)
            .build()
        try {
            val dao = db.dao()
            val fav = dao.favorite(1)!!
            assertEquals("題名A", fav.title)
            assertEquals("3分", fav.duration)
            assertNull(fav.folderId)
            assertNull(fav.gender)
            assertNull(fav.author)
            assertFalse(fav.removed)
            val dl = dao.downloaded(2)!!
            assertEquals("/path/b.mp3", dl.path)
            assertNull(dl.duration)
            assertFalse(dl.removed)
            // 新しい列・テーブルが使える
            val folderId = dao.insertFolder(Folder(name = "f", createdAt = 1))
            dao.setFavoriteFolder(setOf(1L), folderId)
            assertEquals(folderId, dao.favorite(1)!!.folderId)
        } finally {
            db.close()
        }
    }
}
