package com.lavidanoesunbanano.app.di

import android.content.Context
import androidx.room.Room
import com.lavidanoesunbanano.app.data.local.LaVidaDatabase
import com.lavidanoesunbanano.app.data.local.dao.AttemptDao
import com.lavidanoesunbanano.app.data.local.dao.ProfileDao
import com.lavidanoesunbanano.app.data.local.dao.RulesDao
import com.lavidanoesunbanano.app.data.local.dao.TemplateDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LaVidaDatabase =
        Room.databaseBuilder(context, LaVidaDatabase::class.java, "lavida.db")
            // Mientras la app esté en desarrollo el esquema cambia sin migraciones.
            // Antes de usarla en serio se quita esta línea y se escriben migraciones.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideProfileDao(database: LaVidaDatabase): ProfileDao = database.profileDao()

    @Provides
    fun provideRulesDao(database: LaVidaDatabase): RulesDao = database.rulesDao()

    @Provides
    fun provideTemplateDao(database: LaVidaDatabase): TemplateDao = database.templateDao()

    @Provides
    fun provideAttemptDao(database: LaVidaDatabase): AttemptDao = database.attemptDao()
}
