package com.lavidanoesunbanano.app.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lavidanoesunbanano.app.data.local.LaVidaDatabase
import com.lavidanoesunbanano.app.data.local.entity.VerdictLineEntity
import com.lavidanoesunbanano.app.data.remote.BackendConfig
import com.lavidanoesunbanano.app.data.remote.LaVidaApi
import com.lavidanoesunbanano.app.data.repository.DeviceRepository
import com.lavidanoesunbanano.app.data.repository.GenerationResult
import com.lavidanoesunbanano.app.data.repository.ProfileRepository
import com.lavidanoesunbanano.app.data.repository.TemplateRepository
import com.lavidanoesunbanano.app.di.NetworkModule
import com.lavidanoesunbanano.app.domain.model.AppInfo
import com.lavidanoesunbanano.app.domain.model.Profile
import com.lavidanoesunbanano.app.feature.onboarding.SchedulePresets
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import retrofit2.create

/** Room real en memoria (Robolectric) + backend falso: onboarding guardado y plantilla generada. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class DatabaseFlowTest {

    private lateinit var database: LaVidaDatabase
    private val server = MockWebServer()

    private val profile = Profile(
        name = "Alex",
        mainGoal = "ser el mejor programador del mundo",
        secondaryGoals = listOf("leer un libro al mes"),
        goalReason = "vivir de construir software",
        typicalExcuses = "solo cinco minutos",
        sarcasmLevel = 4,
        profanityAllowed = true,
        country = "Colombia",
        forbiddenTopics = "mi familia",
    )
    private val instagram = AppInfo("com.instagram.android", "Instagram")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LaVidaDatabase::class.java,
        ).allowMainThreadQueries().build()
        server.start()
    }

    @After
    fun tearDown() {
        database.close()
        server.close()
    }

    private fun profiles() = ProfileRepository(database, database.profileDao(), database.rulesDao())

    @Test
    fun `onboarding is saved and replaced as a whole`() = runTest {
        val profiles = profiles()

        profiles.saveOnboarding(profile, listOf(instagram), listOf(SchedulePresets.WORK, SchedulePresets.NIGHTS))

        assertEquals(profile, profiles.getProfile())
        assertEquals(listOf(instagram), profiles.getBlockedApps())
        assertEquals(listOf(SchedulePresets.WORK, SchedulePresets.NIGHTS), profiles.getRanges())

        profiles.saveOnboarding(profile.copy(name = "Sam"), emptyList(), listOf(SchedulePresets.MORNINGS))

        assertEquals("Sam", profiles.getProfile()?.name)
        assertEquals(emptyList(), profiles.getBlockedApps())
        assertEquals(listOf(SchedulePresets.MORNINGS), profiles.getRanges())
    }

    @Test
    fun `template is generated, saved and kept when the token expires`() = runTest {
        val profiles = profiles().apply { saveOnboarding(profile, listOf(instagram), listOf(SchedulePresets.WORK)) }
        val store = FakeDeviceStore(id = "0123456789abcdef0123456789abcdef", token = "v1.token.firma")
        val config = BackendConfig(server.url("/").toString())
        val api = NetworkModule.provideRetrofit(OkHttpClient(), NetworkModule.provideJson(), config).create<LaVidaApi>()
        val clock = Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC)
        val templates = TemplateRepository(database.templateDao(), profiles, DeviceRepository(store, api, config), api, config, clock)
        server.enqueue(MockResponse.Builder().body(TEMPLATE_JSON).build())

        val result = templates.generate()

        assertEquals(GenerationResult.Success(2), result)
        assertEquals(listOf("¿{app} otra vez, {nombre}?", "Son las {hora}."), templates.messages())
        assertEquals("Resumen del perfil", database.templateDao().getMeta()?.profileSummary)
        assertEquals(1, database.templateDao().getVerdictLines(VerdictLineEntity.APPROVED).size)
        val request = server.takeRequest()
        assertEquals("/templates/generate", request.url.encodedPath)
        assertEquals("Bearer v1.token.firma", request.headers["Authorization"])
        val body = request.body!!.utf8()
        assertTrue(body.contains(""""schedule_summary":"lun a vie 08:00-18:00""""), body)
        assertTrue(body.contains(""""blocked_apps":["Instagram"]"""), body)

        server.enqueue(MockResponse.Builder().code(401).build())

        assertEquals(GenerationResult.NeedsRegistration, templates.generate())
        assertNull(store.token)
        assertEquals(2, templates.messageCount())
    }

    private companion object {
        val TEMPLATE_JSON = """
            {
              "messages": [
                {"id": "m01", "text": "¿{app} otra vez, {nombre}?", "context": "general", "intensity": 4},
                {"id": "m02", "text": "Son las {hora}.", "context": "noche", "intensity": 3}
              ],
              "verdict_lines": {"approved": ["Vale."], "rejected": ["No.", "Nunca."]},
              "profile_summary": "Resumen del perfil"
            }
        """.trimIndent()
    }
}
