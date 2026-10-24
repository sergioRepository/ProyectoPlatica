package com.lavidanoesunbanano.app.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lavidanoesunbanano.app.data.local.LaVidaDatabase
import com.lavidanoesunbanano.app.data.local.entity.AttemptEntity
import com.lavidanoesunbanano.app.data.local.entity.ExcuseEntity
import com.lavidanoesunbanano.app.data.local.entity.TemplateMessageEntity
import com.lavidanoesunbanano.app.data.local.entity.TemplateMetaEntity
import com.lavidanoesunbanano.app.data.mapper.toEntity
import com.lavidanoesunbanano.app.data.repository.BlockSessionRepository
import com.lavidanoesunbanano.app.domain.model.AppInfo
import com.lavidanoesunbanano.app.domain.model.Profile
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Room real en memoria (Robolectric): intentos guardados, contados y con mensaje. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class BlockSessionRepositoryTest {

    private lateinit var database: LaVidaDatabase
    private lateinit var sessions: BlockSessionRepository

    private val instagram = AppInfo("com.instagram.android", "Instagram")
    private val tiktok = AppInfo("com.zhiliaoapp.musically", "TikTok")
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

    // 2026-10-12, lunes al mediodía (UTC): ningún mensaje de noche ni de mañana aplica.
    private fun at(day: Int = 12, hour: Int = 12, minute: Int = 0): Instant =
        Instant.parse("2026-10-%02dT%02d:%02d:00Z".format(day, hour, minute))

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LaVidaDatabase::class.java,
        ).allowMainThreadQueries().build()
        database.profileDao().upsert(profile.toEntity())
        sessions = BlockSessionRepository(
            database,
            database.attemptDao(),
            database.excuseDao(),
            database.templateDao(),
            database.profileDao(),
            Clock.fixed(at(), ZoneOffset.UTC),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun saveTemplate(count: Int) {
        val messages = (1..count).map {
            TemplateMessageEntity(id = "m%02d".format(it), text = "Mensaje %02d: ¿{app} otra vez, {nombre}?".format(it), context = "general", intensity = 3)
        }
        database.templateDao().replaceTemplate(messages, emptyList(), TemplateMetaEntity(generatedAt = 0, profileSummary = ""))
    }

    @Test
    fun `attempts are counted per app and per day`() = runTest {
        saveTemplate(12)

        assertEquals(1, sessions.open(instagram, at(minute = 0)).attemptNumber)
        assertEquals(2, sessions.open(instagram, at(minute = 5)).attemptNumber)
        assertEquals(1, sessions.open(tiktok, at(minute = 6)).attemptNumber)
        assertEquals(3, sessions.open(instagram, at(hour = 23, minute = 59)).attemptNumber)
        assertEquals(1, sessions.open(instagram, at(day = 13, hour = 0)).attemptNumber)
    }

    @Test
    fun `the message is filled and the time is the attempt time`() = runTest {
        saveTemplate(1)

        val session = sessions.open(instagram, at(hour = 21, minute = 40))

        assertEquals("Mensaje 01: ¿Instagram otra vez, Alex?", session.message)
        assertEquals(LocalTime.of(21, 40), session.time)
    }

    @Test
    fun `opening the same attempt twice returns it without counting again`() = runTest {
        saveTemplate(12)
        sessions.open(instagram, at(minute = 0))

        val first = sessions.open(instagram, at(minute = 5))
        val again = sessions.open(instagram, at(minute = 5))

        assertEquals(first, again)
        assertEquals(2, again.attemptNumber)
        assertEquals(3, sessions.open(instagram, at(minute = 6)).attemptNumber)
    }

    @Test
    fun `eleven attempts in a row never repeat a message`() = runTest {
        saveTemplate(12)

        val shown = (0 until 11).map { sessions.open(if (it % 2 == 0) instagram else tiktok, at(minute = it)).message }

        assertEquals(11, shown.toSet().size)
    }

    @Test
    fun `exit is recorded on the attempt`() = runTest {
        saveTemplate(1)
        val session = sessions.open(instagram, at())

        sessions.recordExit(session.attemptId)

        val saved = database.attemptDao().find(instagram.packageName, at().toEpochMilli())
        assertEquals(AttemptEntity.OUTCOME_EXIT, saved?.outcome)
        assertEquals("m01", saved?.messageId)
    }

    @Test
    fun `minutes today are the minutes granted to that app today`() = runTest {
        val message = TemplateMessageEntity(id = "min", text = "Llevas {minutos_hoy} minutos en {app}.", context = "general", intensity = 3)
        database.templateDao().replaceTemplate(listOf(message), emptyList(), TemplateMetaEntity(generatedAt = 0, profileSummary = ""))
        listOf(instagram to at(hour = 9), instagram to at(hour = 10), tiktok to at(hour = 10), instagram to at(day = 11)).forEach { (app, time) ->
            database.excuseDao().insert(grant(app, time))
        }

        assertEquals("Llevas 20 minutos en Instagram.", sessions.open(instagram, at()).message)
    }

    @Test
    fun `after a rejection the same attempt gets a message for that moment`() = runTest {
        val messages = listOf(
            TemplateMessageEntity(id = "g1", text = "General.", context = "general", intensity = 3),
            TemplateMessageEntity(id = "t1", text = "La excusa no pasó, {nombre}.", context = "tras_rechazo", intensity = 3),
        )
        database.templateDao().replaceTemplate(messages, emptyList(), TemplateMetaEntity(generatedAt = 0, profileSummary = ""))
        val session = sessions.open(instagram, at())
        assertEquals("General.", session.message)

        val updated = sessions.afterRejection(session)

        assertEquals("La excusa no pasó, Alex.", updated.message)
        assertEquals(session.attemptNumber, updated.attemptNumber)
        assertEquals("t1", database.attemptDao().get(session.attemptId)?.messageId)
    }

    private fun grant(app: AppInfo, at: Instant) = ExcuseEntity(
        attemptId = 0,
        packageName = app.packageName,
        appLabel = app.label,
        type = "extra_time",
        reason = "Responder un correo del trabajo",
        decidedAt = at.toEpochMilli(),
        valid = true,
        grantedMinutes = 10,
        unlockedUntil = at.plusSeconds(600).toEpochMilli(),
        message = "Vale.",
        reasonCode = "legit_work",
        confidence = 0.9,
        offline = false,
        needsReview = false,
    )

    @Test
    fun `without a template the attempt is still saved`() = runTest {
        val session = sessions.open(instagram, at())

        assertNull(session.message)
        assertEquals(1, session.attemptNumber)
        assertTrue(session.attemptId > 0)
    }
}
