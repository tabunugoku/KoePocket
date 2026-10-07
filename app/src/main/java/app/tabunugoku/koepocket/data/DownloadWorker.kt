package app.tabunugoku.koepocket.data

import app.tabunugoku.koepocket.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import app.tabunugoku.koepocket.KoeKoeApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import okhttp3.Request

class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as KoeKoeApp
        val id = inputData.getLong(KEY_ID, -1)
        // 長いダウンロードを OS に止められないよう、通知つき (フォアグラウンド) で実行する
        runCatching { setForeground(foregroundInfo(app, id)) }
        val detail = try {
            app.api.detail(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NotFoundException) {
            return Result.failure() // 削除された投稿は何度試しても取得できない
        } catch (e: Exception) {
            return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        } ?: return Result.failure()
        val target = try {
            FileStore.open(applicationContext, app.settings, id, detail.title)
        } catch (e: Exception) {
            return Result.failure()
        }
        try {
            withContext(Dispatchers.IO) {
                val call = app.api.audioClient.newCall(Request.Builder().url(detail.audioUrl).build())
                // 停止・キャンセルされたら、読み込み待ちの通信も止める
                val onStop = coroutineContext.job.invokeOnCompletion { if (it != null) call.cancel() }
                try {
                    call.execute().use { res ->
                        if (!res.isSuccessful) error("HTTP ${res.code}")
                        val body = res.body ?: error("empty body")
                        val total = body.contentLength()
                        var done = 0L
                        var lastPct = -1
                        val buf = ByteArray(32 * 1024)
                        body.byteStream().use { input ->
                            target.out.use { out ->
                                while (true) {
                                    ensureActive()
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                    done += n
                                    val pct = if (total > 0) (done * 100 / total).toInt() else -1
                                    if (pct != lastPct) {
                                        lastPct = pct
                                        setProgress(workDataOf(KEY_PCT to pct))
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    onStop.dispose()
                }
            }
        } catch (e: Exception) {
            runCatching { target.out.close() }
            target.discard()
            if (e is CancellationException) throw e
            return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
        // 保存の確定や記録で失敗したら、書き終えたファイルを残さず、通常の失敗と同じく再試行する
        val path = try {
            target.commit()
        } catch (e: Exception) {
            target.discard()
            return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
        try {
            // 保存先を変えて取り直したときに、前の場所のファイルが誰にも参照されないまま残らないようにする
            val oldPath = app.db.dao().downloaded(id)?.path
            app.library.addDownload(detail, path)
            if (oldPath != null && oldPath != path) FileStore.delete(applicationContext, oldPath)
        } catch (e: Exception) {
            FileStore.delete(applicationContext, path)
            if (e is CancellationException) throw e
            return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
        return Result.success()
    }

    private fun foregroundInfo(app: KoeKoeApp, id: Long): ForegroundInfo {
        val ctx = applicationContext
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, ctx.getString(R.string.notif_channel_download), NotificationManager.IMPORTANCE_LOW))
        // 通知の偽装が有効なら、再生通知と同じく表示を差し替える
        val s = app.settings
        val disguised = s.disguiseEnabled
        val title = if (disguised) s.disguiseTitle.ifBlank { s.disguiseAppName } else ctx.getString(R.string.notif_downloading)
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(0, 0, true)
            .build()
        if (disguised && s.disguiseAppName.isNotBlank()) n.extras.putString("android.substName", s.disguiseAppName)
        // 同時に動く別の投稿のダウンロードと通知 ID が重ならないよう、投稿 ID から決める
        val nid = (NOTIFICATION_ID + id).toInt()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(nid, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(nid, n)
        }
    }

    companion object {
        private const val CHANNEL_ID = "download"
        private const val NOTIFICATION_ID = 2000
        const val TAG = "download"
        private const val MAX_ATTEMPTS = 3
        private const val KEY_ID = "id"
        const val KEY_PCT = "pct"
        fun workName(id: Long) = "dl_$id"

        fun enqueue(ctx: Context, id: Long) {
            val req = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(KEY_ID to id))
                .addTag(TAG)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(workName(id), ExistingWorkPolicy.KEEP, req)
        }
    }
}
