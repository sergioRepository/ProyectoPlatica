package com.lavidanoesunbanano.app.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lavidanoesunbanano.app.data.FakeDeviceStore
import com.lavidanoesunbanano.app.data.FakeSettingsStore
import com.lavidanoesunbanano.app.data.local.LaVidaDatabase
import com.lavidanoesunbanano.app.data.remote.BackendConfig
import com.lavidanoesunbanano.app.data.remote.LaVidaApi
import com.lavidanoesunbanano.app.di.NetworkModule
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import retrofit2.create

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class EventRepositoryTest {

    private val server = MockWebServer()
    private val settings = FakeSettingsStore()
    private val store = FakeDeviceStore(token = "v1.abc.firma")
    private val now = Instant.parse("2026-11-04T20:00:00Z")
    private lateinit var database: LaVidaDatabase
    private lateinit var events: EventRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LaVidaDatabase::class.java,
        ).allowMainThreadQueries().build()
        server.start()
        val config = BackendConfig(server.url("/").toString())
        val api = NetworkModule.provideRetrofit(OkHttpClient(), NetworkModule.provideJson(), config).create<LaVidaApi>()
        events = EventRepository(
            database.eventDao(),
            settings,
            DeviceRepository(store, api, config, settings),
            api,
            Clock.fixed(now, ZoneOffset.UTC),
        )
    }

    @After
    fun tearDown() {
        database.close()
        server.close()
    }

    private fun accepted() = MockResponse.Builder().body("""{"accepted":1}""").build()

    private suspend fun pending() = database.eventDao().due(Long.MAX_VALUE, 1000).size

    @Test
    fun `without the analytics consent nothing is queued`() = runBlocking {
        settings.consents.value = settings.consents.value?.copy(analytics = false)

        events.track(AppEvents.BLOCK_EXIT)

        assertEquals(0, pending())
    }

    @Test
    fun `a flush sends the queue and empties it`() = runBlocking {
        events.track(AppEvents.BLOCK_SHOWN, "com.instagram.android", "attempt" to JsonPrimitive(3))
        server.enqueue(accepted())

        assertTrue(events.flush())

        assertEquals(0, pending())
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body.contains(""""name":"block_shown""""), body)
        assertTrue(body.contains(""""attempt":3"""), body)
        assertTrue(body.contains(""""occurred_at":"2026-11-04T20:00:00Z""""), body)
    }

    @Test
    fun `batches never pass the limit of the endpoint`() = runBlocking {
        repeat(150) { events.track(AppEvents.BLOCK_EXIT) }
        server.enqueue(accepted())
        server.enqueue(accepted())

        assertTrue(events.flush())

        assertEquals(2, server.requestCount)
        assertEquals(100, Regex("block_exit").findAll(server.takeRequest().body!!.utf8()).count())
    }

    @Test
    fun `an event from the future waits for its time`() = runBlocking {
        events.track(AppEvents.FOCUS_SESSION_COMPLETED, at = now.plusSeconds(600))

        assertTrue(events.flush())

        assertEquals(0, server.requestCount)
        assertEquals(1, pending())
    }

    @Test
    fun `a server failure keeps the queue for the retry`() = runBlocking {
        events.track(AppEvents.BLOCK_EXIT)
        server.enqueue(MockResponse.Builder().code(503).build())

        assertFalse(events.flush())

        assertEquals(1, pending())
    }

    @Test
    fun `a rejected batch is dropped so it does not block the queue`() = runBlocking {
        events.track(AppEvents.BLOCK_EXIT)
        server.enqueue(MockResponse.Builder().code(422).build())

        assertTrue(events.flush())

        assertEquals(0, pending())
    }

    @Test
    fun `turning analytics off discards what was pending`() = runBlocking {
        events.track(AppEvents.BLOCK_EXIT)
        settings.consents.value = settings.consents.value?.copy(analytics = false)

        assertTrue(events.flush())

        assertEquals(0, pending())
        assertEquals(0, server.requestCount)
    }
}
