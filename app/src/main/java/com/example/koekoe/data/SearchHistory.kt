package com.example.koekoe.data

import android.content.Context

/** 検索・絞り込みの履歴 (新しい順、最大 [MAX] 件)。画面ごとに scope で分ける。 */
class SearchHistory(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("search_history", Context.MODE_PRIVATE)

    fun get(scope: String): List<String> =
        prefs.getString(scope, "").orEmpty().split('\n').filter { it.isNotBlank() }

    fun add(scope: String, query: String): List<String> {
        val q = query.trim().replace('\n', ' ')
        if (q.isEmpty()) return get(scope)
        return save(scope, (listOf(q) + get(scope).filterNot { it == q }).take(MAX))
    }

    fun remove(scope: String, query: String): List<String> = save(scope, get(scope).filterNot { it == query })

    fun clear(scope: String): List<String> = save(scope, emptyList())

    private fun save(scope: String, list: List<String>): List<String> {
        prefs.edit().putString(scope, list.joinToString("\n")).apply()
        return list
    }

    companion object {
        const val MAX = 10
    }
}
