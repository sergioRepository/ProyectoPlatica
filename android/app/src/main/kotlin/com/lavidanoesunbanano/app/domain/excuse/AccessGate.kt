package com.lavidanoesunbanano.app.domain.excuse

import com.lavidanoesunbanano.app.data.remote.dto.DeviceStatusDto

enum class AccessCheck { OK, NO_EVALUATIONS, NO_CREDITS, UNKNOWN }

/** Decide, antes de pedir la razón, si hay con qué pagar la evaluación. El servidor tiene la última palabra. */
object AccessGate {
    fun check(status: DeviceStatusDto?, judged: Boolean): AccessCheck = when {
        // Sin autorización de razones manda la regla local de siempre.
        !judged -> AccessCheck.OK
        status == null -> AccessCheck.UNKNOWN
        status.remainingToday <= 0 -> AccessCheck.NO_EVALUATIONS
        status.includedRemaining <= 0 && status.credits <= 0 -> AccessCheck.NO_CREDITS
        else -> AccessCheck.OK
    }
}
