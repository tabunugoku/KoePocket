package app.tabunugoku.koepocket.ui

import androidx.compose.ui.res.stringResource
import app.tabunugoku.koepocket.R
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

@Composable
private fun legalSections() = listOf(
    stringResource(R.string.legal_about_title) to stringResource(R.string.legal_about_body),
    stringResource(R.string.legal_disclaimer_title) to stringResource(R.string.legal_disclaimer_body),
    stringResource(R.string.legal_privacy_title) to stringResource(R.string.legal_privacy_body),
)

@Composable
private fun LegalBody(modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        legalSections().forEach { (title, body) ->
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
        BrandTopBar(stringResource(R.string.consent_title))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
            Text(
                stringResource(R.string.consent_18),
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
            ) { Text(stringResource(R.string.consent_accept)) }
            OutlinedButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.consent_decline)) }
        }
    }
    }
}

/** 設定からいつでも読める版。 */
@Composable
fun LegalScreen(onBack: () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        BrandTopBar(stringResource(R.string.legal_link), onBack = onBack)
        LegalBody(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp))
    }
    }
}
