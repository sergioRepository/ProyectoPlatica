package com.lavidanoesunbanano.app.feature.block

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.domain.model.Verdict
import com.lavidanoesunbanano.app.domain.model.VerdictReason

/** La frase del veredicto, o la local cuando no hubo juez y la plantilla no tenía una. */
@Composable
internal fun verdictMessage(verdict: Verdict): String = verdict.message ?: when {
    // La misma para cualquier crisis: sin servidor no se sabe si es una autolesión.
    verdict.reason == VerdictReason.CRISIS -> stringResource(R.string.verdict_crisis, verdict.grantedMinutes)
    verdict.valid -> stringResource(R.string.verdict_offline_emergency, verdict.grantedMinutes)
    else -> stringResource(R.string.verdict_offline_rejected)
}

/** `✓ APROBADA · 10 min` o `✕ RECHAZADA · razón repetida`. `shared`: la razón podía ir al juez. */
@Composable
internal fun verdictLabel(verdict: Verdict, shared: Boolean = true): String = when {
    verdict.valid && verdict.offline && !shared -> stringResource(R.string.verdict_approved_rule, verdict.grantedMinutes)
    verdict.valid && verdict.offline -> stringResource(R.string.verdict_approved_offline, verdict.grantedMinutes)
    verdict.valid -> stringResource(R.string.verdict_approved, verdict.grantedMinutes)
    else -> stringResource(
        R.string.verdict_rejected,
        stringResource(
            when (verdict.reason) {
                VerdictReason.REPEATED -> R.string.verdict_reason_repeated
                VerdictReason.OVER_QUOTA -> R.string.verdict_reason_over_quota
                VerdictReason.TOO_SHORT -> R.string.verdict_reason_too_short
                VerdictReason.OFFLINE -> if (shared) R.string.verdict_reason_offline else R.string.verdict_reason_rule
                VerdictReason.PLAN_QUOTA -> R.string.verdict_reason_plan_quota
                VerdictReason.REJECTION_CAP -> R.string.verdict_reason_rejection_cap
                else -> R.string.verdict_reason_boredom
            },
        ),
    )
}

/** Qué explica un rechazo, en una frase. */
@Composable
internal fun verdictTip(reason: VerdictReason, shared: Boolean): String = stringResource(
    when (reason) {
        VerdictReason.REPEATED -> R.string.verdict_tip_repeated
        VerdictReason.OVER_QUOTA -> R.string.verdict_tip_over_quota
        VerdictReason.TOO_SHORT -> R.string.verdict_tip_too_short
        VerdictReason.OFFLINE -> if (shared) R.string.verdict_tip_offline else R.string.verdict_tip_rule
        VerdictReason.PLAN_QUOTA -> R.string.verdict_tip_plan_quota
        VerdictReason.REJECTION_CAP -> R.string.verdict_tip_rejection_cap
        else -> R.string.verdict_tip_boredom
    },
)
