package com.example.koekoe.data

/** お気に入り・保存済み・フォルダの操作。フォルダ分けの同期設定 ([AppSettings.syncFolders]) をここで一元的に扱う。 */
class Library(private val db: AppDb, private val settings: AppSettings) {
    private val dao get() = db.dao()

    /**
     * お気に入りに追加。同期ON で保存済みなら、保存済み側のフォルダを引き継ぐ。
     * [partial] は保存済みの控えから作った不完全な詳細 (オフライン表示)。コメント数が分からないので記録しない。
     */
    suspend fun addFavorite(d: VoiceDetail, partial: Boolean = false) {
        val folder = (if (settings.syncFolders) dao.downloaded(d.id)?.folderId else null)
            ?: dao.favorite(d.id)?.folderId
        dao.putFavorite(
            Favorite(
                d.id, d.title, d.duration, System.currentTimeMillis(), folder,
                gender = d.gender.ifEmpty { null },
                author = d.author.ifEmpty { null },
                commentCount = if (partial) null else d.comments.size,
            ),
        )
    }

    /**
     * 詳細ページを開いたとき、保存してある項目の情報 (投稿者・長さ・コメント数・性別) を最新にし、
     * 「サイトから削除された」印を外す。以前に追加した項目の分もここで補われる。
     */
    suspend fun refresh(d: VoiceDetail) {
        dao.refreshFavorite(d.id, d.author, d.duration, d.comments.size)
        dao.refreshDownload(d.id, d.author, d.duration, d.comments.size)
        if (d.gender.isNotEmpty()) {
            dao.fillFavoriteGender(d.id, d.gender)
            dao.fillDownloadGender(d.id, d.gender)
        }
    }

    /** Koe-Koe から投稿が削除されていた (詳細ページが 404)。項目は残し、印だけ付ける。 */
    suspend fun markRemoved(id: Long) {
        dao.markFavoriteRemoved(id)
        dao.markDownloadRemoved(id)
    }

    /** ダウンロード完了時。同期ON でお気に入りなら、お気に入り側のフォルダに入れる。 */
    suspend fun addDownload(d: VoiceDetail, path: String) {
        val folder = (if (settings.syncFolders) dao.favorite(d.id)?.folderId else null)
            ?: dao.downloaded(d.id)?.folderId
        dao.putDownload(
            Downloaded(
                d.id, d.title, path, System.currentTimeMillis(), folder,
                gender = d.gender.ifEmpty { null },
                author = d.author.ifEmpty { null },
                duration = d.duration.ifEmpty { null },
                commentCount = d.comments.size,
            ),
        )
    }

    suspend fun moveFavorites(ids: Set<Long>, folderId: Long?) {
        dao.setFavoriteFolder(ids, folderId)
        if (settings.syncFolders) dao.setDownloadFolder(ids, folderId)
    }

    suspend fun moveDownloads(ids: Set<Long>, folderId: Long?) {
        dao.setDownloadFolder(ids, folderId)
        if (settings.syncFolders) dao.setFavoriteFolder(ids, folderId)
    }

    suspend fun createFolder(name: String): Long =
        dao.insertFolder(Folder(name = name.trim(), createdAt = System.currentTimeMillis()))

    suspend fun renameFolder(id: Long, name: String) = dao.renameFolder(id, name.trim())

    /** フォルダを削除。中の項目は未分類に戻る (項目自体は消えない)。 */
    suspend fun deleteFolder(id: Long) {
        dao.clearFavoriteFolder(id)
        dao.clearDownloadFolder(id)
        dao.deleteFolder(id)
    }

    /** 同期を ON にしたとき、お気に入り側のフォルダを保存済みに揃える。 */
    suspend fun syncDownloadsFromFavorites() {
        dao.allDownloads().forEach { d ->
            val fav = dao.favorite(d.id) ?: return@forEach
            if (fav.folderId != d.folderId) dao.setDownloadFolder(setOf(d.id), fav.folderId)
        }
    }
}
