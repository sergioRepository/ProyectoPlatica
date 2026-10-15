package com.lavidanoesunbanano.app.domain.model

sealed interface BackendStatus {
    data object NotConfigured : BackendStatus
    data object Unreachable : BackendStatus
    data class Online(val version: String) : BackendStatus
    data class HttpError(val code: Int) : BackendStatus
}
