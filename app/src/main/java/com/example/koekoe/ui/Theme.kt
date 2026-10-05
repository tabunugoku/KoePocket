package com.example.koekoe.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 濃紺は Koe-Koe のサイトカラー (koe_pc.css より)。アクセントはピンク。 */
val Navy = Color(0xFF062345)
val Accent = Color(0xFFFF4F9A)
val Red = Color(0xFFE34742)
val OnNavyMuted = Color(0xB3FFFFFF)

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4F5),
    onPrimaryContainer = Navy,
    secondary = Accent,
    onSecondary = Navy,
    secondaryContainer = Color(0xFFFFD9E8),
    onSecondaryContainer = Navy,
    tertiary = Red,
    onTertiary = Color.White,
    background = Color(0xFFFFF8FA),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFFF8FA),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFF3E6EC),
    onSurfaceVariant = Color(0xFF504850),
    outlineVariant = Color(0xFFE0D3DA),
)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Navy,
    primaryContainer = Color(0xFF143862),
    onPrimaryContainer = Color(0xFFD6E4F5),
    secondary = Accent,
    onSecondary = Navy,
    secondaryContainer = Color(0xFF5A1F3A),
    onSecondaryContainer = Color(0xFFFFD9E8),
    tertiary = Color(0xFFFF8A85),
    onTertiary = Navy,
    background = Color(0xFF081A30),
    onBackground = Color(0xFFE6EAF0),
    surface = Color(0xFF081A30),
    onSurface = Color(0xFFE6EAF0),
    surfaceVariant = Color(0xFF143862),
    onSurfaceVariant = Color(0xFFC3CDDA),
    outlineVariant = Color(0xFF28466B),
)

@Composable
fun KoePocketTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

/** ライト/ダーク共通の濃紺バー (タイトルはアクセント色) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun brandBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = Navy,
    titleContentColor = Accent,
    navigationIconContentColor = Color.White,
    actionIconContentColor = Color.White,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrandTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title) },
        actions = actions,
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "戻る")
            }
        },
        colors = brandBarColors(),
    )
}
