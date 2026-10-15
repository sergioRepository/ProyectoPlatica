package com.lavidanoesunbanano.app.data.repository

import com.lavidanoesunbanano.app.data.FakeDeviceStore
import com.lavidanoesunbanano.app.data.remote.BackendConfig
import com.lavidanoesunbanano.app.di.NetworkModule
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.create

class DeviceRepositoryTest {

    private val server = MockWebServer()
    private val store = FakeDeviceStore()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun repository(baseUrl: String = server.url("/").toString()): DeviceRepository {
        val config = BackendConfig(baseUrl)
        val retrofit = NetworkModule.provideRetrofit(OkHttpClient(), NetworkModule.provideJson(), config)
        return DeviceRepository(store, retrofit.create(), config)
    }

    @Test
    fun `device id is created once`() = runTest {
        val repository = repository()

        val first = repository.ensureDeviceId()

        assertEquals(first, repository.ensureDeviceId())
        assertTrue(first.matches(Regex("[0-9a-f]{32}")), first)
    }

    @Test
    fun `registration stores the signed token`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"device_token":"v1.abc.firma"}""").build())

        val result = repository().register("  codigo  ")

        assertEquals(RegistrationResult.Success, result)
        assertEquals("v1.abc.firma", store.token)
        assertEquals("Bearer v1.abc.firma", repository().authorization())
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body.contains(""""enrollment_code":"codigo""""), body)
        assertTrue(body.contains(""""device_id":"${store.id}""""), body)
    }

    @Test
    fun `wrong code is reported and nothing is stored`() = runTest {
        server.enqueue(MockResponse.Builder().code(403).build())

        assertEquals(RegistrationResult.WrongCode, repository().register("otro"))
        assertNull(store.token)
    }

    @Test
    fun `server without registration configured`() = runTest {
        server.enqueue(MockResponse.Builder().code(503).build())

        assertEquals(RegistrationResult.NotConfigured, repository().register("codigo"))
    }

    @Test
    fun `unreachable server`() = runTest {
        val repository = repository()
        server.close()

        assertEquals(RegistrationResult.Unreachable, repository.register("codigo"))
    }

    @Test
    fun `blank backend url never calls the network`() = runTest {
        assertEquals(RegistrationResult.NotConfigured, repository(baseUrl = "").register("codigo"))
        assertEquals(0, server.requestCount)
    }
}
