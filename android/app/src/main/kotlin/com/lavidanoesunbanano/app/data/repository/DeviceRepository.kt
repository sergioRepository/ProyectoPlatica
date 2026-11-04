package com.lavidanoesunbanano.app.data.repository

import com.lavidanoesunbanano.app.data.prefs.DeviceStore
import com.lavidanoesunbanano.app.data.prefs.SettingsStore
import com.lavidanoesunbanano.app.data.remote.BackendConfig
import com.lavidanoesunbanano.app.data.remote.LaVidaApi
import com.lavidanoesunbanano.app.data.remote.dto.ConsentsDto
import com.lavidanoesunbanano.app.data.remote.dto.DeviceStatusDto
import com.lavidanoesunbanano.app.data.remote.dto.PlanRequestDto
import com.lavidanoesunbanano.app.data.remote.dto.RegisterRequestDto
import com.lavidanoesunbanano.app.data.remote.dto.ReportRequestDto
import com.lavidanoesunbanano.app.domain.model.Consents
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

sealed interface RegistrationResult {
    data object Success : RegistrationResult

    /** El servidor cerró el registro con ENROLLMENT_CODE. */
    data object Closed : RegistrationResult
    data object NeedsConsent : RegistrationResult
    data object NotConfigured : RegistrationResult
    data object Unreachable : RegistrationResult
    data class ServerError(val code: Int?) : RegistrationResult
}

/**
 * El teléfono se registra solo con las autorizaciones que dio el usuario. El backend genera
 * el id y lo firma, y desde ahí cada petición lleva ese token.
 */
@Singleton
class DeviceRepository @Inject constructor(
    private val store: DeviceStore,
    private val api: LaVidaApi,
    private val config: BackendConfig,
    private val settings: SettingsStore,
) {
    suspend fun authorization(): String? = store.token()?.let { "Bearer $it" }

    suspend fun forgetToken() = store.setToken(null)

    suspend fun register(): RegistrationResult {
        if (!config.isConfigured) return RegistrationResult.NotConfigured
        val consents = settings.consents.first() ?: return RegistrationResult.NeedsConsent
        return try {
            val response = api.register(RegisterRequestDto(consents.toDto()))
            store.setDeviceId(response.deviceId)
            store.setToken(response.deviceToken)
            settings.markConsentsSynced()
            RegistrationResult.Success
        } catch (e: HttpException) {
            when (e.code()) {
                403 -> RegistrationResult.Closed
                503 -> RegistrationResult.NotConfigured
                else -> RegistrationResult.ServerError(e.code())
            }
        } catch (e: IOException) {
            RegistrationResult.Unreachable
        } catch (e: SerializationException) {
            RegistrationResult.ServerError(null)
        }
    }

    /**
     * Deja al servidor al día: registra el teléfono si perdió el token (por ejemplo, si cambió
     * el secreto del servidor) y le envía las autorizaciones pendientes. `false` si no pudo.
     */
    suspend fun sync(): Boolean {
        val consents = settings.consents.first() ?: return false
        if (authorization() == null) return register() == RegistrationResult.Success
        if (settings.consentsSynced.first()) return true
        val saved = authorized { api.updateConsents(it, consents.toDto()) } != null
        if (saved) settings.markConsentsSynced()
        return saved
    }

    /** Plan y llamadas de hoy; `null` sin red o sin registro. */
    suspend fun status(): DeviceStatusDto? = authorized { api.deviceStatus(it) }

    suspend fun setPlan(plan: String, wouldPay: String?): DeviceStatusDto? =
        authorized { api.updatePlan(it, PlanRequestDto(plan, wouldPay)) }

    /** Reporta un mensaje de la IA. `false` si no llegó, para que la pantalla lo diga. */
    suspend fun report(message: String, reason: String, source: String): Boolean =
        authorized { api.report(it, ReportRequestDto(message.take(MAX_REPORT_CHARS), reason, source)).isSuccessful } == true

    /** Borra todo lo del dispositivo en el servidor. `false` si no se pudo. */
    suspend fun deleteRemoteData(): Boolean {
        val deleted = authorized { api.deleteDevice(it).isSuccessful } == true
        if (deleted) store.setToken(null)
        return deleted
    }

    private suspend fun <T : Any> authorized(call: suspend (String) -> T): T? {
        val authorization = authorization() ?: return null
        return try {
            call(authorization)
        } catch (e: HttpException) {
            if (e.code() == 401) forgetToken()
            null
        } catch (e: IOException) {
            null
        } catch (e: SerializationException) {
            null
        }
    }

    private fun Consents.toDto() = ConsentsDto(excuses, phoneUsage, crossBorder, analytics, version)

    private companion object {
        /** Límite del backend (banano/schemas/reports.py). */
        const val MAX_REPORT_CHARS = 500
    }
}
