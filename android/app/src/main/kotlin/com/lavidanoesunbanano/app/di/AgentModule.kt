package com.lavidanoesunbanano.app.di

import com.lavidanoesunbanano.app.service.agent.AgentSchedule
import com.lavidanoesunbanano.app.service.agent.WorkManagerAgentSchedule
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AgentModule {
    @Binds
    abstract fun bindAgentSchedule(schedule: WorkManagerAgentSchedule): AgentSchedule
}
