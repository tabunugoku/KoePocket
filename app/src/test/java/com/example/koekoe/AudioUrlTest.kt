package com.example.koekoe

import com.example.koekoe.data.KoeKoeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioUrlTest {
    @Test
    fun trustsOnlyKoeKoeHostsOverHttps() {
        val ok = listOf(
            "https://file.koe-koe.com/sound/upload/1.mp3",
            "https://koe-koe.com/sound/1.mp3",
        )
        val ng = listOf(
            "http://file.koe-koe.com/sound/1.mp3", // 平文は不可
            "https://evil.example.com/1.mp3",
            "https://koe-koe.com.evil.example/1.mp3", // 前方一致のすり抜け
            "https://evilkoe-koe.com/1.mp3",
            "https://koe-koe.com@evil.example/1.mp3", // userinfo を使った偽装
            "not a url",
        )
        ok.forEach { assertEquals(it, true, KoeKoeParser.isTrustedAudioUrl(it)) }
        ng.forEach { assertEquals(it, false, KoeKoeParser.isTrustedAudioUrl(it)) }
    }

    @Test
    fun detailWithForeignAudioHostIsRejected() {
        val html = """<html><head><title>t - Koe-Koe</title></head><body><audio src="https://evil.example.com/1.mp3"></audio></body></html>"""
        assertNull(KoeKoeParser.parseDetail(html, 1))
    }
}
