package com.lavidanoesunbanano.app.data.remote

import com.lavidanoesunbanano.app.data.remote.dto.AnalyzeRequestDto
import com.lavidanoesunbanano.app.data.remote.dto.AnalyzeResponseDto
import com.lavidanoesunbanano.app.data.remote.dto.ConfirmRequestDto
import com.lavidanoesunbanano.app.data.remote.dto.ConfirmResponseDto
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
import retrofit2.http.Path

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

    @POST("agent/analyze")
    suspend fun analyze(
        @Header("Authorization") authorization: String,
        @Body body: AnalyzeRequestDto,
    ): AnalyzeResponseDto

    @POST("agent/interventions/{id}/result")
    suspend fun confirmIntervention(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Body body: ConfirmRequestDto,
    ): ConfirmResponseDto
}
