package com.example.koekoe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.koekoe.KoeKoeApp
import com.example.koekoe.data.KoeKoeApi

private const val HISTORY_SCOPE = "search"

/** 検索で絞り込める性別。[key] は一覧の項目の gender (icon_female 等) と、[g] はサイトの検索の g パラメータ。 */
private enum class SearchGender(val label: String, val key: String, val g: Int) {
    FEMALE("女性", "female", 1), MALE("男性", "male", 2), COUPLE("カップル", "couple", 3)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(app: KoeKoeApp, onOpen: (Long) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    // 選んだ性別 (ビット: 1=女性, 2=男性, 4=カップル)。何も選ばなければ全員が対象
    var genderBits by rememberSaveable { mutableIntStateOf(0) }
    var history by remember { mutableStateOf(app.history.get(HISTORY_SCOPE)) }
    val keyboard = LocalSoftwareKeyboardController.current
    val search: (String) -> Unit = { q ->
        input = q
        submitted = q.trim()
        if (submitted.isNotEmpty()) history = app.history.add(HISTORY_SCOPE, submitted)
        keyboard?.hide()
    }
    val selected = SearchGender.entries.filter { genderBits and (1 shl it.ordinal) != 0 }

    Column(Modifier.fillMaxSize()) {
        BrandTopBar("検索")
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text("キーワードを検索") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (input.isNotEmpty()) IconButton(onClick = { input = ""; submitted = "" }) {
                    Icon(Icons.Filled.Clear, "クリア")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search(input) }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        )
        // 性別 (複数選択可)。検索結果をこの性別だけに絞る
        FlowRow(
            Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchGender.entries.forEach { gnd ->
                val on = gnd in selected
                FilterChip(
                    selected = on,
                    onClick = { genderBits = genderBits xor (1 shl gnd.ordinal) },
                    label = { Text(gnd.label) },
                    leadingIcon = if (on) {
                        { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }
                    } else null,
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        if (submitted.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (history.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                        Text("キーワードを入力して検索します")
                    }
                } else {
                    HistoryList(
                        history = history,
                        onPick = search,
                        onRemove = { history = app.history.remove(HISTORY_SCOPE, it) },
                        onClear = { history = app.history.clear(HISTORY_SCOPE) },
                    )
                }
            }
        } else {
            // 1種類だけならサイト側で絞る (速い)。2種類以上はサイトが対応していないので、取得した結果をアプリ側で絞る。
            // 3種類すべては絞り込みなしと同じ
            val onlyOne = selected.singleOrNull()
            val clientFilter = if (selected.size == 2) selected.map { it.key }.toSet() else emptySet()
            VoiceListScreen(
                app, KoeKoeApi.searchPath(submitted, onlyOne?.g), onOpen,
                Modifier.weight(1f), genders = clientFilter,
            )
        }
    }
}
