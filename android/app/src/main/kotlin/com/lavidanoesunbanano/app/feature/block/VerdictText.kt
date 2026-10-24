package com.lavidanoesunbanano.app.feature.block

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.domain.model.Verdict
import com.lavidanoesunbanano.app.domain.model.VerdictReason

/** La frase del veredicto, o la local cuando no hubo juez y la plantilla no tenía una. */
@Composable
internal fun verdictMessage(verdict: Verdict): String = verdict.message ?: if (verdict.valid) {
    stringResource(R.string.verdict_offline_emergency, verdict.grantedMinutes)
} else {
    stringResource(R.string.verdict_offline_rejected)
}

/** `APROBADO · 10 min` o `RECHAZADO · excusa repetida`. */
@Composable
internal fun verdictLabel(verdict: Verdict): String = when {
    verdict.valid && verdict.offline -> stringResource(R.string.verdict_approved_offline, verdict.grantedMinutes)
    verdict.valid -> stringResource(R.string.verdict_approved, verdict.grantedMinutes)
    else -> stringResource(
        R.string.verdict_rejected,
        stringResource(
            when (verdict.reason) {
                VerdictReason.REPEATED -> R.string.verdict_reason_repeated
                VerdictReason.OVER_QUOTA -> R.string.verdict_reason_over_quota
                VerdictReason.TOO_SHORT -> R.string.verdict_reason_too_short
                VerdictReason.OFFLINE -> R.string.verdict_reason_offline
                else -> R.string.verdict_reason_boredom
            },
        ),
    )
}

@Composable
internal fun verdictColor(verdict: Verdict): Color =
    if (verdict.valid) LaVidaTheme.colors.approved else LaVidaTheme.colors.rejected
