package com.lavidanoesunbanano.app.data.repository

import com.lavidanoesunbanano.app.data.FakeDeviceStore
import com.lavidanoesunbanano.app.data.FakeSettingsStore
import com.lavidanoesunbanano.app.data.remote.BackendConfig
import com.lavidanoesunbanano.app.di.NetworkModule
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    private val settings = FakeSettingsStore()

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
        return DeviceRepository(store, retrofit.create(), config, settings)
    }

    @Test
    fun `registration needs no code and stores what the server generated`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"device_id":"id-del-servidor","device_token":"v1.abc.firma"}""").build())

        val result = repository().register()

        assertEquals(RegistrationResult.Success, result)
        assertEquals("id-del-servidor", store.id)
        assertEquals("Bearer v1.abc.firma", repository().authorization())
        assertTrue(settings.consentsSynced.value)
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body.contains(""""phone_usage":true"""), body)
        assertFalse(body.contains("enrollment_code"), body)
    }

    @Test
    fun `without consents the phone does not register`() = runTest {
        settings.consents.value = null

        assertEquals(RegistrationResult.NeedsConsent, repository().register())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `closed registration is reported and nothing is stored`() = runTest {
        server.enqueue(MockResponse.Builder().code(403).build())

        assertEquals(RegistrationResult.Closed, repository().register())
        assertNull(store.token)
    }

    @Test
    fun `server without registration configured`() = runTest {
        server.enqueue(MockResponse.Builder().code(503).build())

        assertEquals(RegistrationResult.NotConfigured, repository().register())
    }

    @Test
    fun `unreachable server`() = runTest {
        val repository = repository()
        server.close()

        assertEquals(RegistrationResult.Unreachable, repository.register())
    }

    @Test
    fun `blank backend url never calls the network`() = runTest {
        assertEquals(RegistrationResult.NotConfigured, repository(baseUrl = "").register())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `sync sends pending consents once`() = runTest {
        store.token = "v1.abc.firma"
        server.enqueue(
            MockResponse.Builder()
                .body("""{"excuses":true,"phone_usage":true,"cross_border":true,"analytics":true,"text_version":"v"}""")
                .build(),
        )
        val repository = repository()

        assertTrue(repository.sync())
        assertTrue(repository.sync())
        assertEquals(1, server.requestCount)
        assertEquals("PUT", server.takeRequest().method)
    }

    @Test
    fun `sync registers again after losing the token`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"device_id":"nuevo","device_token":"v1.nuevo.firma"}""").build())

        assertTrue(repository().sync())
        assertEquals("v1.nuevo.firma", store.token)
    }

    @Test
    fun `status and plan travel with the token`() = runTest {
        store.token = "v1.abc.firma"
        val status = """{"plan":"monthly","would_pay":"maybe","daily_limit":20,"used_today":1,"remaining_today":19}"""
        server.enqueue(MockResponse.Builder().body(status).build())

        val result = repository().setPlan("monthly", "maybe")

        assertEquals(19, result?.remainingToday)
        val request = server.takeRequest()
        assertEquals("Bearer v1.abc.firma", request.headers["Authorization"])
        assertTrue(request.body!!.utf8().contains(""""would_pay":"maybe""""))
    }

    @Test
    fun `status is null without a token and an expired token is forgotten`() = runTest {
        assertNull(repository().status())
        assertEquals(0, server.requestCount)

        store.token = "v1.abc.firma"
        server.enqueue(MockResponse.Builder().code(401).build())

        assertNull(repository().status())
        assertNull(store.token)
    }

    @Test
    fun `a report travels with its reason and source`() = runTest {
        store.token = "v1.abc.firma"
        server.enqueue(MockResponse.Builder().code(201).build())

        assertTrue(repository().report("Mensaje feo", "offensive", "verdict"))

        val body = server.takeRequest().body!!.utf8()
        assertTrue(body.contains(""""reason":"offensive""""), body)
        assertTrue(body.contains(""""source":"verdict""""), body)
    }

    @Test
    fun `a failed report says so`() = runTest {
        store.token = "v1.abc.firma"
        server.enqueue(MockResponse.Builder().code(503).build())

        assertFalse(repository().report("Mensaje feo", "offensive", "verdict"))
    }
}
