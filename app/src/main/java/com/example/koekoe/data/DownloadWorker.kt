package com.example.koekoe.data

import android.content.Context
import androidx.work.*
import com.example.koekoe.KoeKoeApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as KoeKoeApp
        val id = inputData.getLong(KEY_ID, -1)
        val detail = try {
            app.api.detail(id)
        } catch (e: Exception) {
            return Result.retry()
        } ?: return Result.failure()
        val target = try {
            FileStore.open(applicationContext, app.settings, id, detail.title)
        } catch (e: Exception) {
            return Result.failure()
        }
        try {
            withContext(Dispatchers.IO) {
                app.api.client.newCall(Request.Builder().url(detail.audioUrl).build()).execute().use { res ->
                    if (!res.isSuccessful) error("HTTP ${res.code}")
                    val body = res.body!!
                    val total = body.contentLength()
                    var done = 0L
                    var lastPct = -1
                    val buf = ByteArray(32 * 1024)
                    body.byteStream().use { input ->
                        target.out.use { out ->
                            while (true) {
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
            }
        } catch (e: Exception) {
            target.discard()
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
        target.commit()
        app.library.addDownload(detail, target.pathOrUri)
        return Result.success()
    }

    companion object {
        private const val KEY_ID = "id"
        const val KEY_PCT = "pct"
        fun workName(id: Long) = "dl_$id"

        fun enqueue(ctx: Context, id: Long) {
            val req = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(KEY_ID to id))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(workName(id), ExistingWorkPolicy.KEEP, req)
        }
    }
}
