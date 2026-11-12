package com.lavidanoesunbanano.app.di

import com.lavidanoesunbanano.app.data.billing.BillingRepository
import com.lavidanoesunbanano.app.data.billing.PlayBillingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {
    @Binds
    abstract fun bindBilling(repository: PlayBillingRepository): BillingRepository
}
