package com.example.koekoe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val SECTIONS = listOf(
    "このアプリについて" to
        "KoePocket は、音声掲示板 Koe-Koe (koe-koe.com) を閲覧するための、個人利用向けの非公式ビューアです。" +
        "Koe-Koe の運営者・投稿者とは無関係で、公式のアプリではありません。",
    "免責事項" to
        "・アダルトな内容 (音声) を含みます。18歳未満の方、および高校生以下の方は利用できません (Koe-Koe の利用規約に準じます)。\n" +
        "・音声や文章の著作権は、投稿者および Koe-Koe に帰属します。保存した音声は私的に楽しむ範囲でのみ利用し、" +
        "複製・再配布・転載・公衆送信はしないでください。\n" +
        "・Koe-Koe の利用規約に従ってください。規約や仕様の変更、サイトの構造変更により、予告なく動作しなくなることがあります。\n" +
        "・本アプリは現状のまま提供します。利用により生じた損害について、作者は責任を負いません。\n" +
        "・投稿がサイトから削除されても、端末に保存した音声は残ります。その扱いはご自身の責任で判断してください。",
    "プライバシーポリシー" to
        "・保存するデータは、お使いの端末の中だけです。お気に入り、フォルダ、保存済みの一覧、検索履歴、設定、" +
        "ダウンロードした音声ファイルは、すべて端末内に保存し、作者や第三者のサーバーへは送信しません。\n" +
        "・アカウント登録やログインはありません。個人情報は取得しません。\n" +
        "・解析ツール・広告・クラッシュ収集は組み込んでいません。\n" +
        "・通信先は Koe-Koe (koe-koe.com) のみです。一覧の表示、再生、ダウンロードのために、あなたの端末から直接アクセスします。\n" +
        "・画面の内容は、最近使ったアプリの一覧やスクリーンショットに映らないようにしています (設定で変更できます)。\n" +
        "・閲覧した一覧や詳細ページの一時データ (キャッシュ) を端末内に保存します。設定から削除できます。\n" +
        "・保存した音声は、端末の音楽フォルダ (Music/KoePocket) に置くため、他の音楽アプリやファイル管理アプリにも表示されることがあります。\n" +
        "・クラウドバックアップは無効にしています。アプリを削除すると一覧や設定は消えます。" +
        "ダウンロードした音声ファイル (既定は Music/KoePocket) は端末に残るため、必要に応じて削除してください。",
)

@Composable
private fun LegalBody(modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SECTIONS.forEach { (title, body) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** 初回起動時に出す、18歳以上の確認・免責・プライバシーポリシーへの同意画面。 */
@Composable
fun ConsentGate(onAccept: () -> Unit, onDecline: () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BrandTopBar("ご利用の前に")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
            Text(
                "このアプリは18歳以上の方のみご利用いただけます。",
                style = MaterialTheme.typography.titleMedium,
                color = Red,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LegalBody()
        }
        HorizontalDivider()
        Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
            ) { Text("18歳以上です。上記に同意して始める") }
            OutlinedButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) { Text("18歳未満、または同意しない (終了)") }
        }
    }
    }
}

/** 設定からいつでも読める版。 */
@Composable
fun LegalScreen(onBack: () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        BrandTopBar("利用上の注意・プライバシー", onBack = onBack)
        LegalBody(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp))
    }
    }
}
