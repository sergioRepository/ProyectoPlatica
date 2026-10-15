package com.lavidanoesunbanano.app.data.remote

import com.lavidanoesunbanano.app.data.remote.dto.HealthDto
import com.lavidanoesunbanano.app.data.remote.dto.JudgeRequestDto
import com.lavidanoesunbanano.app.data.remote.dto.JudgeResponseDto
import com.lavidanoesunbanano.app.data.remote.dto.ProfileDto
import com.lavidanoesunbanano.app.data.remote.dto.RegisterRequestDto
import com.lavidanoesunbanano.app.data.remote.dto.RegisterResponseDto
import com.lavidanoesunbanano.app.data.remote.dto.TemplateResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface LaVidaApi {
    @GET("health")
    suspend fun health(): HealthDto

    @POST("devices/register")
    suspend fun register(@Body body: RegisterRequestDto): RegisterResponseDto

    @POST("templates/generate")
    suspend fun generateTemplate(
        @Header("Authorization") authorization: String,
        @Body profile: ProfileDto,
    ): TemplateResponseDto

    @POST("excuses/judge")
    suspend fun judgeExcuse(
        @Header("Authorization") authorization: String,
        @Body body: JudgeRequestDto,
    ): JudgeResponseDto
}
