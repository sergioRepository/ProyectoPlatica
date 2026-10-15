package com.lavidanoesunbanano.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.lavidanoesunbanano.app.data.prefs.DataStoreDeviceStore
import com.lavidanoesunbanano.app.data.prefs.DataStoreSettingsStore
import com.lavidanoesunbanano.app.data.prefs.DeviceStore
import com.lavidanoesunbanano.app.data.prefs.SettingsStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun providePreferences(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("lavida") }

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageBindings {
    @Binds
    abstract fun bindDeviceStore(store: DataStoreDeviceStore): DeviceStore

    @Binds
    abstract fun bindSettingsStore(store: DataStoreSettingsStore): SettingsStore
}
