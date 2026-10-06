package app.tabunugoku.koepocket

import android.app.Application
import androidx.room.Room
import androidx.work.WorkManager
import app.tabunugoku.koepocket.data.DownloadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import app.tabunugoku.koepocket.data.AppDb
import app.tabunugoku.koepocket.data.AppSettings
import app.tabunugoku.koepocket.data.FileStore
import app.tabunugoku.koepocket.data.KoeKoeApi
import app.tabunugoku.koepocket.data.Library
import app.tabunugoku.koepocket.data.SearchHistory
import app.tabunugoku.koepocket.player.PlayerConnection

class KoeKoeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // ディスク操作は主スレッドを避けて行う
        CoroutineScope(Dispatchers.IO).launch {
            // 初回起動時に、既定の保存先 (Music/KoePocket) を作っておく
            if (!settings.defaultDirPrepared) {
                FileStore.ensureDefaultDir(this@KoeKoeApp)
                settings.defaultDirPrepared = true
            }
            // ダウンロードが動いていないときだけ、中断で残った書きかけのファイルを消す
            val busy = WorkManager.getInstance(this@KoeKoeApp).getWorkInfosByTag(DownloadWorker.TAG).get().any { !it.state.isFinished }
            if (!busy) FileStore.cleanupOrphans(this@KoeKoeApp, settings)
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
