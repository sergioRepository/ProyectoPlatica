package com.lavidanoesunbanano.app.data.repository

import com.lavidanoesunbanano.app.data.local.dao.TemplateDao
import com.lavidanoesunbanano.app.data.mapper.toDto
import com.lavidanoesunbanano.app.data.mapper.toRows
import com.lavidanoesunbanano.app.data.remote.BackendConfig
import com.lavidanoesunbanano.app.data.remote.LaVidaApi
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

sealed interface GenerationResult {
    data class Success(val messageCount: Int) : GenerationResult
    data object NeedsRegistration : GenerationResult
    data object MissingProfile : GenerationResult
    data object NotConfigured : GenerationResult
    data object Unreachable : GenerationResult
    data class ServerError(val code: Int?) : GenerationResult
}

/** La plantilla vive en Room: se genera con el backend una vez y después funciona sin red. */
@Singleton
class TemplateRepository @Inject constructor(
    private val templateDao: TemplateDao,
    private val profileRepository: ProfileRepository,
    private val deviceRepository: DeviceRepository,
    private val api: LaVidaApi,
    private val config: BackendConfig,
    private val clock: Clock,
) {
    fun observeMessageCount(): Flow<Int> = templateDao.observeMessageCount()

    suspend fun messageCount(): Int = templateDao.messageCount()

    suspend fun messages(): List<String> = templateDao.getMessages().map { it.text }

    suspend fun generate(): GenerationResult {
        if (!config.isConfigured) return GenerationResult.NotConfigured
        val profile = profileRepository.getProfile() ?: return GenerationResult.MissingProfile
        val authorization = deviceRepository.authorization() ?: return GenerationResult.NeedsRegistration
        val body = profile.toDto(profileRepository.getBlockedApps(), profileRepository.getRanges())
        return try {
            val rows = api.generateTemplate(authorization, body).toRows(clock.millis())
            templateDao.replaceTemplate(rows.messages, rows.verdictLines, rows.meta)
            GenerationResult.Success(rows.messages.size)
        } catch (e: HttpException) {
            if (e.code() == 401) {
                deviceRepository.forgetToken()
                GenerationResult.NeedsRegistration
            } else {
                GenerationResult.ServerError(e.code())
            }
        } catch (e: IOException) {
            GenerationResult.Unreachable
        } catch (e: SerializationException) {
            GenerationResult.ServerError(null)
        }
    }
}
