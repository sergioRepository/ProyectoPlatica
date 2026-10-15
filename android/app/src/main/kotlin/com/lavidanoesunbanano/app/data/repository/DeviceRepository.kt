package com.lavidanoesunbanano.app.data.repository

import com.lavidanoesunbanano.app.data.prefs.DeviceStore
import com.lavidanoesunbanano.app.data.remote.BackendConfig
import com.lavidanoesunbanano.app.data.remote.LaVidaApi
import com.lavidanoesunbanano.app.data.remote.dto.RegisterRequestDto
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

sealed interface RegistrationResult {
    data object Success : RegistrationResult
    data object WrongCode : RegistrationResult
    data object NotConfigured : RegistrationResult
    data object Unreachable : RegistrationResult
    data class ServerError(val code: Int?) : RegistrationResult
}

/**
 * El teléfono genera su id en el primer arranque. El backend lo firma al registrarlo con el
 * código de registro, y desde ahí cada petición lleva ese token.
 */
@Singleton
class DeviceRepository @Inject constructor(
    private val store: DeviceStore,
    private val api: LaVidaApi,
    private val config: BackendConfig,
) {
    suspend fun ensureDeviceId(): String =
        store.deviceId() ?: UUID.randomUUID().toString().replace("-", "").also { store.setDeviceId(it) }

    suspend fun authorization(): String? = store.token()?.let { "Bearer $it" }

    suspend fun forgetToken() = store.setToken(null)

    suspend fun register(enrollmentCode: String): RegistrationResult {
        if (!config.isConfigured) return RegistrationResult.NotConfigured
        return try {
            val response = api.register(RegisterRequestDto(ensureDeviceId(), enrollmentCode.trim()))
            store.setToken(response.deviceToken)
            RegistrationResult.Success
        } catch (e: HttpException) {
            when (e.code()) {
                403 -> RegistrationResult.WrongCode
                503 -> RegistrationResult.NotConfigured
                else -> RegistrationResult.ServerError(e.code())
            }
        } catch (e: IOException) {
            RegistrationResult.Unreachable
        } catch (e: SerializationException) {
            RegistrationResult.ServerError(null)
        }
    }
}
