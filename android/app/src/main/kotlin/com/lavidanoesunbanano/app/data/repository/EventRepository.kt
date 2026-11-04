package com.lavidanoesunbanano.app.data.repository

import com.lavidanoesunbanano.app.data.local.dao.EventDao
import com.lavidanoesunbanano.app.data.local.entity.PendingEventEntity
import com.lavidanoesunbanano.app.data.prefs.SettingsStore
import com.lavidanoesunbanano.app.data.remote.LaVidaApi
import com.lavidanoesunbanano.app.data.remote.dto.AppEventDto
import com.lavidanoesunbanano.app.data.remote.dto.EventBatchDto
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import retrofit2.HttpException

/** Nombres que acepta POST /events. */
object AppEvents {
    const val BLOCK_SHOWN = "block_shown"
    const val BLOCK_EXIT = "block_exit"
    const val EXTRA_TIME_REQUESTED = "extra_time_requested"
    const val EMERGENCY_REQUESTED = "emergency_requested"
    const val QUOTA_REACHED = "quota_reached"
    const val FOCUS_SESSION_STARTED = "focus_session_started"
    const val FOCUS_SESSION_COMPLETED = "focus_session_completed"
    const val PLAN_SELECTED = "plan_selected"
    const val SABOTAGE_ATTEMPT = "sabotage_attempt"
}

/** Cola local de analítica: se llena solo con la autorización y se vacía por lotes con red. */
@Singleton
class EventRepository @Inject constructor(
    private val dao: EventDao,
    private val settings: SettingsStore,
    private val deviceRepository: DeviceRepository,
    private val api: LaVidaApi,
    private val clock: Clock,
) {
    suspend fun track(
        name: String,
        app: String? = null,
        vararg props: Pair<String, JsonPrimitive>,
        at: Instant = clock.instant(),
    ) {
        if (settings.consents.first()?.analytics != true) return
        dao.insert(
            PendingEventEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                app = app,
                occurredAt = at.toEpochMilli(),
                props = JsonObject(props.toMap()).toString(),
            ),
        )
    }

    suspend fun clear() = dao.clear()

    /** Envía lo pendiente. `false` si hay que reintentar más tarde. */
    suspend fun flush(): Boolean {
        if (settings.consents.first()?.analytics != true) {
            dao.clear()
            return true
        }
        val authorization = deviceRepository.authorization() ?: return false
        while (true) {
            val batch = dao.due(clock.millis(), MAX_BATCH)
            if (batch.isEmpty()) return true
            try {
                api.sendEvents(authorization, EventBatchDto(batch.map { it.toDto() }))
            } catch (e: HttpException) {
                if (e.code() == 401) deviceRepository.forgetToken()
                // El servidor rechazó el lote por su contenido: reintentarlo no lo va a arreglar.
                if (e.code() != 422) return false
            } catch (e: IOException) {
                return false
            } catch (e: SerializationException) {
                return false
            }
            dao.delete(batch.map { it.id })
        }
    }

    private fun PendingEventEntity.toDto() = AppEventDto(
        id = id,
        name = name,
        app = app,
        occurredAt = Instant.ofEpochMilli(occurredAt).toString(),
        props = Json.parseToJsonElement(props).jsonObject,
    )

    private companion object {
        /** El tope de POST /events. */
        const val MAX_BATCH = 100
    }
}
