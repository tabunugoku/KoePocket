package app.tabunugoku.koepocket.ui

import androidx.compose.ui.res.stringResource
import app.tabunugoku.koepocket.R
import androidx.annotation.StringRes
import android.content.Context
import java.io.IOException
import app.tabunugoku.koepocket.data.NotFoundException
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Male
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.flow.MutableSharedFlow
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.tabunugoku.koepocket.KoeKoeApp
import app.tabunugoku.koepocket.data.VoiceItem
import kotlinx.coroutines.launch

/**
 * 一覧の読み込み。[genders] (female / male / couple) を指定すると、取得したページをその性別だけに絞る。
 * 絞り込みで1ページ分が空になっても、見える項目が [MIN_NEW] 件たまるか、最後のページになるまで続けて読み込む
 * (1回の読み込みでの上限は [MAX_PAGES] ページ)。
 */
class ListViewModel(
    private val app: KoeKoeApp,
    private val path: String,
    private val genders: Set<String> = emptySet(),
) : ViewModel() {
    var items by mutableStateOf<List<VoiceItem>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var refreshing by mutableStateOf(false); private set
    /** 更新が成功するたびに増える。画面側が先頭へ戻すきっかけにする。 */
    var refreshCount by mutableIntStateOf(0); private set
    var error by mutableStateOf<String?>(null); private set
    private var page = 0
    private var hasNext = true

    init { load(reset = false) }

    fun loadMore() = load(reset = false)

    /** 1ページ目から取り直す。取得に失敗したときは、表示中の一覧をそのまま残す。 */
    fun refresh() = load(reset = true)

    private fun load(reset: Boolean) {
        if (loading || (!reset && !hasNext)) return
        loading = true
        refreshing = reset
        error = null
        viewModelScope.launch {
            try {
                var acc = if (reset) emptyList() else items
                var pageNo = if (reset) 0 else page
                var more = if (reset) true else hasNext
                var added = 0
                var fetched = 0
                while (more && added < MIN_NEW && fetched < MAX_PAGES) {
                    val res = app.api.list(path, pageNo + 1, fresh = reset && fetched == 0)
                    pageNo++
                    fetched++
                    more = res.hasNext && res.items.isNotEmpty()
                    val before = acc.size
                    acc = (acc + filterByGender(res.items, genders)).distinctBy { it.id }
                    added += acc.size - before
                    items = acc
                    page = pageNo
                    hasNext = more
                    if (reset && fetched == 1) refreshCount++
                }
            } catch (e: Exception) {
                error = errorMessage(app, e)
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    fun retry() = loadMore()
}

private const val MIN_NEW = 8
private const val MAX_PAGES = 6

/** 下部のタブを、選択中にもう一度タップしたことを各画面へ伝える。値はタブのルート。 */
object TabReselect {
    val events = MutableSharedFlow<String>(extraBufferCapacity = 1)
}

/** [route] のタブが再タップされたら、[state] の一覧を先頭へ戻す。[route] が null なら何もしない。 */
@Composable
fun ScrollToTopOnReselect(route: String?, state: LazyListState) {
    if (route == null) return
    LaunchedEffect(route, state) {
        TabReselect.events.collect { if (it == route) state.scrollToItem(0) }
    }
}

/** 読み込みの失敗を、利用者に見せる文言にする。 */
fun errorMessage(ctx: Context, e: Exception): String = when (e) {
    is NotFoundException -> ctx.getString(R.string.post_removed)
    is IOException -> ctx.getString(R.string.network_error)
    else -> ctx.getString(R.string.load_failed)
}

/** [genders] が空なら絞り込まない。 */
fun filterByGender(items: List<VoiceItem>, genders: Set<String>): List<VoiceItem> =
    if (genders.isEmpty()) items else items.filter { it.gender in genders }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceListScreen(
    app: KoeKoeApp,
    path: String,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
    genders: Set<String> = emptySet(),
    reselectRoute: String? = null,
) {
    val key = path + "|" + genders.sorted().joinToString(",")
    val vm: ListViewModel = viewModel(key = key, factory = viewModelFactory { initializer { ListViewModel(app, path, genders) } })
    val ctx = LocalContext.current
    val state = rememberLazyListState()
    ScrollToTopOnReselect(reselectRoute, state)
    val nearEnd by remember(vm, state) { derivedStateOf { state.layoutInfo.let { it.visibleItemsInfo.lastOrNull()?.index ?: 0 } >= vm.items.size - 3 } }
    LaunchedEffect(nearEnd, vm.items.size) { if (nearEnd && vm.items.isNotEmpty()) vm.loadMore() }

    LaunchedEffect(vm.refreshCount) { if (vm.refreshCount > 0) state.scrollToItem(0) }

    PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = vm::refresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
            items(vm.items, key = { it.id }) { v ->
                VoiceRow(
                    title = v.title,
                    subtitle = "${v.author}・${SiteText.duration(ctx, v.duration)}・♥${v.likes}・${stringResource(R.string.comments_short, v.comments)}・${SiteText.ago(ctx, v.postedAgo)}",
                    onClick = { onOpen(v.id) },
                    gender = v.gender,
                )
                HorizontalDivider()
            }
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    val err = vm.error
                    when {
                        vm.loading && !vm.refreshing -> CircularProgressIndicator()
                        err != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(err)
                            TextButton(onClick = vm::retry) { Text(stringResource(R.string.reload)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    gender: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { GenderBadge(gender) },
        trailingContent = trailing,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** 投稿の種別 (女性 / 男性 / カップル) を、色とアイコンで示す丸いバッジ。種別が分からないときは同じ大きさの空き。 */
@Composable
fun GenderBadge(gender: String?) {
    val style = genderStyle(gender)
    Box(
        Modifier.size(40.dp).background(style?.color?.copy(alpha = 0.18f) ?: Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (style != null) Icon(style.icon, stringResource(style.label), tint = style.color, modifier = Modifier.size(24.dp))
    }
}

private class GenderStyle(@StringRes val label: Int, val icon: ImageVector, val color: Color)

private fun genderStyle(gender: String?): GenderStyle? = when (gender) {
    "female" -> GenderStyle(R.string.cat_female, Icons.Filled.Female, Color(0xFFFF6FAE))
    "male" -> GenderStyle(R.string.cat_male, Icons.Filled.Male, Color(0xFF4DA3FF))
    "couple" -> GenderStyle(R.string.cat_couple, Icons.Filled.Group, Color(0xFFB98CFF))
    else -> null
}
