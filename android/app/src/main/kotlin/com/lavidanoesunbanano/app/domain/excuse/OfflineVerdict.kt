package com.lavidanoesunbanano.app.domain.excuse

import com.lavidanoesunbanano.app.domain.model.ExcuseType
import com.lavidanoesunbanano.app.domain.model.UnlockLimits
import com.lavidanoesunbanano.app.domain.model.Verdict
import com.lavidanoesunbanano.app.domain.model.VerdictReason
import kotlin.random.Random

/**
 * Regla del spec cuando el juez no contesta (sin red, más de 8 s o error del servidor):
 * una emergencia se aprueba siempre y queda para revisar; "10 minutos" se rechaza con una
 * frase de la plantilla.
 */
object OfflineVerdict {

    fun decide(
        type: ExcuseType,
        limits: UnlockLimits,
        rejectedLines: List<String>,
        random: Random = Random.Default,
    ): Verdict = when (type) {
        ExcuseType.EMERGENCY -> Verdict(
            valid = true,
            grantedMinutes = limits.emergencyMinutes,
            message = null,
            reason = VerdictReason.OFFLINE,
            confidence = null,
            offline = true,
        )
        ExcuseType.EXTRA_TIME -> Verdict(
            valid = false,
            grantedMinutes = 0,
            message = rejectedLines.randomOrNull(random),
            reason = VerdictReason.OFFLINE,
            confidence = null,
            offline = true,
        )
    }

    /** Las emergencias aprobadas sin juez, por falta de red o de cupo, quedan por revisar. */
    fun needsReview(type: ExcuseType, verdict: Verdict): Boolean {
        val noJudge = verdict.offline || verdict.reason in QUOTA_REASONS
        return noJudge && verdict.valid && type == ExcuseType.EMERGENCY
    }

    private val QUOTA_REASONS = setOf(VerdictReason.PLAN_QUOTA, VerdictReason.REJECTION_CAP)
}
