package com.example.koekoe

import com.example.koekoe.data.KoeKoeParser
import org.junit.Assert.*
import org.junit.Test

class KoeKoeParserTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test fun parsesList() {
        val page = KoeKoeParser.parseList(res("list.html"))
        assertEquals(10, page.items.size)
        assertTrue(page.hasNext)
        val first = page.items.first()
        assertEquals(1001L, first.id)
        assertEquals("作者1", first.author)
        assertEquals("ダミー音声1", first.title)
        assertEquals("female", first.gender)
        assertEquals("1分", first.duration)
        assertEquals(1, first.likes)
        assertEquals("1分前", first.postedAgo)
    }

    @Test fun parsesDetail() {
        val d = KoeKoeParser.parseDetail(res("detail.html"), 1)!!
        assertEquals("https://file.koe-koe.com/sound/old/1.mp3", d.audioUrl)
        assertEquals("ダミー音声", d.title)
        assertEquals(listOf("ダミー"), d.tags)
        assertEquals("甲", d.comments.first { it.no == 1 }.author)
        assertEquals("12/11/9", d.comments.first { it.no == 1 }.postedAt)
        assertEquals("12/11/17", d.comments.first { it.no == 2 }.postedAt)
    }

    @Test fun durationIgnoresRelatedItems() {
        val html = """<html><head><title>t [5] - Koe-Koe</title></head><body>
            <div class="content"><div class="audioTime audioTime_female">7分</div></div>
            <audio src="//file.koe-koe.com/sound/upload/5.mp3"></audio>
            <div class="audioTime audioTime_1">2分11秒</div></body></html>"""
        assertEquals("2分11秒", KoeKoeParser.parseDetail(html, 5)!!.duration)
    }

    @Test fun parsesAuthorGenreAndDescription() {
        val d = KoeKoeParser.parseDetail(res("detail_mobile.html"), 2)!!
        assertEquals("テスト君", d.author)
        assertEquals("search.php?word=%E3%83%86%E3%82%B9%E3%83%88&g=2&m=1", d.authorPath)
        assertEquals("male", d.gender)
        assertEquals("オナ声", d.genre)
        assertEquals("list.php?g=2&g2=2", d.genrePath)
        assertTrue(d.description.startsWith("恥ずかしいからすぐ消すかも。"))
    }

    @Test fun parsesTagList() {
        val html = """<a href="tag_list.php?tag=%E3%82%AA%E3%83%9B%E5%A3%B0">オホ声</a>
            <a href="tag_list.php?tag=x">オナニー</a><a href="tag_list.php?tag=x">オナニー</a><a href="other.php">no</a>"""
        assertEquals(listOf("オホ声", "オナニー"), KoeKoeParser.parseTags(html))
    }

    @Test fun searchPathAddsGenderOnlyWhenGiven() {
        assertEquals("search.php?word=%E3%81%82", com.example.koekoe.data.KoeKoeApi.searchPath("あ"))
        assertEquals("search.php?word=ASMR&g=3", com.example.koekoe.data.KoeKoeApi.searchPath("ASMR", 3))
    }

    @Test fun filtersListByGender() {
        val page = KoeKoeParser.parseList(res("list.html"))
        val all = page.items
        assertTrue(all.map { it.gender }.toSet().containsAll(setOf("female", "male")))
        assertEquals(all, com.example.koekoe.ui.filterByGender(all, emptySet()))
        val onlyFemale = com.example.koekoe.ui.filterByGender(all, setOf("female"))
        assertTrue(onlyFemale.isNotEmpty() && onlyFemale.all { it.gender == "female" })
        val both = com.example.koekoe.ui.filterByGender(all, setOf("female", "male"))
        assertEquals(all.size, both.size)
        assertTrue(com.example.koekoe.ui.filterByGender(all, setOf("couple")).isEmpty())
    }

    @Test fun categoryPaths() {
        assertEquals("v_list.php", com.example.koekoe.data.Category.ALL.path(com.example.koekoe.data.Genre.ALL))
        assertEquals("list.php?g=0&g2=1", com.example.koekoe.data.Category.ALL.path(com.example.koekoe.data.Genre.EROGOE))
        assertEquals("list.php?g=2&g2=0", com.example.koekoe.data.Category.MALE.path(com.example.koekoe.data.Genre.ALL))
        assertEquals("tag_list.php?tag=x&g=1", com.example.koekoe.data.Category.FEMALE.path(com.example.koekoe.data.Genre.ALL, "x"))
    }

    @Test fun parsesDetailWithAudioSrcAttribute() {
        val d = KoeKoeParser.parseDetail(res("detail_mobile.html"), 2)!!
        assertTrue(d.audioUrl.startsWith("https://file.koe-koe.com/sound/upload/2.mp3"))
        assertEquals("ダミー音声 モバイル", d.title)
        assertEquals("7分15秒", d.duration)
    }
}
