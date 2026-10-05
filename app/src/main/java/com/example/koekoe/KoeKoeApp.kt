package com.example.koekoe

import android.app.Application
import androidx.room.Room
import com.example.koekoe.data.AppDb
import com.example.koekoe.data.AppSettings
import com.example.koekoe.data.FileStore
import com.example.koekoe.data.KoeKoeApi
import com.example.koekoe.data.Library
import com.example.koekoe.data.SearchHistory
import com.example.koekoe.player.PlayerConnection

class KoeKoeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 初回起動時に、既定の保存先 (Music/KoePocket) を作っておく
        if (!settings.defaultDirPrepared) {
            FileStore.ensureDefaultDir(this)
            settings.defaultDirPrepared = true
        }
    }

    val api by lazy { KoeKoeApi(cacheDir) }
    val db by lazy {
        Room.databaseBuilder(this, AppDb::class.java, "koekoe.db")
            .addMigrations(AppDb.MIGRATION_1_2, AppDb.MIGRATION_2_3, AppDb.MIGRATION_3_4)
            .build()
    }
    val player by lazy { PlayerConnection(this) }
    val history by lazy { SearchHistory(this) }
    val settings by lazy { AppSettings(this) }
    val library by lazy { Library(db, settings) }
}
