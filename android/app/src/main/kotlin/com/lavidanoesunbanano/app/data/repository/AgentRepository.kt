package com.lavidanoesunbanano.app.data.repository

import com.lavidanoesunbanano.app.data.local.dao.FocusDao
import com.lavidanoesunbanano.app.data.local.dao.TemplateDao
import com.lavidanoesunbanano.app.data.local.entity.FocusSessionEntity
import com.lavidanoesunbanano.app.data.remote.BackendConfig
import com.lavidanoesunbanano.app.data.remote.LaVidaApi
import com.lavidanoesunbanano.app.data.remote.dto.AnalyzeRequestDto
import com.lavidanoesunbanano.app.data.remote.dto.AnalyzeResponseDto
import com.lavidanoesunbanano.app.data.remote.dto.BlockedAppDto
import com.lavidanoesunbanano.app.data.remote.dto.ConfirmRequestDto
import com.lavidanoesunbanano.app.data.remote.dto.InterventionDto
import com.lavidanoesunbanano.app.data.remote.dto.UsageSessionDto
import com.lavidanoesunbanano.app.data.remote.dto.UsageSnapshotDto
import com.lavidanoesunbanano.app.data.system.UsageEvidenceSource
import com.lavidanoesunbanano.app.domain.agent.AgentRules
import com.lavidanoesunbanano.app.domain.model.AppInfo
import com.lavidanoesunbanano.app.domain.schedule.ScheduleFormatter
import com.lavidanoesunbanano.app.domain.usage.UsageEvidence
import com.lavidanoesunbanano.app.service.notify.Notifier
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

/** Lo último que pasó con el agente, tal como se muestra en Inicio. */
sealed interface AgentRun {
    /** Sin backend, sin token o sin red: no hubo análisis, y no se finge. */
    data object Unavailable : AgentRun

    /** `applied`: lo que confirmó el teléfono (executed / failed) si llegó una orden. */
    data class Done(val response: AnalyzeResponseDto, val applied: String?) : AgentRun
}

/** Pide un análisis al agente, ejecuta la sesión de enfoque si llega y confirma el resultado. */
@Singleton
class AgentRepository @Inject constructor(
    private val api: LaVidaApi,
    private val backend: BackendConfig,
    private val deviceRepository: DeviceRepository,
    private val profileRepository: ProfileRepository,
    private val templateDao: TemplateDao,
    private val focusDao: FocusDao,
    private val usage: UsageEvidenceSource,
    private val notifier: Notifier,
    private val clock: Clock,
) {
    private val last = MutableStateFlow<AgentRun?>(null)
    val lastRun: StateFlow<AgentRun?> = last.asStateFlow()

    // shortcut: el enfriamiento vive en memoria y se reinicia si Android mata el proceso; el
    // límite por hora del backend acota el gasto. Pasarlo a DataStore si se nota en el uso real.
    @Volatile
    private var lastRunAt: Instant? = null

    /** Hasta cuándo bloquea cada app una sesión de enfoque. Las vencidas las descarta `BlockPolicy`. */
    fun observeFocus(): Flow<Map<String, Instant>> = focusDao.observeSince(0).map { rows ->
        val until = mutableMapOf<String, Instant>()
        for (row in rows) {
            val end = Instant.ofEpochMilli(row.endsAt)
            row.packages.split(',').filter { it.isNotBlank() }.forEach { pkg ->
                if (until[pkg]?.let { it < end } != false) until[pkg] = end
            }
        }
        until
    }

    /** Revisión del worker: solo llama al backend si las reglas locales lo justifican. */
    suspend fun analyzeIfNeeded(): AgentRun? {
        val apps = profileRepository.getBlockedApps()
        val listed = apps.map { it.packageName }.toSet()
        val evidence = usage.collect(listed)
        val focusUntil = focusDao.latestEnd()?.let(Instant::ofEpochMilli)
        if (!AgentRules.shouldAnalyze(evidence, listed, lastRunAt, focusUntil, clock.instant())) return null
        return analyze(TRIGGER_PERIODIC, apps, evidence)
    }

    /** Botón "Analizar ahora". */
    suspend fun analyzeNow(): AgentRun {
        val apps = profileRepository.getBlockedApps()
        return analyze(TRIGGER_MANUAL, apps, usage.collect(apps.map { it.packageName }.toSet()))
    }

    private suspend fun analyze(trigger: String, apps: List<AppInfo>, evidence: UsageEvidence): AgentRun {
        lastRunAt = clock.instant()
        val run = request(trigger, apps, evidence)?.let { body -> call(body, apps) } ?: AgentRun.Unavailable
        last.value = run
        return run
    }

    private suspend fun request(trigger: String, apps: List<AppInfo>, evidence: UsageEvidence): AnalyzeRequestDto? {
        if (!backend.isConfigured) return null
        val now = clock.instant()
        val labels = mutableMapOf<String, Pair<String, String>>()
        apps.forEach { labels[it.packageName] = it.label to "" }
        val focusUntil = focusDao.latestEnd()?.takeIf { it > now.toEpochMilli() }
        return AnalyzeRequestDto(
            requestId = UUID.randomUUID().toString(),
            trigger = trigger,
            localTime = ScheduleFormatter.time(now.atZone(clock.zone).toLocalTime()),
            profileSummary = templateDao.getMeta()?.profileSummary.orEmpty().take(MAX_SUMMARY_CHARS),
            mainGoal = profileRepository.getProfile()?.mainGoal.orEmpty().take(MAX_GOAL_CHARS),
            blockedApps = apps.take(MAX_APPS).map { BlockedAppDto(it.packageName, it.label.take(MAX_LABEL_CHARS)) },
            focusActiveUntilMs = focusUntil,
            usage = UsageSnapshotDto(
                status = evidence.status.wire,
                collectedAtMs = evidence.collectedAtMillis,
                truncated = evidence.truncated,
                sessions = evidence.sessions.map { s ->
                    val (label, category) = labels.getOrPut(s.packageName) { usage.describe(s.packageName) }
                    UsageSessionDto(s.packageName, label.take(MAX_LABEL_CHARS), category, s.startMillis, s.minutes)
                },
            ),
        )
    }

    private suspend fun call(body: AnalyzeRequestDto, apps: List<AppInfo>): AgentRun? {
        val authorization = deviceRepository.authorization() ?: return null
        val response = try {
            withTimeoutOrNull(TIMEOUT_MS) { api.analyze(authorization, body) } ?: return null
        } catch (e: HttpException) {
            if (e.code() == 401) deviceRepository.forgetToken()
            return null
        } catch (e: IOException) {
            return null
        } catch (e: SerializationException) {
            return null
        }
        val order = response.intervention?.takeIf { it.status == "sent" && it.id != null }
        val applied = order?.let { apply(it, response.reason, apps, authorization) }
        return AgentRun.Done(response, applied)
    }

    /** Aplica la sesión y le dice al backend qué pasó de verdad. */
    private suspend fun apply(order: InterventionDto, reason: String, apps: List<AppInfo>, authorization: String): String {
        val id = order.id ?: return FAILED
        val listed = apps.map { it.packageName }.toSet()
        val status = if (AgentRules.acceptsOrder(order.durationMinutes, order.targetApps, listed)) {
            val start = clock.instant()
            val end = start.plusSeconds(order.durationMinutes * 60L)
            focusDao.insert(FocusSessionEntity(id, order.targetApps.joinToString(","), start.toEpochMilli(), end.toEpochMilli(), reason))
            val labels = apps.filter { it.packageName in order.targetApps }.map { it.label }
            notifier.showFocus(labels, end, reason)
            EXECUTED
        } else {
            FAILED
        }
        try {
            api.confirmIntervention(authorization, id, ConfirmRequestDto(status, if (status == FAILED) "invalid_order" else null))
        } catch (e: IOException) {
            // Sin confirmación el backend la verá como "unknown": es la verdad desde su lado.
        } catch (e: HttpException) {
            // Igual que arriba.
        }
        return status
    }

    private companion object {
        const val TRIGGER_PERIODIC = "periodic"
        const val TRIGGER_MANUAL = "manual"
        const val EXECUTED = "executed"
        const val FAILED = "failed"

        // Hasta 3 turnos de Grok caben en los 20 s del backend; este límite no toca el bloqueo.
        const val TIMEOUT_MS = 25_000L

        // Límites del backend (banano/schemas/agent.py): recortar aquí evita un 422.
        const val MAX_SUMMARY_CHARS = 1500
        const val MAX_GOAL_CHARS = 200
        const val MAX_LABEL_CHARS = 80
        const val MAX_APPS = 50
    }
}
