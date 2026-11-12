package com.lavidanoesunbanano.app.data

import com.lavidanoesunbanano.app.data.prefs.DeviceStore

class FakeDeviceStore(var id: String? = null, var token: String? = null) : DeviceStore {
    var validatedPlan: String? = null
    var agentEnabled: Boolean = false

    override suspend fun validatedPlan(): String? = validatedPlan

    override suspend fun setValidatedPlan(plan: String?) {
        validatedPlan = plan
    }

    override suspend fun agentEnabled(): Boolean = agentEnabled

    override suspend fun setAgentEnabled(enabled: Boolean) {
        agentEnabled = enabled
    }

    override suspend fun deviceId(): String? = id

    override suspend fun setDeviceId(id: String) {
        this.id = id
    }

    override suspend fun token(): String? = token

    override suspend fun setToken(token: String?) {
        this.token = token
    }
}
