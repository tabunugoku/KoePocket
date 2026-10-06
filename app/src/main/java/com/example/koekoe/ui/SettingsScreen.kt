package com.example.koekoe.ui

import androidx.compose.ui.res.stringResource
import com.example.koekoe.R
import com.example.koekoe.data.AppLanguage
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
        BrandTopBar(stringResource(R.string.settings), onBack = onBack)
        Column(Modifier.verticalScroll(rememberScrollState())) {
            LanguageSetting()
            HorizontalDivider()
            SettingSwitch(
                title = stringResource(R.string.set_auto_dl_title),
                description = stringResource(R.string.set_auto_dl_desc),
                checked = autoDownload,
            ) { autoDownload = it; settings.autoDownloadOnFavorite = it }
            HorizontalDivider()
            SettingSwitch(
                title = stringResource(R.string.set_sync_title),
                description = stringResource(R.string.set_sync_desc),
                checked = syncFolders,
            ) {
                syncFolders = it
                settings.syncFolders = it
                if (it) scope.launch { app.library.syncDownloadsFromFavorites() }
            }
            HorizontalDivider()
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(stringResource(R.string.set_dl_dir), style = MaterialTheme.typography.bodyLarge)
                Text(
                    FileStore.displayName(ctx, treeUri),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    stringResource(R.string.set_dl_dir_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { picker.launch(null) }) { Text(stringResource(R.string.choose_folder)) }
                    if (treeUri != null) OutlinedButton(onClick = { releaseTree(ctx, treeUri); settings.downloadTreeUri = null; treeUri = null }) {
                        Text(stringResource(R.string.reset_default))
                    }
                }
            }
            HorizontalDivider()
            SettingSwitch(
                title = stringResource(R.string.set_disguise_title),
                description = stringResource(R.string.set_disguise_desc),
                checked = disguise,
            ) { disguise = it; settings.disguiseEnabled = it }
            if (disguise) {
                OutlinedTextField(
                    value = disguiseApp,
                    onValueChange = { disguiseApp = it; settings.disguiseAppName = it },
                    label = { Text(stringResource(R.string.disguise_app_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                OutlinedTextField(
                    value = disguiseTitle,
                    onValueChange = { disguiseTitle = it; settings.disguiseTitle = it },
                    label = { Text(stringResource(R.string.disguise_title_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            HorizontalDivider()
            SettingSwitch(
                title = stringResource(R.string.set_secure_title),
                description = stringResource(R.string.set_secure_desc),
                checked = secure,
            ) {
                secure = it
                settings.secureScreen = it
                ctx.findActivity()?.let { a -> setSecureScreen(a, it) }
            }
            HorizontalDivider()
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(stringResource(R.string.set_cache), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.set_cache_desc),
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
                    }) { Text(stringResource(R.string.clear_cache)) }
                    if (cacheCleared) Text(stringResource(R.string.cache_cleared), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp))
                }
            }
            HorizontalDivider()
            Text(
                stringResource(R.string.legal_link),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onLegal).padding(16.dp),
            )
        }
    }
}

/** 表示言語の選択。選ぶとすぐに画面が切り替わる (端末の言語設定は変えない)。 */
@Composable
private fun LanguageSetting() {
    var open by remember { mutableStateOf(false) }
    val current = AppLanguage.current()
    val systemLabel = stringResource(R.string.lang_system)
    val label = { l: AppLanguage -> l.nativeName ?: systemLabel }
    Column(Modifier.fillMaxWidth().clickable { open = true }.padding(16.dp)) {
        Text(stringResource(R.string.set_language), style = MaterialTheme.typography.bodyLarge)
        Text(label(current), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.set_language)) },
            text = {
                Column {
                    AppLanguage.entries.forEach { l ->
                        Row(
                            Modifier.fillMaxWidth().clickable { open = false; if (l != current) AppLanguage.apply(l) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = l == current, onClick = null)
                            Text(label(l), modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.close)) } },
        )
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
