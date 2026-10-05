package com.example.koekoe.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.koekoe.KoeKoeApp
import com.example.koekoe.data.FileStore
import com.example.koekoe.setSecureScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(app: KoeKoeApp, onBack: () -> Unit, onLegal: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = app.settings
    var autoDownload by remember { mutableStateOf(settings.autoDownloadOnFavorite) }
    var syncFolders by remember { mutableStateOf(settings.syncFolders) }
    var treeUri by remember { mutableStateOf(settings.downloadTreeUri) }
    var disguise by remember { mutableStateOf(settings.disguiseEnabled) }
    var disguiseApp by remember { mutableStateOf(settings.disguiseAppName) }
    var disguiseTitle by remember { mutableStateOf(settings.disguiseTitle) }
    var secure by remember { mutableStateOf(settings.secureScreen) }
    var cacheCleared by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            // アプリを再起動しても読み書きできるよう、権限を永続化する
            ctx.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            if (treeUri != uri.toString()) releaseTree(ctx, treeUri)
            settings.downloadTreeUri = uri.toString()
            treeUri = uri.toString()
        }
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        BrandTopBar("設定", onBack = onBack)
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SettingSwitch(
                title = "お気に入りに入れたら自動でダウンロード",
                description = "お気に入りに追加した音声を、自動で保存済みに入れます。",
                checked = autoDownload,
            ) { autoDownload = it; settings.autoDownloadOnFavorite = it }
            HorizontalDivider()
            SettingSwitch(
                title = "お気に入りとフォルダ分けを同期",
                description = "お気に入りと保存済みで、同じ音声は同じフォルダに入ります。片方で移動すると、もう片方も移動します。",
                checked = syncFolders,
            ) {
                syncFolders = it
                settings.syncFolders = it
                if (it) scope.launch { app.library.syncDownloadsFromFavorites() }
            }
            HorizontalDivider()
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("ダウンロード先", style = MaterialTheme.typography.bodyLarge)
                Text(
                    FileStore.displayName(ctx, treeUri),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "変更後にダウンロードする音声から適用されます。すでに保存した音声は移動しません。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { picker.launch(null) }) { Text("フォルダを選ぶ") }
                    if (treeUri != null) OutlinedButton(onClick = { releaseTree(ctx, treeUri); settings.downloadTreeUri = null; treeUri = null }) {
                        Text("既定に戻す")
                    }
                }
            }
            HorizontalDivider()
            SettingSwitch(
                title = "通知の表示を偽装",
                description = "再生通知に出るアプリ名と音声タイトルを、下の文字に置き換えます。次に通知が更新されたときから反映されます。",
                checked = disguise,
            ) { disguise = it; settings.disguiseEnabled = it }
            if (disguise) {
                OutlinedTextField(
                    value = disguiseApp,
                    onValueChange = { disguiseApp = it; settings.disguiseAppName = it },
                    label = { Text("通知のアプリ名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                OutlinedTextField(
                    value = disguiseTitle,
                    onValueChange = { disguiseTitle = it; settings.disguiseTitle = it },
                    label = { Text("通知の音声タイトル") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            HorizontalDivider()
            SettingSwitch(
                title = "画面を隠す",
                description = "最近使ったアプリの一覧に画面を映さず、スクリーンショットや画面録画も止めます。",
                checked = secure,
            ) {
                secure = it
                settings.secureScreen = it
                ctx.findActivity()?.let { a -> setSecureScreen(a, it) }
            }
            HorizontalDivider()
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("キャッシュ", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "閲覧した一覧や詳細ページの一時データ (最大20MB) を端末に残しています。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { runCatching { app.api.client.cache?.evictAll() } }
                            cacheCleared = true
                        }
                    }) { Text("キャッシュを削除") }
                    if (cacheCleared) Text("削除しました", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp))
                }
            }
            HorizontalDivider()
            Text(
                "利用上の注意・プライバシー",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onLegal).padding(16.dp),
            )
        }
    }
}

@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** 保存先に選んでいたフォルダの永続的な権限を手放す (保存先を変えたときに権限がたまらないように)。 */
private fun releaseTree(ctx: Context, tree: String?) {
    if (tree == null) return
    runCatching {
        ctx.contentResolver.releasePersistableUriPermission(
            Uri.parse(tree), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }
}

private fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
