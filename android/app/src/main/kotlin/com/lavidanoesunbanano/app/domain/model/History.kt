package com.lavidanoesunbanano.app.domain.model

import java.time.Instant

/** Cómo terminó un intento. `null` en el registro: se fue sin tocar ningún botón. */
enum class AttemptOutcome(val wire: String) {
    EXIT("exit"),
    EMERGENCY("emergency"),
    EXTRA_TIME("extra_time"),
    ;

    /** Terminó con la app desbloqueada. */
    val unlocked: Boolean get() = this != EXIT

    companion object {
        fun fromWire(value: String?): AttemptOutcome? = entries.firstOrNull { it.wire == value }
    }
}

data class AttemptRecord(
    val id: Long,
    val app: AppInfo,
    val startedAt: Instant,
    val outcome: AttemptOutcome?,
)

data class ExcuseRecord(
    val id: Long,
    val attemptId: Long,
    val app: AppInfo,
    val type: ExcuseType,
    val reason: String,
    val decidedAt: Instant,
    val verdict: Verdict,
    val unlockedUntil: Instant?,
    /** Emergencia aprobada sin juez que el usuario todavía no revisó. */
    val needsReview: Boolean,
)
