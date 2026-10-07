package app.tabunugoku.koepocket

import androidx.compose.ui.res.stringResource
import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalContext
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import app.tabunugoku.koepocket.data.AppSettings
import app.tabunugoku.koepocket.data.Category
import app.tabunugoku.koepocket.data.Genre
import app.tabunugoku.koepocket.data.KoeKoeApi
import app.tabunugoku.koepocket.ui.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val navy = Navy.toArgb()
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(navy), navigationBarStyle = SystemBarStyle.dark(navy))
        // 3 ボタン操作のとき、システムが下の帯に白い半透明の膜を重ねないようにする (下から出るシートの表示中に白く見える)
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        val app = application as KoeKoeApp
        setSecureScreen(this, app.settings.secureScreen)
        app.player // 再生サービスへの接続を先に開始する
        setContent {
            KoePocketTheme {
                Box(Modifier.fillMaxSize()) {
                    var accepted by remember { mutableStateOf(app.settings.acceptedLegalVersion >= AppSettings.LEGAL_VERSION) }
                    if (!accepted) {
                        ConsentGate(
                            onAccept = { app.settings.acceptedLegalVersion = AppSettings.LEGAL_VERSION; accepted = true },
                            onDecline = { finishAndRemoveTask() },
                        )
                    } else {
                        if (Build.VERSION.SDK_INT >= 33) {
                            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                            LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        }
                        AppRoot(app)
                    }
                    // ナビゲーションバー (ホーム・戻るボタン) の背面を濃紺にする。タブのない画面でも、白地に白いアイコンにならないように
                    Spacer(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .windowInsetsBottomHeight(WindowInsets.navigationBars).background(Navy),
                    )
                }
            }
        }
    }
}

private enum class NavTab(val route: String, @StringRes val label: Int) {
    HOME(TabRoutes.HOME, R.string.tab_home), SEARCH(TabRoutes.SEARCH, R.string.tab_search),
    FAVORITES(TabRoutes.FAVORITES, R.string.tab_favorites), DOWNLOADS(TabRoutes.DOWNLOADS, R.string.tab_downloads)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(app: KoeKoeApp) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    val player by app.player.state.collectAsState()
    val openDetail = { id: Long -> nav.navigate("detail/$id") }
    // タイトルとパスは、記号を含むので Base64(URL セーフ) にしてルートに載せる
    val openList = { title: String, path: String -> nav.navigate("list/${b64(title)}/${b64(path)}") }
    val ctx = LocalContext.current
    val openTag = { tag: String -> openList(ctx.getString(R.string.tag_prefix, tag), KoeKoeApi.tagPath(tag)) }

    val showTabs = NavTab.entries.any { t -> dest?.hierarchy?.any { it.route == t.route } == true }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0), // 各画面のバーが自分でステータスバー分を確保する
        bottomBar = {
            Column {
                val currentId = player.currentId
                if (currentId != null && dest?.route?.startsWith("detail/") != true) {
                    Surface(
                        color = Navy,
                        contentColor = Color.White,
                        modifier = Modifier.fillMaxWidth().clickable { openDetail(currentId) },
                    ) {
                        Column(Modifier.then(if (showTabs) Modifier else Modifier.navigationBarsPadding())) {
                            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(player.title, maxLines = 1, modifier = Modifier.weight(1f))
                                IconButton(onClick = { if (player.isPlaying) app.player.pause() else app.player.resume() }) {
                                    Icon(if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, stringResource(R.string.play_pause), tint = Accent)
                                }
                            }
                            MiniSeekBar(app, currentId, player.isPlaying)
                        }
                    }
                }
                if (showTabs) {
                    NavigationBar(containerColor = Navy, contentColor = Color.White) {
                        NavTab.entries.forEach { t ->
                            val isCurrent = dest?.hierarchy?.any { it.route == t.route } == true
                            NavigationBarItem(
                                selected = isCurrent,
                                onClick = {
                                    // 選択中のタブをもう一度押したら、一覧を先頭へ戻す
                                    if (isCurrent) TabReselect.events.tryEmit(t.route)
                                    else nav.navigate(t.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        when (t) { NavTab.HOME -> Icons.Filled.Home; NavTab.SEARCH -> Icons.Filled.Search; NavTab.FAVORITES -> Icons.Filled.Favorite; NavTab.DOWNLOADS -> Icons.Filled.Download },
                                        stringResource(t.label),
                                    )
                                },
                                label = { Text(stringResource(t.label)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Navy,
                                    selectedTextColor = Accent,
                                    indicatorColor = Accent,
                                    unselectedIconColor = OnNavyMuted,
                                    unselectedTextColor = OnNavyMuted,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = NavTab.HOME.route, modifier = Modifier.padding(pad)) {
            composable(NavTab.HOME.route) { HomeScreen(app, openDetail, onSettings = { nav.navigate("settings") }) }
            composable("settings") {
                SettingsScreen(app, onBack = { nav.popBackStack() }, onLegal = { nav.navigate("legal") })
            }
            composable("legal") { LegalScreen(onBack = { nav.popBackStack() }) }
            composable(NavTab.SEARCH.route) { SearchScreen(app, openDetail) }
            composable(NavTab.FAVORITES.route) { FavoritesScreen(app, openDetail) }
            composable(NavTab.DOWNLOADS.route) { DownloadsScreen(app, openDetail) }
            composable("detail/{id}") { e ->
                val id = e.arguments?.getString("id")?.toLongOrNull()
                if (id == null) {
                    LaunchedEffect(Unit) { nav.popBackStack() }
                    return@composable
                }
                DetailScreen(
                    app, id,
                    onBack = { nav.popBackStack() }, onTag = openTag, onList = openList,
                )
            }
            composable("list/{title}/{path}") { e ->
                val title = e.arguments?.getString("title")?.let(::unb64)
                val path = e.arguments?.getString("path")?.let(::unb64)
                if (title == null || path == null) {
                    LaunchedEffect(Unit) { nav.popBackStack() }
                    return@composable
                }
                Column(Modifier.navigationBarsPadding()) {
                    BrandTopBar(title, onBack = { nav.popBackStack() })
                    VoiceListScreen(app, path, openDetail, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(app: KoeKoeApp, onOpen: (Long) -> Unit, onSettings: () -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var genre by rememberSaveable { mutableStateOf(Genre.ALL) }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    var showTagPicker by remember { mutableStateOf(false) }
    Column {
        BrandTopBar("KoePocket", actions = {
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) }
        })
        TabRow(selectedTabIndex = selected, containerColor = Navy, contentColor = Accent) {
            Category.entries.forEachIndexed { i, c ->
                Tab(
                    selected = i == selected,
                    onClick = { selected = i },
                    text = { Text(stringResource(c.labelRes)) },
                    selectedContentColor = Accent,
                    unselectedContentColor = OnNavyMuted,
                )
            }
        }
        val category = Category.entries[selected]
        // ジャンル(カテゴリ内の分類)とタグは排他。タグ選択中はジャンルの選択表示を外す
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(Genre.entries) { g ->
                FilterChip(selected = tag == null && g == genre, onClick = { genre = g; tag = null }, label = { Text(stringResource(g.labelRes)) })
            }
        }
        // タグは数が多いので、チップを並べず選択画面(検索つき)から選ぶ
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = tag != null,
                onClick = { showTagPicker = true },
                leadingIcon = { Icon(Icons.Filled.Sell, null, Modifier.size(18.dp)) },
                label = { Text(tag?.let { stringResource(R.string.tag_prefix, it) } ?: stringResource(R.string.tag_filter)) },
                trailingIcon = if (tag != null) {
                    { Icon(Icons.Filled.Close, stringResource(R.string.tag_clear), Modifier.size(18.dp).clickable { tag = null }) }
                } else null,
            )
        }
        if (showTagPicker) {
            TagPickerSheet(
                app = app,
                current = tag,
                onPick = { tag = it; showTagPicker = false },
                onDismiss = { showTagPicker = false },
            )
        }
        VoiceListScreen(app, category.path(genre, tag), onOpen, Modifier.weight(1f), reselectRoute = TabRoutes.HOME)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagPickerSheet(app: KoeKoeApp, current: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val q = query.trim()

    // タグはサイト (all_tag.php) から人気順に取得する。1ページ60件・約360ページあるので、
    // スクロールの終わりに達したときに次のページを読み込む。通信できないときは前回の1ページ目を使う
    var tags by remember { mutableStateOf(app.settings.cachedTags) }
    var nextPage by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val loadMore: () -> Unit = {
        if (!loading && hasMore) {
            loading = true
            scope.launch {
                try {
                    val page = app.api.tagsPage(nextPage)
                    if (page.isEmpty()) {
                        hasMore = false
                    } else {
                        tags = if (nextPage == 1) page else (tags + page).distinct()
                        if (nextPage == 1) app.settings.cachedTags = page
                        nextPage++
                    }
                    failed = false
                } catch (e: Exception) {
                    failed = true
                    hasMore = false
                } finally {
                    loading = false
                }
            }
        }
    }
    LaunchedEffect(Unit) { loadMore() }

    val matches = remember(tags, q) { tags.filter { q.isEmpty() || it.contains(q, ignoreCase = true) } }
    // 入力で絞っているのに候補が少ないときは、続きのページも自動で探す (入力のたびに最大10ページまで)
    var autoPages by remember(q) { mutableIntStateOf(0) }
    LaunchedEffect(q, tags.size, loading, hasMore) {
        if (q.isNotEmpty() && matches.size < 10 && hasMore && !loading && autoPages < 10) {
            autoPages++
            loadMore()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetSystemBars()
        Column(Modifier.fillMaxHeight(0.8f).padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.tag_filter), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.tag_search_hint)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            LazyColumn(Modifier.weight(1f), contentPadding = WindowInsets.navigationBars.asPaddingValues()) {
                // 一覧にないタグも、入力してそのまま使える
                if (q.isNotEmpty() && q !in tags) item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.tag_use, q)) },
                        leadingContent = { Icon(Icons.Filled.Search, null) },
                        modifier = Modifier.clickable { onPick(q) },
                    )
                }
                items(matches) { t ->
                    ListItem(
                        headlineContent = { Text(t) },
                        trailingContent = { if (t == current) Icon(Icons.Filled.Check, stringResource(R.string.selected_mark)) },
                        modifier = Modifier.clickable { onPick(t) },
                    )
                }
                item {
                    // 末尾に来たら次のページを読み込む
                    when {
                        hasMore && q.isEmpty() -> {
                            LaunchedEffect(tags.size) { loadMore() }
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(24.dp))
                            }
                        }
                        failed && tags.isEmpty() -> Text(stringResource(R.string.tag_load_failed), Modifier.padding(16.dp))
                        failed -> TextButton(onClick = { hasMore = true; loadMore() }) { Text(stringResource(R.string.tag_more_failed)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniSeekBar(app: KoeKoeApp, currentId: Long?, isPlaying: Boolean) {
    var pos by remember { mutableLongStateOf(0) }
    var dur by remember { mutableLongStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(currentId, isPlaying) {
        // 再生中だけ 500ms ごとに読む。一時停止中は、長さがまだ分からないときだけ少し待って読み直す
        var waits = 0
        do {
            if (!dragging) { pos = app.player.positionMs(); dur = app.player.durationMs() }
            val again = isPlaying || (dur == 0L && waits++ < 20)
            if (again) delay(500)
        } while (again)
    }
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = pos.coerceIn(0, maxOf(dur, 1)).toFloat(),
            onValueChange = { dragging = true; pos = it.toLong() },
            onValueChangeFinished = { app.player.seekTo(pos); dragging = false },
            valueRange = 0f..maxOf(dur, 1).toFloat(),
            enabled = dur > 0,
            colors = seekColors(),
            modifier = Modifier.weight(1f).height(24.dp),
        )
        TimeLabel("${formatTime(pos)} / ${formatTime(dur)}", Modifier.padding(start = 8.dp))
    }
}

private fun b64(s: String): String =
    android.util.Base64.encodeToString(s.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)

private fun unb64(s: String): String =
    String(android.util.Base64.decode(s, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING))

/** 最近使ったアプリの一覧やスクリーンショットに画面を映さない (FLAG_SECURE) かどうかを切り替える。 */
fun setSecureScreen(activity: android.app.Activity, enabled: Boolean) {
    if (enabled) activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    else activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
}
