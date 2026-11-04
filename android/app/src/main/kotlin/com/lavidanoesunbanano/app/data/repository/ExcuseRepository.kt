package com.lavidanoesunbanano.app.data.repository

import androidx.room.withTransaction
import com.lavidanoesunbanano.app.data.local.LaVidaDatabase
import com.lavidanoesunbanano.app.data.local.dao.AttemptDao
import com.lavidanoesunbanano.app.data.local.dao.ExcuseDao
import com.lavidanoesunbanano.app.data.local.dao.ProfileDao
import com.lavidanoesunbanano.app.data.local.dao.TemplateDao
import com.lavidanoesunbanano.app.data.local.entity.ExcuseEntity
import com.lavidanoesunbanano.app.data.local.entity.VerdictLineEntity
import com.lavidanoesunbanano.app.data.prefs.SettingsStore
import com.lavidanoesunbanano.app.data.remote.BackendConfig
import com.lavidanoesunbanano.app.data.remote.LaVidaApi
import com.lavidanoesunbanano.app.data.remote.dto.JudgeRequestDto
import com.lavidanoesunbanano.app.data.remote.dto.JudgeResponseDto
import com.lavidanoesunbanano.app.domain.excuse.OfflineVerdict
import com.lavidanoesunbanano.app.domain.model.ExcuseType
import com.lavidanoesunbanano.app.domain.model.UnlockLimits
import com.lavidanoesunbanano.app.domain.model.Verdict
import com.lavidanoesunbanano.app.domain.model.VerdictReason
import com.lavidanoesunbanano.app.domain.schedule.ScheduleFormatter
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.HttpException

/** Cuánto se espera al juez antes de aplicar la regla sin conexión. Los cupos están en Ajustes. */
data class JudgeConfig(
    val timeoutMillis: Long = 8_000,
)

data class ExcuseResult(
    val verdict: Verdict,
    /** Hasta cuándo queda desbloqueada la app; `null` si se rechazó. */
    val unlockedUntil: Instant?,
)

/** Lo que queda hoy de cada cupo. `judged`: las razones van al juez, que es quien aplica los cupos. */
data class QuotaToday(
    val extraTimeLeft: Int,
    val extraTimeDaily: Int,
    val emergencyLeft: Int,
    val emergencyDaily: Int,
    val judged: Boolean,
)

/** Pide el veredicto, aplica la regla sin conexión si hace falta y deja todo en el historial. */
@Singleton
class ExcuseRepository @Inject constructor(
    private val database: LaVidaDatabase,
    private val excuseDao: ExcuseDao,
    private val attemptDao: AttemptDao,
    private val templateDao: TemplateDao,
    private val profileDao: ProfileDao,
    private val deviceRepository: DeviceRepository,
    private val api: LaVidaApi,
    private val backend: BackendConfig,
    private val config: JudgeConfig,
    private val settings: SettingsStore,
    private val events: EventRepository,
    private val clock: Clock,
) {
    /** Último desbloqueo de cada app. Los vencidos los descarta `BlockPolicy`. */
    fun observeUnlocks(): Flow<Map<String, Instant>> = excuseDao.observeUnlocks().map { rows ->
        rows.associate { it.packageName to Instant.ofEpochMilli(it.unlockedUntil) }
    }

    suspend fun quotaToday(): QuotaToday {
        val limits = settings.limits.first()
        val dayStart = clock.instant().atZone(clock.zone).toLocalDate().atStartOfDay(clock.zone).toInstant().toEpochMilli()
        return QuotaToday(
            extraTimeLeft = (limits.extraTimeDaily - excuseDao.countGrantedSince(ExcuseType.EXTRA_TIME.wire, dayStart)).coerceAtLeast(0),
            extraTimeDaily = limits.extraTimeDaily,
            emergencyLeft = (limits.emergencyDaily - excuseDao.countGrantedSince(ExcuseType.EMERGENCY.wire, dayStart)).coerceAtLeast(0),
            emergencyDaily = limits.emergencyDaily,
            judged = settings.consents.first()?.excuses == true,
        )
    }

    suspend fun judge(session: BlockSession, type: ExcuseType, reason: String): ExcuseResult {
        val text = reason.trim().take(MAX_REASON_CHARS)
        val limits = settings.limits.first()
        // Sin esa autorización, el texto no sale del teléfono.
        val allowed = settings.consents.first()?.excuses == true
        val verdict = (if (allowed) askJudge(request(session, type, text, limits), limits) else null)
            ?: OfflineVerdict.decide(
                type,
                limits,
                // Las frases de la plantilla son sarcásticas: el modo amable usa la local.
                if (settings.kindMode.first()) emptyList() else templateDao.getVerdictLines(VerdictLineEntity.REJECTED).map { it.text },
                text,
            )
        // El tiempo corre desde el veredicto.
        val decidedAt = clock.instant()
        val unlockedUntil = if (verdict.valid && verdict.grantedMinutes > 0) {
            decidedAt.plus(Duration.ofMinutes(verdict.grantedMinutes.toLong()))
        } else {
            null
        }
        database.withTransaction {
            excuseDao.insert(
                ExcuseEntity(
                    attemptId = session.attemptId,
                    packageName = session.app.packageName,
                    appLabel = session.app.label,
                    type = type.wire,
                    reason = text,
                    decidedAt = decidedAt.toEpochMilli(),
                    valid = verdict.valid,
                    grantedMinutes = verdict.grantedMinutes,
                    unlockedUntil = unlockedUntil?.toEpochMilli(),
                    message = verdict.message,
                    reasonCode = verdict.reason.wire,
                    confidence = verdict.confidence,
                    offline = verdict.offline,
                    needsReview = OfflineVerdict.needsReview(type, verdict),
                ),
            )
            if (verdict.valid) attemptDao.setOutcome(session.attemptId, type.wire)
        }
        track(session, type, verdict)
        return ExcuseResult(verdict, unlockedUntil)
    }

    /** Solo el veredicto y su código: el texto de la excusa nunca entra a la analítica. */
    private suspend fun track(session: BlockSession, type: ExcuseType, verdict: Verdict) {
        val app = session.app.packageName
        events.track(
            if (type == ExcuseType.EMERGENCY) AppEvents.EMERGENCY_REQUESTED else AppEvents.EXTRA_TIME_REQUESTED,
            app,
            "verdict" to JsonPrimitive(if (verdict.valid) "approved" else "rejected"),
            "reason_code" to JsonPrimitive(verdict.reason.wire),
        )
        if (verdict.reason == VerdictReason.PLAN_QUOTA) {
            events.track(AppEvents.QUOTA_REACHED, app, "source" to JsonPrimitive("judge"))
        }
    }

    private suspend fun request(
        session: BlockSession,
        type: ExcuseType,
        reason: String,
        limits: UnlockLimits,
    ): JudgeRequestDto {
        val now = clock.instant().atZone(clock.zone)
        val dayStart = now.toLocalDate().atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val profile = profileDao.get()
        val kind = settings.kindMode.first()
        return JudgeRequestDto(
            type = type.wire,
            reason = reason,
            app = session.app.label.take(MAX_APP_CHARS),
            profileSummary = templateDao.getMeta()?.profileSummary.orEmpty().take(MAX_SUMMARY_CHARS),
            attemptsToday = session.attemptNumber,
            extraTimeGrantedToday = excuseDao.countGrantedSince(ExcuseType.EXTRA_TIME.wire, dayStart),
            // De la más vieja a la más nueva: el backend toma las últimas como las más recientes.
            recentExcuses = excuseDao.recentReasons(RECENT_EXCUSES).asReversed(),
            localTime = ScheduleFormatter.time(now.toLocalTime()),
            emergenciesGrantedToday = excuseDao.countGrantedSince(ExcuseType.EMERGENCY.wire, dayStart),
            extraTimeDailyLimit = limits.extraTimeDaily,
            emergencyDailyLimit = limits.emergencyDaily,
            sarcasmLevel = if (kind) MIN_SARCASM else profile?.sarcasmLevel ?: DEFAULT_SARCASM,
            profanityAllowed = !kind && profile?.profanityAllowed ?: false,
            country = profile?.country.orEmpty().take(MAX_COUNTRY_CHARS),
            kindMode = kind,
        )
    }

    /** `null` si el juez no contestó a tiempo o falló: se aplica la regla sin conexión. */
    private suspend fun askJudge(request: JudgeRequestDto, limits: UnlockLimits): Verdict? {
        if (!backend.isConfigured) return null
        val authorization = deviceRepository.authorization() ?: return null
        return try {
            withTimeoutOrNull(config.timeoutMillis) { api.judgeExcuse(authorization, request) }
                ?.toVerdict(ExcuseType.fromWire(request.type), limits)
        } catch (e: HttpException) {
            if (e.code() == 401) deviceRepository.forgetToken()
            null
        } catch (e: IOException) {
            null
        } catch (e: SerializationException) {
            null
        }
    }

    private fun JudgeResponseDto.toVerdict(type: ExcuseType, limits: UnlockLimits) = Verdict(
        valid = valid,
        grantedMinutes = when {
            !valid -> 0
            grantedMinutes > 0 -> grantedMinutes
            else -> limits.minutesFor(type)
        },
        message = message.ifBlank { null },
        reason = VerdictReason.fromWire(reasonCode),
        confidence = confidence,
        offline = false,
    )

    private companion object {
        // Límites del backend (banano/schemas/judge.py): recortar aquí evita un 422.
        const val MAX_REASON_CHARS = 1000
        const val MAX_APP_CHARS = 80
        const val MAX_SUMMARY_CHARS = 1500
        const val MAX_COUNTRY_CHARS = 40
        const val RECENT_EXCUSES = 10
        const val DEFAULT_SARCASM = 3
        const val MIN_SARCASM = 1
    }
}
