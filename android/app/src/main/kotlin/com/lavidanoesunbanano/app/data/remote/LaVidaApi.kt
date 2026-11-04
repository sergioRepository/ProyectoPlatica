package com.lavidanoesunbanano.app.data.remote

import com.lavidanoesunbanano.app.data.remote.dto.AnalyzeRequestDto
import com.lavidanoesunbanano.app.data.remote.dto.AnalyzeResponseDto
import com.lavidanoesunbanano.app.data.remote.dto.ConfirmRequestDto
import com.lavidanoesunbanano.app.data.remote.dto.ConfirmResponseDto
import com.lavidanoesunbanano.app.data.remote.dto.ConsentsDto
import com.lavidanoesunbanano.app.data.remote.dto.DeviceStatusDto
import com.lavidanoesunbanano.app.data.remote.dto.EventBatchDto
import com.lavidanoesunbanano.app.data.remote.dto.EventsAcceptedDto
import com.lavidanoesunbanano.app.data.remote.dto.HealthDto
import com.lavidanoesunbanano.app.data.remote.dto.JudgeRequestDto
import com.lavidanoesunbanano.app.data.remote.dto.JudgeResponseDto
import com.lavidanoesunbanano.app.data.remote.dto.PlanRequestDto
import com.lavidanoesunbanano.app.data.remote.dto.ProfileDto
import com.lavidanoesunbanano.app.data.remote.dto.RegisterRequestDto
import com.lavidanoesunbanano.app.data.remote.dto.RegisterResponseDto
import com.lavidanoesunbanano.app.data.remote.dto.TemplateResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface LaVidaApi {
    @GET("health")
    suspend fun health(): HealthDto

    @POST("devices/register")
    suspend fun register(@Body body: RegisterRequestDto): RegisterResponseDto

    @PUT("devices/me/consents")
    suspend fun updateConsents(
        @Header("Authorization") authorization: String,
        @Body body: ConsentsDto,
    ): ConsentsDto

    @GET("devices/me")
    suspend fun deviceStatus(@Header("Authorization") authorization: String): DeviceStatusDto

    @PUT("devices/me/plan")
    suspend fun updatePlan(
        @Header("Authorization") authorization: String,
        @Body body: PlanRequestDto,
    ): DeviceStatusDto

    @POST("events")
    suspend fun sendEvents(
        @Header("Authorization") authorization: String,
        @Body body: EventBatchDto,
    ): EventsAcceptedDto

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
