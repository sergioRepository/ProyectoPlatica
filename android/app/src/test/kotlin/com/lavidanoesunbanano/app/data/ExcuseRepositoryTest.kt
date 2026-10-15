package com.lavidanoesunbanano.app.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lavidanoesunbanano.app.data.local.LaVidaDatabase
import com.lavidanoesunbanano.app.data.local.entity.TemplateMetaEntity
import com.lavidanoesunbanano.app.data.local.entity.VerdictLineEntity
import com.lavidanoesunbanano.app.data.mapper.toEntity
import com.lavidanoesunbanano.app.data.remote.BackendConfig
import com.lavidanoesunbanano.app.data.remote.LaVidaApi
import com.lavidanoesunbanano.app.data.repository.BlockSession
import com.lavidanoesunbanano.app.data.repository.BlockSessionRepository
import com.lavidanoesunbanano.app.data.repository.DeviceRepository
import com.lavidanoesunbanano.app.data.repository.ExcuseRepository
import com.lavidanoesunbanano.app.data.repository.JudgeConfig
import com.lavidanoesunbanano.app.di.NetworkModule
import com.lavidanoesunbanano.app.domain.model.AppInfo
import com.lavidanoesunbanano.app.domain.model.ExcuseType
import com.lavidanoesunbanano.app.domain.model.Profile
import com.lavidanoesunbanano.app.domain.model.VerdictReason
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
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

/**
 * Room real en memoria (Robolectric) + juez falso. Usa `runBlocking`: con `runTest` el
 * tiempo virtual vencería los 8 s del juez apenas la petición se queda esperando.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class ExcuseRepositoryTest {

    private lateinit var database: LaVidaDatabase
    private lateinit var sessions: BlockSessionRepository
    private lateinit var excuses: ExcuseRepository
    private val server = MockWebServer()
    private val store = FakeDeviceStore(id = "0123456789abcdef0123456789abcdef", token = "v1.token.firma")

    private val now = Instant.parse("2026-10-12T21:40:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val instagram = AppInfo("com.instagram.android", "Instagram")
    private val profile = Profile(
        name = "Alex",
        mainGoal = "ser el mejor programador del mundo",
        secondaryGoals = emptyList(),
        goalReason = "vivir de construir software",
        typicalExcuses = "solo cinco minutos",
        sarcasmLevel = 4,
        profanityAllowed = true,
        country = "Colombia",
        forbiddenTopics = "",
    )

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LaVidaDatabase::class.java,
        ).allowMainThreadQueries().build()
        database.profileDao().upsert(profile.toEntity())
        database.templateDao().replaceTemplate(
            messages = emptyList(),
            verdictLines = listOf(VerdictLineEntity(kind = VerdictLineEntity.REJECTED, text = "Ni lo sueñes.")),
            meta = TemplateMetaEntity(generatedAt = 0, profileSummary = "Resumen del perfil"),
        )
        server.start()
        val config = BackendConfig(server.url("/").toString())
        val api = NetworkModule.provideRetrofit(OkHttpClient(), NetworkModule.provideJson(), config).create<LaVidaApi>()
        sessions = BlockSessionRepository(
            database,
            database.attemptDao(),
            database.excuseDao(),
            database.templateDao(),
            database.profileDao(),
            clock,
        )
        excuses = ExcuseRepository(
            database,
            database.excuseDao(),
            database.attemptDao(),
            database.templateDao(),
            database.profileDao(),
            DeviceRepository(store, api, config),
            api,
            config,
            JudgeConfig(timeoutMillis = 500),
            clock,
        )
    }

    @After
    fun tearDown() {
        database.close()
        server.close()
    }

    private suspend fun session(minute: Int = 0): BlockSession =
        sessions.open(instagram, now.minus(Duration.ofMinutes(10)).plusSeconds(minute * 60L))

    private fun judgeReplies(valid: Boolean, minutes: Int, message: String, code: String) {
        server.enqueue(
            MockResponse.Builder()
                .body("""{"valid":$valid,"confidence":0.8,"granted_minutes":$minutes,"message":"$message","reason_code":"$code"}""")
                .build(),
        )
    }

    @Test
    fun `an approved excuse unlocks the app and closes the attempt`() = runBlocking {
        val session = session()
        judgeReplies(valid = true, minutes = 10, message = "Vale, te creo.", code = "legit_work")

        val result = excuses.judge(session, ExcuseType.EXTRA_TIME, "  Responder un correo del trabajo  ")

        assertTrue(result.verdict.valid)
        assertFalse(result.verdict.offline)
        assertEquals("Vale, te creo.", result.verdict.message)
        assertEquals(now.plus(Duration.ofMinutes(10)), result.unlockedUntil)
        assertEquals(mapOf(instagram.packageName to now.plus(Duration.ofMinutes(10))), excuses.observeUnlocks().first())
        assertEquals("extra_time", database.attemptDao().get(session.attemptId)?.outcome)
        val saved = database.excuseDao().forAttempt(session.attemptId).single()
        assertEquals("Responder un correo del trabajo", saved.reason)
        assertFalse(saved.needsReview)

        val request = server.takeRequest()
        assertEquals("/excuses/judge", request.url.encodedPath)
        assertEquals("Bearer v1.token.firma", request.headers["Authorization"])
        val body = request.body!!.utf8()
        listOf(
            """"type":"extra_time"""",
            """"reason":"Responder un correo del trabajo"""",
            """"app":"Instagram"""",
            """"profile_summary":"Resumen del perfil"""",
            """"attempts_today":1""",
            """"local_time":"21:40"""",
            """"extra_time_daily_limit":3""",
            """"emergency_daily_limit":2""",
            """"sarcasm_level":4""",
            """"profanity_allowed":true""",
            """"country":"Colombia"""",
        ).forEach { assertTrue(body.contains(it), "$it no está en $body") }
    }

    @Test
    fun `quotas and recent excuses travel to the judge`() = runBlocking {
        judgeReplies(valid = true, minutes = 10, message = "Vale.", code = "legit_work")
        excuses.judge(session(0), ExcuseType.EXTRA_TIME, "Responder un correo del trabajo")
        judgeReplies(valid = false, minutes = 0, message = "No.", code = "boredom")
        excuses.judge(session(1), ExcuseType.EXTRA_TIME, "Solo quiero ver un video de gatos")
        judgeReplies(valid = false, minutes = 0, message = "Esa excusa ya la usaste.", code = "repeated")
        val rejected = excuses.judge(session(2), ExcuseType.EXTRA_TIME, "Responder un correo del trabajo")

        assertEquals(VerdictReason.REPEATED, rejected.verdict.reason)
        assertNull(rejected.unlockedUntil)
        repeat(2) { server.takeRequest() }
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body.contains(""""extra_time_granted_today":1"""), body)
        assertTrue(body.contains(""""emergencies_granted_today":0"""), body)
        // De la más vieja a la más nueva, como las lee el backend.
        assertTrue(
            body.contains(""""recent_excuses":["Responder un correo del trabajo","Solo quiero ver un video de gatos"]"""),
            body,
        )
        assertTrue(body.contains(""""attempts_today":3"""), body)
    }

    @Test
    fun `when the judge fails an emergency is approved and flagged`() = runBlocking {
        val session = session()
        server.enqueue(MockResponse.Builder().code(503).build())

        val result = excuses.judge(session, ExcuseType.EMERGENCY, "Tengo que pagar el arriendo antes de las 5")

        assertTrue(result.verdict.valid)
        assertTrue(result.verdict.offline)
        assertEquals(now.plus(Duration.ofMinutes(15)), result.unlockedUntil)
        assertTrue(database.excuseDao().forAttempt(session.attemptId).single().needsReview)
        assertEquals("emergency", database.attemptDao().get(session.attemptId)?.outcome)
    }

    @Test
    fun `when the judge fails extra time is rejected with a template line`() = runBlocking {
        val session = session()
        server.enqueue(MockResponse.Builder().code(503).build())

        val result = excuses.judge(session, ExcuseType.EXTRA_TIME, "Solo quiero ver una cosa rápido")

        assertFalse(result.verdict.valid)
        assertEquals("Ni lo sueñes.", result.verdict.message)
        assertNull(result.unlockedUntil)
        assertNull(database.attemptDao().get(session.attemptId)?.outcome)
    }

    @Test
    fun `a judge slower than the limit falls back to the offline rule`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .body("""{"valid":true,"confidence":0.8,"granted_minutes":10,"message":"Tarde.","reason_code":"legit_work"}""")
                .headersDelay(2, TimeUnit.SECONDS)
                .build(),
        )

        val result = excuses.judge(session(), ExcuseType.EXTRA_TIME, "Responder un correo del trabajo")

        assertTrue(result.verdict.offline)
        assertFalse(result.verdict.valid)
    }

    @Test
    fun `without a token there is no request`() = runBlocking {
        store.token = null

        val result = excuses.judge(session(), ExcuseType.EMERGENCY, "Tengo que pagar el arriendo antes de las 5")

        assertTrue(result.verdict.offline)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `an expired token is forgotten`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(401).build())

        val result = excuses.judge(session(), ExcuseType.EXTRA_TIME, "Responder un correo del trabajo")

        assertTrue(result.verdict.offline)
        assertNull(store.token)
    }
}
