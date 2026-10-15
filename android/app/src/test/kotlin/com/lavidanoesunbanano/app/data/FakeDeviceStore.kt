package com.lavidanoesunbanano.app.data

import com.lavidanoesunbanano.app.data.prefs.DeviceStore

class FakeDeviceStore(var id: String? = null, var token: String? = null) : DeviceStore {
    override suspend fun deviceId(): String? = id

    override suspend fun setDeviceId(id: String) {
        this.id = id
    }

    override suspend fun token(): String? = token

    override suspend fun setToken(token: String?) {
        this.token = token
    }
}
