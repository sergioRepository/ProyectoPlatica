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
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import retrofit2.create

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class ProtectionRepositoryTest {

    private val now = Instant.parse("2026-11-04T20:00:00Z")
    private val settings = FakeSettingsStore().apply { antiSabotage.value = true }
    private lateinit var database: LaVidaDatabase
    private lateinit var protection: ProtectionRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LaVidaDatabase::class.java,
        ).allowMainThreadQueries().build()
        val config = BackendConfig("http://localhost/")
        val api = NetworkModule.provideRetrofit(OkHttpClient(), NetworkModule.provideJson(), config).create<LaVidaApi>()
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        val devices = DeviceRepository(FakeDeviceStore(), api, config, settings)
        val events = EventRepository(database.eventDao(), settings, devices, api, clock)
        protection = ProtectionRepository(settings, events, clock)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun tracked() = database.eventDao().due(Long.MAX_VALUE, 10)

    @Test
    fun `a reason turns the protection off at once and is not kept`() = runBlocking {
        val reason = "Tengo que usar Ajustes para algo del trabajo"

        assertTrue(protection.disableNow(reason, "com.android.settings"))

        assertFalse(settings.antiSabotage.value)
        assertEquals(listOf("sabotage_attempt"), tracked().map { it.name })
        assertTrue(tracked().single().props.contains("disabled_now"))
        assertFalse(tracked().single().props.contains("trabajo"))
    }

    @Test
    fun `a short reason keeps the protection on`() = runBlocking {
        assertFalse(protection.disableNow("porque si"))

        assertTrue(settings.antiSabotage.value)
    }

    @Test
    fun `scheduling leaves it on for 24 hours`() = runBlocking {
        val at = protection.scheduleDisable()

        assertEquals(now.plus(Duration.ofHours(24)), at)
        assertEquals(at.toEpochMilli(), settings.antiSabotageOffAt.value)
        assertTrue(settings.antiSabotage.value)
    }

    @Test
    fun `a scheduled disable can be cancelled`() = runBlocking {
        protection.scheduleDisable()

        protection.cancelScheduled()

        assertNull(settings.antiSabotageOffAt.value)
    }

    @Test
    fun `turning it off clears what was scheduled`() = runBlocking {
        protection.scheduleDisable()

        protection.disableNow("Ya lo pensé y la quiero apagar")

        assertNull(settings.antiSabotageOffAt.value)
    }
}
