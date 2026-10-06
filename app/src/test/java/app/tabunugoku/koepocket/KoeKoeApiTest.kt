package app.tabunugoku.koepocket

import app.tabunugoku.koepocket.data.KoeKoeApi
import app.tabunugoku.koepocket.data.NotFoundException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KoeKoeApiTest {
    @get:Rule val tmp = TemporaryFolder()
    private val server = MockWebServer()

    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    private fun api(minIntervalMs: Long = 0) =
        KoeKoeApi(tmp.newFolder(), server.url("/").toString(), minIntervalMs)

    @Before fun start() = server.start()
    @After fun stop() = server.shutdown()

    @Test fun listsPage() = runBlocking {
        server.enqueue(MockResponse().setBody(res("list.html")))
        val page = api().list("list.php", 1)
        assertEquals(10, page.items.size)
        assertEquals("/list.php", server.takeRequest().path)
    }

    @Test fun secondPageUsesPageParameter() = runBlocking {
        server.enqueue(MockResponse().setBody(res("list.html")))
        server.enqueue(MockResponse().setBody(res("list.html")))
        val a = api()
        a.list("list.php", 2)
        a.list("list.php?g=1", 2)
        assertEquals("/list.php?p=2", server.takeRequest().path)
        assertEquals("/list.php?g=1&p=2", server.takeRequest().path)
    }

    @Test fun detailParsed() = runBlocking {
        server.enqueue(MockResponse().setBody(res("detail.html")))
        val d = api().detail(1)
        assertEquals("ダミー音声", d?.title)
        assertEquals("/detail.php?n=1", server.takeRequest().path)
    }

    @Test fun status404And410MeanNotFound() {
        for (code in listOf(404, 410)) {
            server.enqueue(MockResponse().setResponseCode(code))
            assertThrows(NotFoundException::class.java) { runBlocking { api().detail(1) } }
        }
    }

    @Test fun otherErrorsAreNotNotFound() {
        server.enqueue(MockResponse().setResponseCode(500))
        val e = assertThrows(Exception::class.java) { runBlocking { api().detail(1) } }
        assertFalse(e is NotFoundException)
    }

    @Test fun sendsCustomUserAgent() = runBlocking {
        server.enqueue(MockResponse().setBody(res("list.html")))
        api().list("list.php", 1)
        assertTrue(server.takeRequest().getHeader("User-Agent")!!.contains("KoeKoeViewer"))
    }

    @Test fun requestsAreSpacedByMinInterval() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setBody(res("list.html"))) }
        val a = api(minIntervalMs = 200)
        val start = System.currentTimeMillis()
        repeat(3) { a.list("list.php", it + 1) }
        // 3回の間に最低2回ぶんの間隔があく
        assertTrue(System.currentTimeMillis() - start >= 380)
    }

    @Test fun tagPagesAreCachedWhenNotEmpty() = runBlocking {
        server.enqueue(MockResponse().setBody("""<html><body><div id="tag"><a href="tag_list.php?tag=a">a</a></div></body></html>"""))
        val a = api()
        val first = a.tagsPage(1)
        val second = a.tagsPage(1)
        assertEquals(first, second)
        assertEquals(1, server.requestCount)
    }
}
