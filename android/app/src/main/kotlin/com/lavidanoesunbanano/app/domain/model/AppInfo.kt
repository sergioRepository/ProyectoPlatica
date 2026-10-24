package com.lavidanoesunbanano.app.domain.model

/** Una app instalada o bloqueada: el paquete identifica, la etiqueta se muestra. */
data class AppInfo(
    val packageName: String,
    val label: String,
)
