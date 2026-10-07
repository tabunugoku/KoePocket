package app.tabunugoku.koepocket

import app.tabunugoku.koepocket.data.KoeKoeApi
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class AudioClientTest {
    @get:Rule val tmp = TemporaryFolder()
    private val server = MockWebServer()

    @Before fun start() = server.start()
    @After fun stop() = server.shutdown()

    @Test fun audioClientHasNoHttpCacheWhileApiClientHasOne() {
        val api = KoeKoeApi(tmp.newFolder())
        assertNull(api.audioClient.cache)
        assertTrue(api.client.cache != null)
    }

    @Test fun audioClientRefusesUntrustedHostWithoutSendingARequest() {
        val api = KoeKoeApi(tmp.newFolder())
        val request = Request.Builder().url(server.url("/a.mp3")).build()
        val e = runCatching { api.audioClient.newCall(request).execute() }.exceptionOrNull()
        assertTrue(e is IOException)
        assertEquals(0, server.requestCount)
    }

    @Test fun apiClientStillReachesTheTestServer() {
        server.enqueue(MockResponse().setBody("ok"))
        val api = KoeKoeApi(tmp.newFolder())
        api.client.newCall(Request.Builder().url(server.url("/")).build()).execute().use { assertEquals(200, it.code) }
    }
}
