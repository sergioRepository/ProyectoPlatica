package com.lavidanoesunbanano.app.data.repository

import androidx.room.withTransaction
import com.lavidanoesunbanano.app.data.local.LaVidaDatabase
import com.lavidanoesunbanano.app.data.local.dao.AttemptDao
import com.lavidanoesunbanano.app.data.local.dao.ExcuseDao
import com.lavidanoesunbanano.app.data.local.dao.ProfileDao
import com.lavidanoesunbanano.app.data.local.dao.TemplateDao
import com.lavidanoesunbanano.app.data.local.entity.AttemptEntity
import com.lavidanoesunbanano.app.data.mapper.toDomain
import com.lavidanoesunbanano.app.domain.model.AppInfo
import com.lavidanoesunbanano.app.domain.model.TemplateMessage
import com.lavidanoesunbanano.app.domain.template.MessagePicker
import com.lavidanoesunbanano.app.domain.template.PickSituation
import com.lavidanoesunbanano.app.domain.template.PlaceholderFiller
import com.lavidanoesunbanano.app.domain.template.PlaceholderValues
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Un intento ya guardado, con el mensaje listo para mostrar. */
data class BlockSession(
    val attemptId: Long,
    val app: AppInfo,
    val startedAt: Instant,
    val time: LocalTime,
    /** Intentos de hoy con esta app, contando este. */
    val attemptNumber: Int,
    /** `null` si no hay plantilla: la pantalla usa la frase local. */
    val message: String?,
)

/** Abre y cierra intentos: cuenta, elige el mensaje sin red y lo deja en el historial. */
@Singleton
class BlockSessionRepository @Inject constructor(
    private val database: LaVidaDatabase,
    private val attemptDao: AttemptDao,
    private val excuseDao: ExcuseDao,
    private val templateDao: TemplateDao,
    private val profileDao: ProfileDao,
    private val clock: Clock,
) {
    /**
     * Guarda el intento y elige su mensaje. Si ese intento (app y hora) ya existe, por ejemplo
     * porque Android recreó la pantalla, devuelve el mismo sin contarlo otra vez.
     */
    suspend fun open(app: AppInfo, startedAt: Instant): BlockSession = database.withTransaction {
        val at = startedAt.toEpochMilli()
        val local = startedAt.atZone(clock.zone)
        val existing = attemptDao.find(app.packageName, at)
        val attemptNumber = attemptDao.countBetween(app.packageName, dayStart(local), at) + if (existing == null) 1 else 0

        val messages = templateDao.getMessages().map { it.toDomain() }
        val minutesToday = excuseDao.grantedMinutesSince(app.packageName, dayStart(local))
        val chosen = if (existing != null) {
            messages.firstOrNull { it.id == existing.messageId }
        } else {
            pick(messages, attemptNumber, minutesToday, local, afterRejection = false)
        }
        val attemptId = existing?.id ?: attemptDao.insert(
            AttemptEntity(
                packageName = app.packageName,
                appLabel = app.label,
                startedAt = at,
                messageId = chosen?.id,
                outcome = null,
            ),
        )
        BlockSession(
            attemptId = attemptId,
            app = app,
            startedAt = startedAt,
            time = local.toLocalTime(),
            attemptNumber = attemptNumber,
            message = chosen?.let { fill(it, app, attemptNumber, minutesToday, local) },
        )
    }

    /** Mensaje nuevo para el mismo intento, justo después de que el juez rechazó la excusa. */
    suspend fun afterRejection(session: BlockSession): BlockSession = database.withTransaction {
        val now = clock.instant().atZone(clock.zone)
        val messages = templateDao.getMessages().map { it.toDomain() }
        val minutesToday = excuseDao.grantedMinutesSince(session.app.packageName, dayStart(now))
        val chosen = pick(messages, session.attemptNumber, minutesToday, now, afterRejection = true)
            ?: return@withTransaction session
        attemptDao.setMessage(session.attemptId, chosen.id)
        session.copy(message = fill(chosen, session.app, session.attemptNumber, minutesToday, now))
    }

    suspend fun recordExit(attemptId: Long) {
        attemptDao.setOutcome(attemptId, AttemptEntity.OUTCOME_EXIT)
    }

    private suspend fun pick(
        messages: List<TemplateMessage>,
        attemptNumber: Int,
        minutesToday: Int,
        at: ZonedDateTime,
        afterRejection: Boolean,
    ): TemplateMessage? = MessagePicker.pick(
        messages,
        PickSituation(
            attemptsToday = attemptNumber,
            minutesToday = minutesToday,
            time = at.toLocalTime(),
            afterRejection = afterRejection,
            recentIds = attemptDao.recentMessageIds(MessagePicker.RECENT_WINDOW),
        ),
    )

    private suspend fun fill(
        message: TemplateMessage,
        app: AppInfo,
        attemptNumber: Int,
        minutesToday: Int,
        at: ZonedDateTime,
    ): String {
        val profile = profileDao.get()
        return PlaceholderFiller.fill(
            message.text,
            PlaceholderValues(
                name = profile?.name.orEmpty(),
                goal = profile?.mainGoal.orEmpty(),
                app = app.label,
                attemptsToday = attemptNumber,
                minutesToday = minutesToday,
                time = at.toLocalTime(),
            ),
        )
    }

    private fun dayStart(at: ZonedDateTime): Long = at.toLocalDate().atStartOfDay(at.zone).toInstant().toEpochMilli()
}
