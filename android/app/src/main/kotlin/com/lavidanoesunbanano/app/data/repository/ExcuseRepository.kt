package com.lavidanoesunbanano.app.data.repository

import androidx.room.withTransaction
import com.lavidanoesunbanano.app.data.local.LaVidaDatabase
import com.lavidanoesunbanano.app.data.local.dao.AttemptDao
import com.lavidanoesunbanano.app.data.local.dao.ExcuseDao
import com.lavidanoesunbanano.app.data.local.dao.ProfileDao
import com.lavidanoesunbanano.app.data.local.dao.TemplateDao
import com.lavidanoesunbanano.app.data.local.entity.ExcuseEntity
import com.lavidanoesunbanano.app.data.local.entity.VerdictLineEntity
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

/** Cuánto se espera al juez antes de aplicar la regla sin conexión, y los cupos del día. */
data class JudgeConfig(
    val timeoutMillis: Long = 8_000,
    val limits: UnlockLimits = UnlockLimits(),
)

data class ExcuseResult(
    val verdict: Verdict,
    /** Hasta cuándo queda desbloqueada la app; `null` si se rechazó. */
    val unlockedUntil: Instant?,
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
    private val clock: Clock,
) {
    /** Último desbloqueo de cada app. Los vencidos los descarta `BlockPolicy`. */
    fun observeUnlocks(): Flow<Map<String, Instant>> = excuseDao.observeUnlocks().map { rows ->
        rows.associate { it.packageName to Instant.ofEpochMilli(it.unlockedUntil) }
    }

    suspend fun judge(session: BlockSession, type: ExcuseType, reason: String): ExcuseResult {
        val text = reason.trim().take(MAX_REASON_CHARS)
        val verdict = askJudge(request(session, type, text))
            ?: OfflineVerdict.decide(
                type,
                config.limits,
                templateDao.getVerdictLines(VerdictLineEntity.REJECTED).map { it.text },
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
        return ExcuseResult(verdict, unlockedUntil)
    }

    private suspend fun request(session: BlockSession, type: ExcuseType, reason: String): JudgeRequestDto {
        val now = clock.instant().atZone(clock.zone)
        val dayStart = now.toLocalDate().atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val profile = profileDao.get()
        val limits = config.limits
        return JudgeRequestDto(
            type = type.wire,
            reason = reason,
            app = session.app.label.take(MAX_APP_CHARS),
            profileSummary = templateDao.getMeta()?.profileSummary.orEmpty().take(MAX_SUMMARY_CHARS),
            attemptsToday = session.attemptNumber,
            extraTimeGrantedToday = excuseDao.countGrantedSince(ExcuseType.EXTRA_TIME.wire, dayStart),
            recentExcuses = excuseDao.recentReasons(RECENT_EXCUSES),
            localTime = ScheduleFormatter.time(now.toLocalTime()),
            emergenciesGrantedToday = excuseDao.countGrantedSince(ExcuseType.EMERGENCY.wire, dayStart),
            extraTimeDailyLimit = limits.extraTimeDaily,
            emergencyDailyLimit = limits.emergencyDaily,
            sarcasmLevel = profile?.sarcasmLevel ?: DEFAULT_SARCASM,
            profanityAllowed = profile?.profanityAllowed ?: false,
            country = profile?.country.orEmpty().take(MAX_COUNTRY_CHARS),
        )
    }

    /** `null` si el juez no contestó a tiempo o falló: se aplica la regla sin conexión. */
    private suspend fun askJudge(request: JudgeRequestDto): Verdict? {
        if (!backend.isConfigured) return null
        val authorization = deviceRepository.authorization() ?: return null
        return try {
            withTimeoutOrNull(config.timeoutMillis) { api.judgeExcuse(authorization, request) }
                ?.toVerdict(ExcuseType.fromWire(request.type))
        } catch (e: HttpException) {
            if (e.code() == 401) deviceRepository.forgetToken()
            null
        } catch (e: IOException) {
            null
        } catch (e: SerializationException) {
            null
        }
    }

    private fun JudgeResponseDto.toVerdict(type: ExcuseType) = Verdict(
        valid = valid,
        grantedMinutes = when {
            !valid -> 0
            grantedMinutes > 0 -> grantedMinutes
            else -> config.limits.minutesFor(type)
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
    }
}
