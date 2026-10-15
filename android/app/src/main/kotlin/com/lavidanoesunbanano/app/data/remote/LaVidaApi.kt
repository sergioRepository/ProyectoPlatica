package com.lavidanoesunbanano.app.data.remote

import com.lavidanoesunbanano.app.data.remote.dto.HealthDto
import retrofit2.http.GET

interface LaVidaApi {
    @GET("health")
    suspend fun health(): HealthDto
}
