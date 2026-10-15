package com.lavidanoesunbanano.app.data.repository

import com.lavidanoesunbanano.app.data.remote.BackendConfig
import com.lavidanoesunbanano.app.di.NetworkModule
import com.lavidanoesunbanano.app.domain.model.BackendStatus
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.create

class BackendRepositoryTest {

    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun repository(baseUrl: String = server.url("/").toString()): BackendRepository {
        val config = BackendConfig(baseUrl)
        val retrofit = NetworkModule.provideRetrofit(OkHttpClient(), NetworkModule.provideJson(), config)
        return BackendRepository(retrofit.create(), config)
    }

    @Test
    fun `reports online with the backend version`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"status":"ok","version":"0.1.0"}""").build())

        assertEquals(BackendStatus.Online("0.1.0"), repository().status())
        assertEquals("/health", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `reports the http code when the backend fails`() = runTest {
        server.enqueue(MockResponse.Builder().code(503).build())

        assertEquals(BackendStatus.HttpError(503), repository().status())
    }

    @Test
    fun `reports unreachable when nothing answers`() = runTest {
        val repository = repository()
        server.close()

        assertEquals(BackendStatus.Unreachable, repository.status())
    }

    @Test
    fun `does not call the network when the base url is blank`() = runTest {
        assertEquals(BackendStatus.NotConfigured, repository(baseUrl = "").status())
        assertEquals(0, server.requestCount)
    }
}
