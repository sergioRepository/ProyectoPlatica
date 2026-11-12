package com.lavidanoesunbanano.app.domain

import com.lavidanoesunbanano.app.data.remote.dto.DeviceStatusDto
import com.lavidanoesunbanano.app.domain.excuse.AccessCheck
import com.lavidanoesunbanano.app.domain.excuse.AccessGate
import kotlin.test.Test
import kotlin.test.assertEquals

class AccessGateTest {

    private fun status(remaining: Int = 5, included: Int = 3, credits: Int = 0) = DeviceStatusDto(
        plan = "free",
        dailyLimit = 5,
        remainingToday = remaining,
        credits = credits,
        includedRemaining = included,
    )

    @Test
    fun `without consent the local rule decides`() =
        assertEquals(AccessCheck.OK, AccessGate.check(null, judged = false))

    @Test
    fun `without network it is unknown`() =
        assertEquals(AccessCheck.UNKNOWN, AccessGate.check(null, judged = true))

    @Test
    fun `no evaluations left`() =
        assertEquals(AccessCheck.NO_EVALUATIONS, AccessGate.check(status(remaining = 0), judged = true))

    @Test
    fun `no included access and no credits`() =
        assertEquals(AccessCheck.NO_CREDITS, AccessGate.check(status(included = 0, credits = 0), judged = true))

    @Test
    fun `credits or included access let it through`() {
        assertEquals(AccessCheck.OK, AccessGate.check(status(included = 0, credits = 2), judged = true))
        assertEquals(AccessCheck.OK, AccessGate.check(status(included = 1), judged = true))
    }
}
