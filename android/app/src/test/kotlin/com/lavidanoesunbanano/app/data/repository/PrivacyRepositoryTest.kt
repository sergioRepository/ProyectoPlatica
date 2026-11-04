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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
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
class PrivacyRepositoryTest {

    private val server = MockWebServer()
    private val settings = FakeSettingsStore()
    private val store = FakeDeviceStore(token = "v1.abc.firma")
    private lateinit var database: LaVidaDatabase
    private lateinit var events: EventRepository
    private lateinit var privacy: PrivacyRepository

    private val consentsBody =
        """{"excuses":false,"phone_usage":true,"cross_border":true,"analytics":true,"text_version":"v"}"""

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LaVidaDatabase::class.java,
        ).allowMainThreadQueries().build()
        server.start()
        val config = BackendConfig(server.url("/").toString())
        val api = NetworkModule.provideRetrofit(OkHttpClient(), NetworkModule.provideJson(), config).create<LaVidaApi>()
        val devices = DeviceRepository(store, api, config, settings)
        events = EventRepository(database.eventDao(), settings, devices, api, Clock.systemUTC())
        privacy = PrivacyRepository(database, settings, devices, events)
        events.track(AppEvents.BLOCK_EXIT)
    }

    @After
    fun tearDown() {
        database.close()
        server.close()
    }

    private suspend fun pending() = database.eventDao().due(Long.MAX_VALUE, 10).size

    @Test
    fun `revoking the excuses consent tells the server`() = runBlocking {
        server.enqueue(MockResponse.Builder().body(consentsBody).build())

        privacy.update { it.copy(excuses = false) }

        assertEquals(false, settings.consents.value?.excuses)
        assertTrue(server.takeRequest().body!!.utf8().contains(""""excuses":false"""))
        assertTrue(settings.consentsSynced.value)
    }

    @Test
    fun `turning analytics off clears the queue`() = runBlocking {
        server.enqueue(MockResponse.Builder().body(consentsBody).build())

        privacy.update { it.copy(analytics = false) }

        assertEquals(0, pending())
    }

    @Test
    fun `erasing deletes the server first and then the phone`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(204).build())

        assertTrue(privacy.eraseEverything())

        assertEquals("DELETE", server.takeRequest().method)
        assertEquals(0, pending())
        assertNull(settings.consents.value)
        assertNull(store.token)
    }

    @Test
    fun `if the server fails the phone keeps everything`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(503).build())

        assertFalse(privacy.eraseEverything())

        assertEquals(1, pending())
        assertNotNull(settings.consents.value)
        assertTrue(store.token != null)
    }

    @Test
    fun `without a token only the phone is erased`() = runBlocking {
        store.token = null

        assertTrue(privacy.eraseEverything())

        assertEquals(0, server.requestCount)
        assertEquals(0, pending())
    }
}
