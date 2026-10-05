package com.example.koekoe.data

data class VoiceItem(
    val id: Long,
    val title: String,
    val author: String,
    val gender: String,
    val duration: String,
    val comments: Int,
    val likes: Int,
    val postedAgo: String,
)

data class VoiceListPage(val items: List<VoiceItem>, val hasNext: Boolean)

data class VoiceComment(val no: Int, val author: String, val body: String, val postedAt: String = "")

data class VoiceDetail(
    val id: Long,
    val title: String,
    val audioUrl: String,
    val duration: String,
    val tags: List<String>,
    val comments: List<VoiceComment>,
    val author: String = "",
    /** 投稿者の投稿一覧 (search.php?word=...&m=1)。 */
    val authorPath: String? = null,
    val genre: String = "",
    val genrePath: String? = null,
    val description: String = "",
    /** female / male / couple (一覧の項目と同じ表記)。判別できなければ空。 */
    val gender: String = "",
)

/** サイトの g パラメータ (1=女性, 2=男性, 3=カップル) を、一覧の項目と同じ表記にする。 */
fun genderFromG(g: Int?): String = when (g) {
    1 -> "female"
    2 -> "male"
    3 -> "couple"
    else -> ""
}

/** [g] は list.php の g パラメータ。null は全投稿 (v_list.php)。 */
enum class Category(val label: String, val g: Int?) {
    ALL("全て", null),
    FEMALE("女性", 1),
    MALE("男性", 2),
    COUPLE("カップル", 3);

    /**
     * 一覧のパス。タグ指定があればタグ一覧 (g でカテゴリを絞れる)。
     * 全て(g=null)は、ジャンル指定なしなら v_list.php、あれば list.php?g=0 (全カテゴリ)。
     */
    fun path(genre: Genre, tag: String? = null): String = when {
        tag != null -> KoeKoeApi.tagPath(tag) + if (g != null) "&g=$g" else ""
        g == null && genre == Genre.ALL -> "v_list.php"
        else -> "list.php?g=${g ?: 0}&g2=${genre.g2}"
    }
}

/** list.php の g2 パラメータ (all_genre.php より)。 */
enum class Genre(val label: String, val g2: Int) {
    ALL("すべて", 0),
    EROGOE("エロ声", 1),
    ONAGOE("オナ声", 2),
    EXPERIENCE("体験談", 4),
    SECRET("私の秘密", 5),
    CALL("通話", 6),
    OTHER("その他", 3),
    ARCHIVE("アーカイブ", 10),
}
