package com.example.koekoe.ui

import androidx.compose.ui.res.stringResource
import com.example.koekoe.R
import androidx.annotation.StringRes
import android.content.Context
import java.io.IOException
import com.example.koekoe.data.NotFoundException
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
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
import com.example.koekoe.KoeKoeApp
import com.example.koekoe.data.VoiceItem
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
    var error by mutableStateOf<String?>(null); private set
    private var page = 0
    private var hasNext = true

    init { loadMore() }

    fun loadMore() {
        if (loading || !hasNext) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                var added = 0
                var fetched = 0
                while (hasNext && added < MIN_NEW && fetched < MAX_PAGES) {
                    val res = app.api.list(path, page + 1)
                    page++
                    fetched++
                    hasNext = res.hasNext && res.items.isNotEmpty()
                    val visible = filterByGender(res.items, genders)
                    val before = items.size
                    items = (items + visible).distinctBy { it.id }
                    added += items.size - before
                }
            } catch (e: Exception) {
                error = errorMessage(app, e)
            } finally {
                loading = false
            }
        }
    }

    fun retry() = loadMore()
}

private const val MIN_NEW = 8
private const val MAX_PAGES = 6

/** 読み込みの失敗を、利用者に見せる文言にする。 */
fun errorMessage(ctx: Context, e: Exception): String = when (e) {
    is NotFoundException -> ctx.getString(R.string.post_removed)
    is IOException -> ctx.getString(R.string.network_error)
    else -> ctx.getString(R.string.load_failed)
}

/** [genders] が空なら絞り込まない。 */
fun filterByGender(items: List<VoiceItem>, genders: Set<String>): List<VoiceItem> =
    if (genders.isEmpty()) items else items.filter { it.gender in genders }

@Composable
fun VoiceListScreen(
    app: KoeKoeApp,
    path: String,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
    genders: Set<String> = emptySet(),
) {
    val key = path + "|" + genders.sorted().joinToString(",")
    val vm: ListViewModel = viewModel(key = key, factory = viewModelFactory { initializer { ListViewModel(app, path, genders) } })
    val ctx = LocalContext.current
    val state = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { state.layoutInfo.let { it.visibleItemsInfo.lastOrNull()?.index ?: 0 } >= vm.items.size - 3 } }
    LaunchedEffect(nearEnd, vm.items.size) { if (nearEnd && vm.items.isNotEmpty()) vm.loadMore() }

    LazyColumn(state = state, modifier = modifier.fillMaxSize()) {
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
                when {
                    vm.loading -> CircularProgressIndicator()
                    vm.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(vm.error!!)
                        TextButton(onClick = vm::retry) { Text(stringResource(R.string.reload)) }
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
