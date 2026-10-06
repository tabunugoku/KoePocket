package app.tabunugoku.koepocket.data

import androidx.room.*
import androidx.room.ColumnInfo
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** お気に入りと保存済みを分類するフォルダ (アプリ内の論理フォルダ。両方の画面で共通)。 */
@Entity
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity
data class Favorite(
    @PrimaryKey val id: Long,
    val title: String,
    val duration: String,
    val addedAt: Long,
    /** null は未分類。 */
    val folderId: Long? = null,
    /** female / male / couple。取得前は null。 */
    val gender: String? = null,
    /** 投稿者名・コメント数。詳細ページを開くたびに更新する。取得前は null。 */
    val author: String? = null,
    val commentCount: Int? = null,
    /** Koe-Koe から削除された(詳細ページが 404)ことを確認済み。 */
    @ColumnInfo(defaultValue = "0") val removed: Boolean = false,
)

@Entity
data class Downloaded(
    @PrimaryKey val id: Long,
    val title: String,
    /** 保存先。アプリ専用フォルダなら絶対パス、フォルダ選択(SAF)なら content:// の URI 文字列。 */
    val path: String,
    val addedAt: Long,
    val folderId: Long? = null,
    val gender: String? = null,
    val author: String? = null,
    val duration: String? = null,
    val commentCount: Int? = null,
    @ColumnInfo(defaultValue = "0") val removed: Boolean = false,
)

@Dao
interface AppDao {
    @Query("SELECT * FROM Favorite ORDER BY addedAt DESC")
    fun favorites(): Flow<List<Favorite>>

    @Query("SELECT id FROM Favorite")
    fun favoriteIds(): Flow<List<Long>>

    @Query("SELECT * FROM Favorite WHERE id = :id")
    suspend fun favorite(id: Long): Favorite?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putFavorite(f: Favorite)

    @Query("DELETE FROM Favorite WHERE id = :id")
    suspend fun removeFavorite(id: Long)

    @Query("UPDATE Favorite SET folderId = :folderId WHERE id IN (:ids)")
    suspend fun setFavoriteFolder(ids: Collection<Long>, folderId: Long?)

    @Query("SELECT * FROM Downloaded ORDER BY addedAt DESC")
    fun downloads(): Flow<List<Downloaded>>

    @Query("SELECT * FROM Downloaded")
    suspend fun allDownloads(): List<Downloaded>

    @Query("SELECT * FROM Downloaded WHERE id = :id")
    suspend fun downloaded(id: Long): Downloaded?

    @Query("SELECT * FROM Downloaded WHERE id = :id")
    fun downloadedFlow(id: Long): Flow<Downloaded?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putDownload(d: Downloaded)

    @Query("DELETE FROM Downloaded WHERE id = :id")
    suspend fun removeDownload(id: Long)

    @Query("UPDATE Downloaded SET folderId = :folderId WHERE id IN (:ids)")
    suspend fun setDownloadFolder(ids: Collection<Long>, folderId: Long?)

    @Query("SELECT * FROM Folder ORDER BY createdAt ASC, id ASC")
    fun folders(): Flow<List<Folder>>

    @Insert
    suspend fun insertFolder(f: Folder): Long

    @Query("UPDATE Folder SET name = :name WHERE id = :id")
    suspend fun renameFolder(id: Long, name: String)

    @Query("DELETE FROM Folder WHERE id = :id")
    suspend fun deleteFolder(id: Long)

    @Query("UPDATE Favorite SET gender = :gender WHERE id = :id AND gender IS NULL")
    suspend fun fillFavoriteGender(id: Long, gender: String)

    @Query("UPDATE Downloaded SET gender = :gender WHERE id = :id AND gender IS NULL")
    suspend fun fillDownloadGender(id: Long, gender: String)

    @Query("UPDATE Favorite SET author = :author, duration = :duration, commentCount = :comments, removed = 0 WHERE id = :id")
    suspend fun refreshFavorite(id: Long, author: String, duration: String, comments: Int)

    @Query("UPDATE Downloaded SET author = :author, duration = :duration, commentCount = :comments, removed = 0 WHERE id = :id")
    suspend fun refreshDownload(id: Long, author: String, duration: String, comments: Int)

    @Query("UPDATE Favorite SET removed = 1 WHERE id = :id")
    suspend fun markFavoriteRemoved(id: Long)

    @Query("UPDATE Downloaded SET removed = 1 WHERE id = :id")
    suspend fun markDownloadRemoved(id: Long)

    @Query("UPDATE Favorite SET folderId = NULL WHERE folderId = :folderId")
    suspend fun clearFavoriteFolder(folderId: Long)

    @Query("UPDATE Downloaded SET folderId = NULL WHERE folderId = :folderId")
    suspend fun clearDownloadFolder(folderId: Long)
}

@Database(entities = [Favorite::class, Downloaded::class, Folder::class], version = 4, exportSchema = true)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `Favorite` ADD COLUMN `author` TEXT")
                db.execSQL("ALTER TABLE `Favorite` ADD COLUMN `commentCount` INTEGER")
                db.execSQL("ALTER TABLE `Favorite` ADD COLUMN `removed` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `Downloaded` ADD COLUMN `author` TEXT")
                db.execSQL("ALTER TABLE `Downloaded` ADD COLUMN `duration` TEXT")
                db.execSQL("ALTER TABLE `Downloaded` ADD COLUMN `commentCount` INTEGER")
                db.execSQL("ALTER TABLE `Downloaded` ADD COLUMN `removed` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `Favorite` ADD COLUMN `gender` TEXT")
                db.execSQL("ALTER TABLE `Downloaded` ADD COLUMN `gender` TEXT")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `Folder` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("ALTER TABLE `Favorite` ADD COLUMN `folderId` INTEGER")
                db.execSQL("ALTER TABLE `Downloaded` ADD COLUMN `folderId` INTEGER")
            }
        }
    }
}
