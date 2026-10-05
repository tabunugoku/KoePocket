package com.example.koekoe.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.koekoe.KoeKoeApp
import com.example.koekoe.data.DownloadWorker
import com.example.koekoe.data.FileStore
import com.example.koekoe.data.NotFoundException
import com.example.koekoe.data.VoiceComment
import com.example.koekoe.data.VoiceDetail
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val ANONYMOUS = "名無し"
private const val DESCRIPTION_LINES = 6
private const val COMMENTS_PAGE = 10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    app: KoeKoeApp,
    id: Long,
    onBack: () -> Unit,
    onTag: (String) -> Unit,
    /** 一覧ページへ移る (タイトル, 一覧のパス)。投稿者・ジャンル用。 */
    onList: (String, String) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = app.db.dao()

    var detail by remember { mutableStateOf<VoiceDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var offline by remember { mutableStateOf(false) }
    var removedOnSite by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(id, reload) {
        error = null
        removedOnSite = false
        try {
            detail = app.api.detail(id)
            offline = false
            detail?.let { app.library.refresh(it) }
            if (detail == null) error = "音声が見つかりません"
        } catch (e: Exception) {
            val gone = e is NotFoundException
            if (gone) {
                // サイトから削除された。お気に入り・保存済みの項目は消さず、印だけ付ける
                removedOnSite = true
                app.library.markRemoved(id)
            }
            // 削除されていても、通信できなくても、ダウンロード済みならローカルのファイルで再生できるようにする
            val local = dao.downloaded(id)
            if (local != null) {
                detail = VoiceDetail(id, local.title, FileStore.playUri(local.path), local.duration.orEmpty(), emptyList(), emptyList(), author = local.author.orEmpty())
                offline = true
            } else {
                error = if (e is java.io.IOException) "通信できません。ネットワークを確認してください" else e.message ?: "読み込みに失敗しました"
            }
        }
    }

    val favIds by dao.favoriteIds().collectAsState(initial = emptyList())
    val downloads by dao.downloads().collectAsState(initial = emptyList())
    val downloaded = downloads.firstOrNull { it.id == id }
    val works by WorkManager.getInstance(ctx).getWorkInfosForUniqueWorkFlow(DownloadWorker.workName(id))
        .collectAsState(initial = emptyList())
    val downloading = works.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
    val pct = works.firstOrNull { it.state == WorkInfo.State.RUNNING }?.progress?.getInt(DownloadWorker.KEY_PCT, -1) ?: -1
    val player by app.player.state.collectAsState()
    val isCurrent = player.currentId == id

    var pos by remember { mutableLongStateOf(0) }
    var dur by remember { mutableLongStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(isCurrent) {
        while (isCurrent) {
            if (!dragging) { pos = app.player.positionMs(); dur = app.player.durationMs() }
            delay(500)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("音声") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "戻る") } },
                colors = brandBarColors(),
                actions = {
                    val d = detail
                    if (d != null) {
                        val fav = id in favIds
                        IconButton(onClick = {
                            scope.launch {
                                if (fav) {
                                    dao.removeFavorite(id)
                                } else {
                                    app.library.addFavorite(d)
                                    // 設定がONで、まだ保存していなければ自動でダウンロードする
                                    if (app.settings.autoDownloadOnFavorite && downloaded == null && !offline) {
                                        DownloadWorker.enqueue(ctx, id)
                                    }
                                }
                            }
                        }) {
                            Icon(if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "お気に入り")
                        }
                        when {
                            downloaded != null -> IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.DownloadDone, "ダウンロード済み(タップで削除)") }
                            downloading -> Row(verticalAlignment = Alignment.CenterVertically) {
                                if (pct >= 0) {
                                    Text("$pct%", style = MaterialTheme.typography.labelMedium)
                                    Spacer(Modifier.width(6.dp))
                                    CircularProgressIndicator(progress = { pct / 100f }, modifier = Modifier.size(22.dp), color = Accent, strokeWidth = 3.dp)
                                } else {
                                    CircularProgressIndicator(Modifier.size(22.dp), color = Accent, strokeWidth = 3.dp)
                                }
                                Spacer(Modifier.width(14.dp))
                            }
                            else -> IconButton(onClick = { DownloadWorker.enqueue(ctx, id) }) {
                                Icon(Icons.Filled.Download, "ダウンロード")
                            }
                        }
                    }
                },
            )
        },
        // 再生の操作は常に画面の一番下。本文やコメントが長くても位置が動かない
        bottomBar = {
            detail?.let { d ->
                PlayerBar(
                    playing = isCurrent && player.isPlaying,
                    pos = if (isCurrent) pos else 0,
                    dur = if (isCurrent) dur else 0,
                    fallbackDuration = d.duration,
                    onToggle = { app.player.toggle(id, d.title, downloaded?.let { FileStore.playUri(it.path) } ?: d.audioUrl) },
                    onSeekPreview = { dragging = true; pos = it },
                    onSeekDone = { app.player.seekTo(pos); dragging = false },
                )
            }
        },
    ) { pad ->
        if (confirmDelete && downloaded != null) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("保存した音声を削除") },
                text = { Text("端末に保存したファイルを削除します。よろしいですか？") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        scope.launch {
                            FileStore.delete(ctx, downloaded.path)
                            dao.removeDownload(id)
                        }
                    }) { Text("削除") }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("キャンセル") } },
            )
        }
        val d = detail
        if (d == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                if (error != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!, modifier = Modifier.padding(horizontal = 24.dp), textAlign = TextAlign.Center)
                    TextButton(onClick = { reload++ }) { Text("再読み込み") }
                } else CircularProgressIndicator()
            }
            return@Scaffold
        }
        var descExpanded by rememberSaveable { mutableStateOf(false) }
        var descOverflow by remember { mutableStateOf(false) }
        var commentsShown by rememberSaveable { mutableIntStateOf(COMMENTS_PAGE) }

        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(d.title, style = MaterialTheme.typography.titleLarge) }
            if (d.authorPath != null || d.genrePath != null) item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (d.authorPath != null && d.author.isNotEmpty()) AssistChip(
                        onClick = { onList("投稿者: ${d.author}", d.authorPath) },
                        leadingIcon = { Icon(Icons.Filled.Person, null, Modifier.size(18.dp)) },
                        label = { Text(d.author) },
                    )
                    if (d.genrePath != null && d.genre.isNotEmpty()) AssistChip(
                        onClick = { onList("ジャンル: ${d.genre}", d.genrePath) },
                        leadingIcon = { Icon(Icons.Filled.Mic, null, Modifier.size(18.dp)) },
                        label = { Text(d.genre) },
                    )
                }
            }
            if (offline) item {
                Text(
                    if (removedOnSite) "この投稿はKoe-Koeから削除されました。保存済みの音声のみ再生できます"
                    else "オフライン表示です(保存済みの音声のみ再生できます)",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (removedOnSite) MaterialTheme.colorScheme.tertiary else Color.Unspecified,
                )
            }
            if (d.description.isNotEmpty()) item {
                Column {
                    Text(
                        d.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (descExpanded) Int.MAX_VALUE else DESCRIPTION_LINES,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { if (!descExpanded) descOverflow = it.hasVisualOverflow },
                    )
                    if (descOverflow || descExpanded) {
                        TextButton(onClick = { descExpanded = !descExpanded }) { Text(if (descExpanded) "閉じる" else "続きを読む") }
                    }
                }
            }
            if (d.tags.isNotEmpty()) item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.tags.forEach { AssistChip(onClick = { onTag(it) }, label = { Text(it) }) }
                }
            }
            if (!offline) item {
                HorizontalDivider(Modifier.padding(top = 4.dp))
                Text("コメント (${d.comments.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            }
            // 投稿者本人のコメントは色を変えて見分けられるようにする (「名無し」は他人と区別できないので対象外)
            items(d.comments.take(commentsShown)) { c ->
                CommentCard(c, isOwner = d.author.isNotEmpty() && d.author != ANONYMOUS && c.author == d.author)
            }
            if (d.comments.size > commentsShown) item {
                OutlinedButton(onClick = { commentsShown += COMMENTS_PAGE }, modifier = Modifier.fillMaxWidth()) {
                    Text("もっと見る (残り${d.comments.size - commentsShown}件)")
                }
            }
        }
    }
}

@Composable
private fun CommentCard(c: VoiceComment, isOwner: Boolean) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isOwner) Accent.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = if (isOwner) BorderStroke(1.dp, Accent) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.background(Accent, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                ) { Text("${c.no}", style = MaterialTheme.typography.labelSmall, color = Navy, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(8.dp))
                // 名前 (+ 投稿者ラベル) が左、日付が右。名前が長いときだけ省略する
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(c.author, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (isOwner) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "投稿者",
                            style = MaterialTheme.typography.labelSmall,
                            color = Navy,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.background(Accent, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 1.dp),
                        )
                    }
                }
                if (c.postedAt.isNotEmpty()) {
                    Text(c.postedAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(c.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** 画面下の再生バー: 再生/一時停止・シークバー・経過/全体の時間。 */
@Composable
private fun PlayerBar(
    playing: Boolean,
    pos: Long,
    dur: Long,
    fallbackDuration: String,
    onToggle: () -> Unit,
    onSeekPreview: (Long) -> Unit,
    onSeekDone: () -> Unit,
) {
    Surface(color = Navy, contentColor = Color.White, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Accent, contentColor = Navy),
                onClick = onToggle,
            ) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "再生/一時停止") }
            Slider(
                value = pos.coerceIn(0, maxOf(dur, 1)).toFloat(),
                onValueChange = { onSeekPreview(it.toLong()) },
                onValueChangeFinished = onSeekDone,
                valueRange = 0f..maxOf(dur, 1).toFloat(),
                enabled = dur > 0,
                colors = seekColors(),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            TimeLabel(if (dur > 0) "${formatTime(pos)} / ${formatTime(dur)}" else fallbackDuration)
        }
    }
}

@Composable
fun seekColors() = SliderDefaults.colors(
    thumbColor = Accent,
    activeTrackColor = Accent,
    inactiveTrackColor = OnNavyMuted.copy(alpha = 0.3f),
    disabledThumbColor = OnNavyMuted,
    disabledActiveTrackColor = OnNavyMuted,
    disabledInactiveTrackColor = OnNavyMuted.copy(alpha = 0.3f),
)

/**
 * 再生時間の表示。幅を固定(等幅フォント・右寄せ)して、数字が変わってもシークバーの長さが変わらないようにする。
 * 幅は「100:00 / 100:00」(15文字)が入る大きさ。
 */
@Composable
fun TimeLabel(text: String, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.labelSmall
    val width = with(LocalDensity.current) { (style.fontSize * 0.62f * TIME_LABEL_CHARS).toDp() }
    Text(
        text,
        style = style,
        fontFamily = FontFamily.Monospace,
        textAlign = TextAlign.End,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.width(width),
    )
}

private const val TIME_LABEL_CHARS = 15

fun formatTime(ms: Long): String = "%d:%02d".format(ms / 60000, ms / 1000 % 60)
