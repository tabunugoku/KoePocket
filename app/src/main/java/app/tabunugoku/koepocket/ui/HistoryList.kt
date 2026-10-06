package app.tabunugoku.koepocket.ui

import androidx.compose.ui.res.stringResource
import app.tabunugoku.koepocket.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 検索・絞り込みの履歴。タップで再入力、× で個別削除、「すべて削除」で全消去。 */
@Composable
fun HistoryList(
    history: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (history.isEmpty()) return
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.history), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onClear) { Text(stringResource(R.string.clear_all)) }
        }
        history.forEach { q ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(q) }.padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(q, modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 12.dp), maxLines = 1)
                IconButton(onClick = { onRemove(q) }) { Icon(Icons.Filled.Close, stringResource(R.string.history_remove)) }
            }
        }
    }
}
