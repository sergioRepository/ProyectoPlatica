package com.lavidanoesunbanano.app.data.repository

import com.lavidanoesunbanano.app.data.FakeAgentSchedule
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
    private val schedule = FakeAgentSchedule()

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
        return DeviceRepository(store, retrofit.create(), config, settings, schedule)
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
    fun `status travels with the token`() = runTest {
        store.token = "v1.abc.firma"
        val status = """{"plan":"monthly","daily_limit":20,"used_today":1,"remaining_today":19}"""
        server.enqueue(MockResponse.Builder().body(status).build())

        val result = repository().status()

        assertEquals(19, result?.remainingToday)
        assertEquals("Bearer v1.abc.firma", server.takeRequest().headers["Authorization"])
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

    private val verified = """
        {"outcome":"subscription","device":{"plan":"monthly","daily_limit":20,"remaining_today":19,
        "subscription_state":"active","expiry_time":"2030-01-01T00:00:00Z","auto_renewing":true,
        "credits":3,"included_daily":20,"included_remaining":19,"agent_enabled":true}}
    """.trimIndent()

    @Test
    fun `verifyPurchase sends token and snake case body and stores the validated plan`() = runTest {
        store.token = "v1.abc.firma"
        server.enqueue(MockResponse.Builder().body(verified).build())

        val result = repository().verifyPurchase("monthly_1", "tok-123")

        assertTrue(result is VerifyResult.Verified)
        assertEquals("subscription", (result as VerifyResult.Verified).outcome)
        assertEquals("monthly", store.validatedPlan)
        assertTrue(store.agentEnabled)
        val request = server.takeRequest()
        assertEquals("Bearer v1.abc.firma", request.headers["Authorization"])
        val body = request.body!!.utf8()
        assertTrue(body.contains(""""product_id":"monthly_1""""), body)
        assertTrue(body.contains(""""purchase_token":"tok-123""""), body)
    }

    @Test
    fun `verifyPurchase maps http errors`() = runTest {
        store.token = "v1.abc.firma"
        val repository = repository()
        server.enqueue(MockResponse.Builder().code(404).build())
        server.enqueue(MockResponse.Builder().code(422).build())
        server.enqueue(MockResponse.Builder().code(503).build())

        assertEquals(VerifyResult.Rejected, repository.verifyPurchase("p", "t"))
        assertEquals(VerifyResult.Rejected, repository.verifyPurchase("p", "t"))
        assertEquals(VerifyResult.Unavailable, repository.verifyPurchase("p", "t"))
        assertNull(store.validatedPlan)
    }

    @Test
    fun `verifyPurchase without token does not call the server`() = runTest {
        assertEquals(VerifyResult.NotRegistered, repository().verifyPurchase("p", "t"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `status stores the validated plan and a network failure keeps it`() = runTest {
        store.token = "v1.abc.firma"
        val repository = repository()
        val device = verified.substringAfter(""""device":""").removeSuffix("}")
        server.enqueue(MockResponse.Builder().body(device).build())
        assertEquals(19, repository.status()?.includedRemaining)
        assertEquals("monthly", store.validatedPlan)
        assertTrue(store.agentEnabled)

        server.close()

        assertNull(repository.status())
        assertEquals("monthly", store.validatedPlan)
        assertTrue(store.agentEnabled)
    }

    private fun statusJson(enabled: Boolean) =
        """{"plan":"free","daily_limit":5,"remaining_today":5,"agent_enabled":$enabled}"""

    @Test
    fun `status schedules the agent as the server says and a network failure does not touch it`() = runTest {
        store.token = "v1.abc.firma"
        val repository = repository()
        server.enqueue(MockResponse.Builder().body(statusJson(true)).build())
        repository.status()
        server.enqueue(MockResponse.Builder().body(statusJson(false)).build())
        repository.status()
        assertEquals(listOf(true, false), schedule.calls)

        server.close()
        repository.status()
        assertEquals(listOf(true, false), schedule.calls)
    }

    @Test
    fun `verifyPurchase schedules the agent from the validated device only`() = runTest {
        store.token = "v1.abc.firma"
        val repository = repository()
        server.enqueue(MockResponse.Builder().body(verified).build())
        repository.verifyPurchase("monthly_1", "tok")
        server.enqueue(MockResponse.Builder().body(verified.replace(""""agent_enabled":true""", """"agent_enabled":false""")).build())
        repository.verifyPurchase("monthly_1", "tok")
        server.enqueue(MockResponse.Builder().code(503).build())
        repository.verifyPurchase("monthly_1", "tok")

        assertEquals(listOf(true, false), schedule.calls)
    }

    @Test
    fun `accountHash is a sha256 hex and never the id`() = runTest {
        assertNull(repository().accountHash())

        store.id = "id-del-servidor"
        val hash = repository().accountHash()!!

        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' }, hash)
        assertFalse(hash.contains("id-del-servidor"))
    }

    @Test
    fun `an old server status without billing fields still parses`() = runTest {
        store.token = "v1.abc.firma"
        server.enqueue(MockResponse.Builder().body("""{"plan":"free","daily_limit":5,"remaining_today":5}""").build())

        val status = repository().status()

        assertEquals("free", status?.plan)
        assertEquals(0, status?.credits)
        assertFalse(status?.agentEnabled == true)
    }

    @Test
    fun `a new phone does not register when it accepts the consents`() = runTest {
        repository().syncIfRegistered()

        assertEquals(0, server.requestCount)
    }
}
