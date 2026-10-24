package com.lavidanoesunbanano.app.di

import com.lavidanoesunbanano.app.BuildConfig
import com.lavidanoesunbanano.app.data.remote.BackendConfig
import com.lavidanoesunbanano.app.data.remote.LaVidaApi
import com.lavidanoesunbanano.app.data.repository.JudgeConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // Retrofit exige una URL válida aunque no haya backend; BackendRepository no llama en ese caso.
    private const val UNCONFIGURED_BASE_URL = "http://localhost/"

    @Provides
    @Singleton
    fun provideBackendConfig(): BackendConfig = BackendConfig(BuildConfig.API_BASE_URL)

    @Provides
    @Singleton
    fun provideJudgeConfig(): JudgeConfig = JudgeConfig()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        // Generar la plantilla puede tardar minutos (Vercel corta a los 300 s). Los usos
        // rápidos, como el juez, ponen su propio límite con withTimeout.
        .readTimeout(300, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            }
        }
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json, config: BackendConfig): Retrofit =
        Retrofit.Builder()
            .baseUrl(config.baseUrl.ifBlank { UNCONFIGURED_BASE_URL })
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideLaVidaApi(retrofit: Retrofit): LaVidaApi = retrofit.create()
}
