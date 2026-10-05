package com.example.koekoe.player

import androidx.media3.common.AudioAttributes
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.koekoe.KoeKoeApp

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as KoeKoeApp
        val http = OkHttpDataSource.Factory(app.api.client)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)))
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        // システムの通知・メディア操作カードは、セッション側のメタデータを表示する。
        // 偽装が有効なときはここで題名を差し替える (アプリ内は MediaController 経由で本当の題名を読む)。
        val shown = object : ForwardingPlayer(player) {
            override fun getMediaMetadata(): MediaMetadata {
                val s = app.settings
                if (!s.disguiseEnabled) return super.getMediaMetadata()
                return MediaMetadata.Builder()
                    .setTitle(s.disguiseTitle.ifBlank { s.disguiseAppName })
                    .build()
            }
        }
        session = MediaSession.Builder(this, shown).build()
        setMediaNotificationProvider(DisguisedNotificationProvider(this, app.settings))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
