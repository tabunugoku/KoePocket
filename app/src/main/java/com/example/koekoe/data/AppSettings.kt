package com.example.koekoe.data

import com.example.koekoe.R
import android.content.Context
import androidx.core.content.edit

/** アプリの設定 (SharedPreferences)。 */
class AppSettings(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** お気に入りに入れたら自動でダウンロードする。 */
    var autoDownloadOnFavorite: Boolean
        get() = prefs.getBoolean(KEY_AUTO_DL, true)
        set(v) = prefs.edit { putBoolean(KEY_AUTO_DL, v) }

    /** 保存済みのフォルダ分けをお気に入りと同期する。 */
    var syncFolders: Boolean
        get() = prefs.getBoolean(KEY_SYNC, true)
        set(v) = prefs.edit { putBoolean(KEY_SYNC, v) }

    /** ダウンロード先に選んだフォルダ (SAF のツリー URI)。null はアプリ専用フォルダ。 */
    var downloadTreeUri: String?
        get() = prefs.getString(KEY_DIR, null)
        set(v) = prefs.edit { if (v == null) remove(KEY_DIR) else putString(KEY_DIR, v) }

    /** 既定の保存先 (Music/KoePocket) を作成済みか。初回起動時に一度だけ作る。 */
    var defaultDirPrepared: Boolean
        get() = prefs.getBoolean(KEY_DIR_PREPARED, false)
        set(v) = prefs.edit { putBoolean(KEY_DIR_PREPARED, v) }

    /** タグ一覧の1ページ目の控え (通信できないときの代わり)。 */
    var cachedTags: List<String>
        get() = prefs.getString(KEY_TAGS, "").orEmpty().split('\n').filter { it.isNotBlank() }
        set(v) = prefs.edit { putString(KEY_TAGS, v.joinToString("\n")) }

    /** 18歳以上の確認と免責・プライバシーポリシーに同意した版。[LEGAL_VERSION] 未満なら初回画面を出す。 */
    var acceptedLegalVersion: Int
        get() = prefs.getInt(KEY_LEGAL, 0)
        set(v) = prefs.edit { putInt(KEY_LEGAL, v) }

    /** 通知のアプリ名と音声タイトルを偽装する。 */
    var disguiseEnabled: Boolean
        get() = prefs.getBoolean(KEY_DISGUISE, false)
        set(v) = prefs.edit { putBoolean(KEY_DISGUISE, v) }

    var disguiseAppName: String
        get() = prefs.getString(KEY_DISGUISE_APP, null) ?: ctx.getString(R.string.disguise_default_app)
        set(v) = prefs.edit { putString(KEY_DISGUISE_APP, v) }

    var disguiseTitle: String
        get() = prefs.getString(KEY_DISGUISE_TITLE, null) ?: ctx.getString(R.string.disguise_default_title)
        set(v) = prefs.edit { putString(KEY_DISGUISE_TITLE, v) }

    /** 最近使ったアプリの一覧やスクリーンショットに、画面を映さない (FLAG_SECURE)。 */
    var secureScreen: Boolean
        get() = prefs.getBoolean(KEY_SECURE, true)
        set(v) = prefs.edit { putBoolean(KEY_SECURE, v) }

    companion object {
        const val LEGAL_VERSION = 1
        private const val KEY_SECURE = "secure_screen"
        private const val KEY_LEGAL = "accepted_legal_version"
        private const val KEY_DISGUISE = "disguise_enabled"
        private const val KEY_DISGUISE_APP = "disguise_app_name"
        private const val KEY_DISGUISE_TITLE = "disguise_title"
        private const val KEY_TAGS = "cached_tags"
        private const val KEY_DIR_PREPARED = "default_dir_prepared"
        private const val KEY_AUTO_DL = "auto_download_on_favorite"
        private const val KEY_SYNC = "sync_folders"
        private const val KEY_DIR = "download_tree_uri"
    }
}
