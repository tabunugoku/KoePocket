package app.tabunugoku.koepocket

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** adb shell am broadcast -a app.tabunugoku.koepocket.DEBUG_SQL --es sql "UPDATE ..." で SQL を実行する (debug ビルドのみ)。 */
class DebugSqlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sql = intent.getStringExtra("sql") ?: return
        val app = context.applicationContext as KoeKoeApp
        runCatching {
            app.db.openHelper.writableDatabase.execSQL(sql)
            app.db.invalidationTracker.refreshVersionsAsync()
            Log.i("DebugSql", "ok: $sql")
        }.onFailure { Log.e("DebugSql", "failed: $sql", it) }
    }
}
