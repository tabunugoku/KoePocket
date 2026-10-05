package com.example.koekoe.data

import android.content.Context
import androidx.core.content.edit

/** アプリの設定 (SharedPreferences)。 */
class AppSettings(ctx: Context) {
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
        get() = prefs.getString(KEY_DISGUISE_APP, DEFAULT_DISGUISE_APP).orEmpty()
        set(v) = prefs.edit { putString(KEY_DISGUISE_APP, v) }

    var disguiseTitle: String
        get() = prefs.getString(KEY_DISGUISE_TITLE, DEFAULT_DISGUISE_TITLE).orEmpty()
        set(v) = prefs.edit { putString(KEY_DISGUISE_TITLE, v) }

    companion object {
        const val LEGAL_VERSION = 1
        const val DEFAULT_DISGUISE_APP = "音楽"
        const val DEFAULT_DISGUISE_TITLE = "再生中"
        private const val KEY_LEGAL = "accepted_legal_version"
        private const val KEY_DISGUISE = "disguise_enabled"
        private const val KEY_DISGUISE_APP = "disguise_app_name"
        private const val KEY_DISGUISE_TITLE = "disguise_title"
        const val KEY_TAGS = "cached_tags"
        const val KEY_DIR_PREPARED = "default_dir_prepared"
        const val KEY_AUTO_DL = "auto_download_on_favorite"
        const val KEY_SYNC = "sync_folders"
        const val KEY_DIR = "download_tree_uri"
    }
}
