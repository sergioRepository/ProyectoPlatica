package com.lavidanoesunbanano.app.data.remote

/** URL base del backend. Vacía significa que esta compilación no tiene backend configurado. */
data class BackendConfig(val baseUrl: String) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank()
}
