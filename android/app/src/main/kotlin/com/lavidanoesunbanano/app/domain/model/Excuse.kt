package com.lavidanoesunbanano.app.domain.model

/** Los dos botones que piden permiso. `wire` es el valor del spec en la API y en Room. */
enum class ExcuseType(val wire: String) {
    EMERGENCY("emergency"),
    EXTRA_TIME("extra_time"),
    ;

    companion object {
        fun fromWire(value: String): ExcuseType = entries.firstOrNull { it.wire == value } ?: EXTRA_TIME
    }
}

/** `reason_code` del juez. `OFFLINE` es local: el veredicto salió de la regla sin conexión. */
enum class VerdictReason(val wire: String) {
    LEGIT_WORK("legit_work"),
    LEGIT_EMERGENCY("legit_emergency"),
    BOREDOM("boredom"),
    REPEATED("repeated"),
    OVER_QUOTA("over_quota"),
    CRISIS("crisis"),
    TOO_SHORT("too_short"),
    OFFLINE("offline"),
    ;

    companion object {
        fun fromWire(value: String): VerdictReason = entries.firstOrNull { it.wire == value } ?: BOREDOM
    }
}

data class Verdict(
    val valid: Boolean,
    val grantedMinutes: Int,
    /** `null`: la pantalla usa una frase local (por ejemplo, emergencia aprobada sin conexión). */
    val message: String?,
    val reason: VerdictReason,
    /** Seguridad del juez; `null` sin conexión. */
    val confidence: Double?,
    val offline: Boolean,
)

/** Cupos por día (todas las apps juntas) y duración de cada desbloqueo, según el spec. */
data class UnlockLimits(
    val extraTimeDaily: Int = 3,
    val emergencyDaily: Int = 2,
    val extraTimeMinutes: Int = 10,
    val emergencyMinutes: Int = 15,
) {
    fun minutesFor(type: ExcuseType): Int = when (type) {
        ExcuseType.EMERGENCY -> emergencyMinutes
        ExcuseType.EXTRA_TIME -> extraTimeMinutes
    }
}
