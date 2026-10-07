package app.tabunugoku.koepocket.data

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * アプリ内で選べる表示言語。[tag] が空なら端末の設定に合わせる。
 * [nativeName] は各言語での表記で、翻訳しない (null は「端末の設定に合わせる」)。
 * 選んだ言語は AppCompat が保存する (Android 13 以上はシステムの「アプリの言語」と連動)。
 */
enum class AppLanguage(val tag: String, val nativeName: String?) {
    SYSTEM("", null),
    JA("ja", "日本語"),
    EN("en", "English"),
    ZH_CN("zh-CN", "简体中文"),
    ZH_TW("zh-TW", "繁體中文"),
    KO("ko", "한국어");

    companion object {
        private val TRADITIONAL_REGIONS = setOf("TW", "HK", "MO")

        fun current(): AppLanguage {
            val locale = AppCompatDelegate.getApplicationLocales()[0] ?: return SYSTEM
            // システムの設定で選ぶと ja-JP や zh-Hans-CN のように地域・文字体系つきで入るので、言語で照合する
            return when (locale.language) {
                "zh" -> if (locale.script == "Hant" || locale.country in TRADITIONAL_REGIONS) ZH_TW else ZH_CN
                else -> entries.firstOrNull { it.tag.isNotEmpty() && it.tag.equals(locale.language, ignoreCase = true) } ?: SYSTEM
            }
        }

        fun apply(lang: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang.tag))
        }
    }
}
