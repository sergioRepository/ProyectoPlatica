package com.lavidanoesunbanano.app.data.repository

import androidx.room.withTransaction
import com.lavidanoesunbanano.app.data.local.LaVidaDatabase
import com.lavidanoesunbanano.app.data.local.dao.AttemptDao
import com.lavidanoesunbanano.app.data.local.dao.ProfileDao
import com.lavidanoesunbanano.app.data.local.dao.TemplateDao
import com.lavidanoesunbanano.app.data.local.entity.AttemptEntity
import com.lavidanoesunbanano.app.data.mapper.toDomain
import com.lavidanoesunbanano.app.domain.model.AppInfo
import com.lavidanoesunbanano.app.domain.template.MessagePicker
import com.lavidanoesunbanano.app.domain.template.PickSituation
import com.lavidanoesunbanano.app.domain.template.PlaceholderFiller
import com.lavidanoesunbanano.app.domain.template.PlaceholderValues
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
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
        val dayStart = local.toLocalDate().atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val existing = attemptDao.find(app.packageName, at)
        val attemptNumber = attemptDao.countBetween(app.packageName, dayStart, at) + if (existing == null) 1 else 0

        val messages = templateDao.getMessages().map { it.toDomain() }
        val chosen = if (existing != null) {
            messages.firstOrNull { it.id == existing.messageId }
        } else {
            val situation = PickSituation(
                attemptsToday = attemptNumber,
                // Los minutos concedidos hoy llegan con los desbloqueos temporales.
                minutesToday = 0,
                time = local.toLocalTime(),
                recentIds = attemptDao.recentMessageIds(MessagePicker.RECENT_WINDOW),
            )
            MessagePicker.pick(messages, situation)
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

        val profile = profileDao.get()
        val text = chosen?.let {
            PlaceholderFiller.fill(
                it.text,
                PlaceholderValues(
                    name = profile?.name.orEmpty(),
                    goal = profile?.mainGoal.orEmpty(),
                    app = app.label,
                    attemptsToday = attemptNumber,
                    minutesToday = 0,
                    time = local.toLocalTime(),
                ),
            )
        }
        BlockSession(
            attemptId = attemptId,
            app = app,
            startedAt = startedAt,
            time = local.toLocalTime(),
            attemptNumber = attemptNumber,
            message = text,
        )
    }

    suspend fun recordExit(attemptId: Long) {
        attemptDao.setOutcome(attemptId, AttemptEntity.OUTCOME_EXIT)
    }
}
