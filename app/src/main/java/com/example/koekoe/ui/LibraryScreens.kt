package com.example.koekoe.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.koekoe.KoeKoeApp
import com.example.koekoe.data.FileStore
import com.example.koekoe.data.Folder
import com.example.koekoe.data.SearchHistory
import kotlinx.coroutines.launch

private data class LibItem(
    val id: Long,
    val title: String,
    val subtitle: String,
    val addedAt: Long,
    val folderId: Long?,
    val gender: String?,
    /** Koe-Koe から削除されたことを確認済み。 */
    val removed: Boolean,
)

/** ホーム・検索の一覧に合わせて「投稿者・再生時間・コメント数」を並べる。 */
private fun subtitleOf(author: String?, duration: String?, comments: Int?, fallback: String = ""): String =
    listOfNotNull(
        author?.takeIf { it.isNotBlank() },
        duration?.takeIf { it.isNotBlank() },
        comments?.let { "コメ$it" },
    ).joinToString("・").ifEmpty { fallback }

private enum class LibSort(val label: String) {
    NEWEST("追加が新しい順"), OLDEST("追加が古い順"), TITLE("タイトル順")
}

/** フォルダ絞り込みの特別な値。フォルダの id は 1 以上。 */
private const val FOLDER_ALL = -1L
private const val FOLDER_NONE = 0L
private const val FOLDER_NEW = -2L

@Composable
fun FavoritesScreen(app: KoeKoeApp, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    val dao = app.db.dao()
    val scope = rememberCoroutineScope()
    val favs by dao.favorites().collectAsState(initial = emptyList())
    LibraryScreen(
        app = app,
        title = "お気に入り",
        items = favs.map {
            LibItem(it.id, it.title, subtitleOf(it.author, it.duration, it.commentCount), it.addedAt, it.folderId, it.gender, it.removed)
        },
        emptyText = "お気に入りはまだありません",
        deleteLabel = "お気に入りから外す",
        history = app.history,
        historyScope = "fav",
        onOpen = onOpen,
        onMove = { ids, folder -> scope.launch { app.library.moveFavorites(ids, folder) } },
        onDelete = { ids -> scope.launch { ids.forEach { dao.removeFavorite(it) } } },
        modifier = modifier,
    )
}

@Composable
fun DownloadsScreen(app: KoeKoeApp, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    val dao = app.db.dao()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val list by dao.downloads().collectAsState(initial = emptyList())
    LibraryScreen(
        app = app,
        title = "保存済み",
        items = list.map {
            LibItem(it.id, it.title, subtitleOf(it.author, it.duration, it.commentCount, "オフライン再生可"), it.addedAt, it.folderId, it.gender, it.removed)
        },
        emptyText = "ダウンロード済みの音声はありません",
        deleteLabel = "ファイルを削除",
        history = app.history,
        historyScope = "dl",
        onOpen = onOpen,
        onMove = { ids, folder -> scope.launch { app.library.moveDownloads(ids, folder) } },
        onDelete = { ids ->
            scope.launch {
                list.filter { it.id in ids }.forEach {
                    FileStore.delete(ctx, it.path)
                    dao.removeDownload(it.id)
                }
            }
        },
        modifier = modifier,
    )
}

/** お気に入り・保存済み共通の一覧。フォルダ分け / 絞り込み / 並べ替え / 複数選択して移動・削除ができる。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun LibraryScreen(
    app: KoeKoeApp,
    title: String,
    items: List<LibItem>,
    emptyText: String,
    deleteLabel: String,
    history: SearchHistory,
    historyScope: String,
    onOpen: (Long) -> Unit,
    onMove: (Set<Long>, Long?) -> Unit,
    onDelete: (Set<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val folders by app.db.dao().folders().collectAsState(initial = emptyList())
    var hist by remember { mutableStateOf(history.get(historyScope)) }
    val keyboard = LocalSoftwareKeyboardController.current

    var sort by rememberSaveable { mutableStateOf(LibSort.NEWEST) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var folderFilter by rememberSaveable { mutableLongStateOf(FOLDER_ALL) }
    var sortMenu by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var moveDialog by remember { mutableStateOf(false) }
    var filterSheet by remember { mutableStateOf(false) }
    var manageDialog by remember { mutableStateOf(false) }
    var newFolderDialog by remember { mutableStateOf<((Long) -> Unit)?>(null) }
    val selecting = selected.isNotEmpty()

    // 消えたフォルダを選んだままにしない
    LaunchedEffect(folders) {
        if (folderFilter > 0 && folders.none { it.id == folderFilter }) folderFilter = FOLDER_ALL
    }
    val folderNames = remember(folders) { folders.associate { it.id to it.name } }

    val shown = remember(items, sort, query, folderFilter) {
        val filtered = items.filter {
            (query.isBlank() || it.title.contains(query.trim(), ignoreCase = true)) &&
                when (folderFilter) {
                    FOLDER_ALL -> true
                    FOLDER_NONE -> it.folderId == null
                    else -> it.folderId == folderFilter
                }
        }
        when (sort) {
            LibSort.NEWEST -> filtered.sortedByDescending { it.addedAt }
            LibSort.OLDEST -> filtered.sortedBy { it.addedAt }
            LibSort.TITLE -> filtered.sortedBy { it.title }
        }
    }
    // 他の操作で消えた項目が選択に残らないようにする
    LaunchedEffect(items) { selected = selected.intersect(items.map { it.id }.toSet()) }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (selecting) "${selected.size}件を選択中" else "$title (${items.size})") },
            navigationIcon = {
                if (selecting) IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, "選択を解除") }
            },
            actions = {
                if (selecting) {
                    IconButton(onClick = { selected = shown.map { it.id }.toSet() }) { Icon(Icons.Filled.SelectAll, "すべて選択") }
                    IconButton(onClick = { moveDialog = true }) { Icon(Icons.Filled.FolderOpen, "フォルダへ移動") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, deleteLabel) }
                } else {
                    IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                        Icon(Icons.Filled.Search, "絞り込み")
                    }
                    IconButton(onClick = { manageDialog = true }) { Icon(Icons.Filled.CreateNewFolder, "フォルダを管理") }
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "並べ替え") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            LibSort.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.label) },
                                    leadingIcon = { if (s == sort) Icon(Icons.Filled.Check, null) },
                                    onClick = { sort = s; sortMenu = false },
                                )
                            }
                        }
                    }
                }
            },
            colors = brandBarColors(),
        )

        // フォルダが多くてもチップが並びきらないよう、1つのボタンから選択シート (検索つき) を開く
        if (folders.isNotEmpty() || items.any { it.folderId != null }) {
            val currentLabel = when (folderFilter) {
                FOLDER_ALL -> "すべて"
                FOLDER_NONE -> "未分類"
                else -> folderNames[folderFilter] ?: "すべて"
            }
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = folderFilter != FOLDER_ALL,
                    onClick = { filterSheet = true },
                    leadingIcon = { Icon(Icons.Filled.Folder, null, Modifier.size(18.dp)) },
                    label = { Text("フォルダ: $currentLabel") },
                    trailingIcon = {
                        if (folderFilter != FOLDER_ALL) {
                            Icon(Icons.Filled.Close, "フォルダの絞り込みを解除", Modifier.size(18.dp).clickable { folderFilter = FOLDER_ALL })
                        } else {
                            Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp))
                        }
                    },
                )
                Spacer(Modifier.width(8.dp))
                Text("${shown.size}件", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (searching && !selecting) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("タイトルで絞り込み") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (query.isNotBlank()) hist = history.add(historyScope, query)
                    keyboard?.hide()
                }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        if (searching && !selecting && query.isBlank() && hist.isNotEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                HistoryList(
                    history = hist,
                    onPick = { query = it },
                    onRemove = { hist = history.remove(historyScope, it) },
                    onClear = { hist = history.clear(historyScope) },
                )
            }
        } else if (shown.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(if (items.isEmpty()) emptyText else "該当する項目がありません")
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.id }) { item ->
                    val isSel = item.id in selected
                    val folderName = item.folderId?.let { folderNames[it] }
                    ListItem(
                        headlineContent = { Text(item.title, maxLines = 2) },
                        supportingContent = {
                            val base = if (folderName != null) "${item.subtitle}・$folderName" else item.subtitle
                            if (item.removed) {
                                Text("【削除済み】$base", color = MaterialTheme.colorScheme.tertiary)
                            } else {
                                Text(base)
                            }
                        },
                        leadingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (selecting) {
                                    Checkbox(checked = isSel, onCheckedChange = null)
                                    Spacer(Modifier.width(4.dp))
                                }
                                GenderBadge(item.gender)
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (isSel) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        ),
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                if (selecting) selected = if (isSel) selected - item.id else selected + item.id
                                else onOpen(item.id)
                            },
                            onLongClick = { selected = selected + item.id },
                        ),
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("${selected.size}件の${deleteLabel}") },
            text = { Text("選択した項目に対して実行します。よろしいですか？") },
            confirmButton = {
                TextButton(onClick = { onDelete(selected); selected = emptySet(); confirmDelete = false }) { Text("実行") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("キャンセル") } },
        )
    }

    if (filterSheet) {
        FolderSheet(
            title = "フォルダで絞り込み",
            entries = buildList {
                add(FolderEntry(FOLDER_ALL, "すべて", items.size, Icons.Filled.Folder, special = true))
                add(FolderEntry(FOLDER_NONE, "未分類", items.count { it.folderId == null }, Icons.Filled.FolderOff, special = true))
                folders.forEach { f -> add(FolderEntry(f.id, f.name, items.count { it.folderId == f.id }, Icons.Filled.Folder)) }
            },
            current = folderFilter,
            note = null,
            onPick = { folderFilter = it; filterSheet = false },
            onDismiss = { filterSheet = false },
        )
    }

    if (moveDialog) {
        FolderSheet(
            title = "${selected.size}件をフォルダへ移動",
            entries = buildList {
                add(FolderEntry(FOLDER_NEW, "新しいフォルダを作って移動", null, Icons.Filled.CreateNewFolder, special = true))
                add(FolderEntry(FOLDER_NONE, "未分類", null, Icons.Filled.FolderOff, special = true))
                folders.forEach { f -> add(FolderEntry(f.id, f.name, null, Icons.Filled.Folder)) }
            },
            current = null,
            note = if (app.settings.syncFolders) "お気に入りと保存済みのフォルダは同期されます" else null,
            onPick = { picked ->
                val ids = selected
                moveDialog = false
                if (picked == FOLDER_NEW) {
                    newFolderDialog = { newId -> onMove(ids, newId); selected = emptySet() }
                } else {
                    onMove(ids, picked.takeIf { it > 0 })
                    selected = emptySet()
                }
            },
            onDismiss = { moveDialog = false },
        )
    }

    if (manageDialog) {
        ManageFoldersDialog(
            folders = folders,
            onDismiss = { manageDialog = false },
            onCreate = { name -> scope.launch { app.library.createFolder(name) } },
            onRename = { id, name -> scope.launch { app.library.renameFolder(id, name) } },
            onDelete = { id -> scope.launch { app.library.deleteFolder(id) } },
        )
    }

    newFolderDialog?.let { done ->
        NameDialog(
            title = "新しいフォルダ",
            initial = "",
            onDismiss = { newFolderDialog = null },
            onOk = { name -> scope.launch { done(app.library.createFolder(name)) }; newFolderDialog = null },
        )
    }
}

/** [id] は FOLDER_ALL / FOLDER_NONE / FOLDER_NEW / フォルダの id。[special] は検索中も常に先頭に出す項目。 */
private class FolderEntry(
    val id: Long,
    val label: String,
    val count: Int?,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val special: Boolean = false,
)

/**
 * フォルダの選択シート。フォルダが多くても選べるよう、名前で検索できる。
 * (絞り込みと、項目の移動の両方で使う)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderSheet(
    title: String,
    entries: List<FolderEntry>,
    current: Long?,
    note: String?,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val shown = remember(entries, q) {
        entries.filter { it.special || q.isEmpty() || it.label.contains(q, ignoreCase = true) }
    }
    val folderCount = entries.count { !it.special }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxHeight(0.8f).padding(horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (note != null) Text(note, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
            if (folderCount >= SEARCH_FOLDERS_FROM) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("フォルダ名で検索 (${folderCount}件)") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            }
            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.id }) { e ->
                    ListItem(
                        headlineContent = { Text(e.label) },
                        leadingContent = { Icon(e.icon, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                e.count?.let { Text("$it", style = MaterialTheme.typography.labelMedium) }
                                if (e.id == current) Icon(Icons.Filled.Check, "選択中", Modifier.padding(start = 8.dp))
                            }
                        },
                        modifier = Modifier.clickable { onPick(e.id) },
                    )
                }
                if (shown.none { !it.special } && q.isNotEmpty()) item {
                    Text("該当するフォルダがありません", Modifier.padding(16.dp))
                }
            }
        }
    }
}

/** フォルダがこの数以上になったら、選択シートに検索欄を出す。 */
private const val SEARCH_FOLDERS_FROM = 6

@Composable
private fun MoveRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(label)
    }
}

@Composable
private fun ManageFoldersDialog(
    folders: List<Folder>,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Folder?>(null) }
    var deleting by remember { mutableStateOf<Folder?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("フォルダの管理") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (folders.isEmpty()) Text("フォルダはまだありません", modifier = Modifier.padding(vertical = 8.dp))
                folders.forEach { f ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary)
                        Text(f.name, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1)
                        IconButton(onClick = { renaming = f }) { Icon(Icons.Filled.Edit, "名前を変更") }
                        IconButton(onClick = { deleting = f }) { Icon(Icons.Filled.Delete, "フォルダを削除") }
                    }
                }
                MoveRow("新しいフォルダ", Icons.Filled.CreateNewFolder) { creating = true }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
    if (creating) NameDialog("新しいフォルダ", "", { creating = false }) { onCreate(it); creating = false }
    renaming?.let { f -> NameDialog("名前を変更", f.name, { renaming = null }) { onRename(f.id, it); renaming = null } }
    deleting?.let { f ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("「${f.name}」を削除") },
            text = { Text("中の項目は削除されず、未分類に戻ります。") },
            confirmButton = { TextButton(onClick = { onDelete(f.id); deleting = null }) { Text("削除") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("キャンセル") } },
        )
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 30) name = it },
                singleLine = true,
                placeholder = { Text("フォルダ名") },
            )
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onOk(name) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}
