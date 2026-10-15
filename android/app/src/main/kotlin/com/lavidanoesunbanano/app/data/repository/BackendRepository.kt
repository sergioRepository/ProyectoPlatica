package com.lavidanoesunbanano.app.data.repository

import com.lavidanoesunbanano.app.data.remote.BackendConfig
import com.lavidanoesunbanano.app.data.remote.LaVidaApi
import com.lavidanoesunbanano.app.domain.model.BackendStatus
import java.io.IOException
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

class BackendRepository @Inject constructor(
    private val api: LaVidaApi,
    private val config: BackendConfig,
) {
    suspend fun status(): BackendStatus {
        if (!config.isConfigured) return BackendStatus.NotConfigured
        return try {
            BackendStatus.Online(api.health().version)
        } catch (e: HttpException) {
            BackendStatus.HttpError(e.code())
        } catch (e: IOException) {
            BackendStatus.Unreachable
        } catch (e: SerializationException) {
            BackendStatus.Unreachable
        }
    }
}
