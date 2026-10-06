package app.tabunugoku.koepocket.player

import android.content.Context
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import app.tabunugoku.koepocket.data.AppSettings

/**
 * 再生通知を標準の作りのまま生成し、設定が有効なら表示上のアプリ名と音声タイトルだけを差し替える。
 * 再生状態やアプリ内の表示 (メタデータ) は変えない。
 */
@UnstableApi
class DisguisedNotificationProvider(
    ctx: Context,
    private val settings: AppSettings,
) : MediaNotification.Provider {
    private val inner = DefaultMediaNotificationProvider(ctx)

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val n = inner.createNotification(mediaSession, customLayout, actionFactory, onNotificationChangedCallback)
        if (settings.disguiseEnabled) {
            val extras = n.notification.extras ?: Bundle().also { n.notification.extras = it }
            val title = settings.disguiseTitle
            // 空欄のときは何も差し替えない (標準の表示のまま)
            extras.putCharSequence(NotificationCompat.EXTRA_TITLE, title.ifBlank { settings.disguiseAppName })
            extras.remove(NotificationCompat.EXTRA_TEXT)
            extras.remove(NotificationCompat.EXTRA_SUB_TEXT)
            if (settings.disguiseAppName.isNotBlank()) extras.putString(EXTRA_SUBSTITUTE_APP_NAME, settings.disguiseAppName)
        }
        return n
    }

    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle) =
        inner.handleCustomCommand(session, action, extras)

    private companion object {
        /** Notification.EXTRA_SUBSTITUTE_APP_NAME (API 28)。SUBSTITUTE_NOTIFICATION_APP_NAME 権限が必要。 */
        const val EXTRA_SUBSTITUTE_APP_NAME = "android.substName"
    }
}
