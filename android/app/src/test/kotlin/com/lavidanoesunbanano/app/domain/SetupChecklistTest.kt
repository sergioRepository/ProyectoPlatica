package com.lavidanoesunbanano.app.domain

import com.lavidanoesunbanano.app.domain.setup.ServiceHealth
import com.lavidanoesunbanano.app.domain.setup.SetupChecklist
import com.lavidanoesunbanano.app.domain.setup.SetupItem
import com.lavidanoesunbanano.app.domain.setup.SetupStatus
import com.lavidanoesunbanano.app.domain.setup.SetupStep
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class SetupChecklistTest {

    private val ready = SetupStatus(
        sdk = 35,
        installedFromFile = false,
        service = ServiceHealth.ACTIVE,
        notificationsAllowed = true,
        overlayAllowed = true,
        batteryUnrestricted = true,
    )

    @Test
    fun `service health tells off from stopped`() {
        assertEquals(ServiceHealth.OFF, ServiceHealth.of(enabledInSettings = false, running = false))
        assertEquals(ServiceHealth.STOPPED, ServiceHealth.of(enabledInSettings = true, running = false))
        assertEquals(ServiceHealth.ACTIVE, ServiceHealth.of(enabledInSettings = true, running = true))
    }

    @Test
    fun `installed by adb there is no restricted settings step`() {
        val steps = SetupChecklist.items(ready).map { it.step }

        assertEquals(
            listOf(SetupStep.ACCESSIBILITY, SetupStep.NOTIFICATIONS, SetupStep.OVERLAY, SetupStep.BATTERY),
            steps,
        )
    }

    @Test
    fun `installed from a file on android 13 the restricted settings step comes first`() {
        val items = SetupChecklist.items(ready.copy(installedFromFile = true, service = ServiceHealth.OFF))

        assertEquals(SetupItem(SetupStep.RESTRICTED_SETTINGS, done = false, required = true), items.first())
        // Si Accesibilidad ya se pudo activar, los ajustes restringidos estaban permitidos.
        val enabled = SetupChecklist.items(ready.copy(installedFromFile = true, service = ServiceHealth.STOPPED))
        assertTrue(enabled.first().done)
    }

    @Test
    fun `before android 13 neither notifications nor restricted settings appear`() {
        val steps = SetupChecklist.items(ready.copy(sdk = 31, installedFromFile = true)).map { it.step }

        assertEquals(listOf(SetupStep.ACCESSIBILITY, SetupStep.OVERLAY, SetupStep.BATTERY), steps)
    }

    @Test
    fun `the blocker is ready only with the required steps`() {
        val stopped = SetupChecklist.items(ready.copy(service = ServiceHealth.STOPPED))
        val missingExtras = SetupChecklist.items(ready.copy(overlayAllowed = false, batteryUnrestricted = false))

        assertFalse(SetupChecklist.blockerReady(stopped))
        assertTrue(SetupChecklist.blockerReady(missingExtras))
        assertEquals(2, SetupChecklist.pending(missingExtras))
        assertEquals(0, SetupChecklist.pending(SetupChecklist.items(ready)))
    }
}
