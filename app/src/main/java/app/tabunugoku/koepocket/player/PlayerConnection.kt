package app.tabunugoku.koepocket.player

import android.content.ComponentName
import android.content.Context
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import app.tabunugoku.koepocket.R

data class PlayerState(val currentId: Long? = null, val title: String = "", val isPlaying: Boolean = false)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerConnection(private val ctx: Context) {
    private var controller: MediaController? = null
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    private var connecting = false
    /** 接続が切れていたときに、つなぎ直したあとで実行する操作。 */
    private var pending: (() -> Unit)? = null

    init { connect() }

    /** 再生サービスにつなぐ。失敗やサービスの終了で切れても、次の再生操作でつなぎ直す。 */
    private fun connect() {
        if (connecting) return
        connecting = true
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    if (this@PlayerConnection.controller === controller) this@PlayerConnection.controller = null
                    _state.value = PlayerState()
                    controller.release()
                }
            })
            .buildAsync()
        future.addListener({
            connecting = false
            // 接続に失敗してもアプリは落とさない (次の再生操作でもう一度つなぐ)
            val c = runCatching { future.get() }.getOrNull()
            if (c == null) { pending = null; return@addListener }
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = sync(player)
                override fun onPlayerError(error: PlaybackException) {
                    Toast.makeText(ctx, R.string.playback_error, Toast.LENGTH_SHORT).show()
                }
            })
            sync(c)
            pending?.also { pending = null }?.invoke()
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun sync(p: Player) {
        val item = p.currentMediaItem
        _state.value = PlayerState(
            item?.mediaId?.toLongOrNull(),
            item?.mediaMetadata?.title?.toString().orEmpty(),
            p.isPlaying,
        )
    }

    /** 同じ音声なら再生/一時停止を切り替え、違う音声なら読み込んで再生する。 */
    fun toggle(id: Long, title: String, uri: String) {
        val c = controller ?: run {
            pending = { toggle(id, title, uri) }
            connect()
            return
        }
        if (c.currentMediaItem?.mediaId == id.toString()) {
            // エラーで止まっているときは、読み込み直してから再生する
            if (c.playerError != null) c.prepare()
            if (c.isPlaying) c.pause() else c.play()
            return
        }
        c.setMediaItem(
            MediaItem.Builder()
                .setMediaId(id.toString())
                .setUri(uri.toUri())
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                .build()
        )
        c.prepare()
        c.play()
    }

    fun pause() { controller?.pause() }
    fun resume() { controller?.play() }
    fun seekTo(ms: Long) { controller?.seekTo(ms) }
    fun positionMs(): Long = controller?.currentPosition ?: 0
    fun durationMs(): Long = controller?.duration?.takeIf { it > 0 } ?: 0
}
