package com.lavidanoesunbanano.app.feature.onboarding

import com.lavidanoesunbanano.app.domain.model.AppInfo
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class OnboardingRulesTest {

    private val empty = OnboardingDraft()

    @Test
    fun `required steps need an answer`() {
        assertFalse(OnboardingRules.canContinue(OnboardingStep.NAME, empty.copy(name = "   ")))
        assertTrue(OnboardingRules.canContinue(OnboardingStep.NAME, empty.copy(name = "Alex")))
        assertFalse(OnboardingRules.canContinue(OnboardingStep.MAIN_GOAL, empty.copy(mainGoal = " ab ")))
        assertFalse(OnboardingRules.canContinue(OnboardingStep.APPS, empty))
        assertTrue(OnboardingRules.canContinue(OnboardingStep.APPS, empty.copy(selectedApps = listOf(AppInfo("a.b", "App")))))
        assertFalse(OnboardingRules.canContinue(OnboardingStep.SCHEDULE, empty))
        assertTrue(OnboardingRules.canContinue(OnboardingStep.SCHEDULE, empty.copy(ranges = listOf(SchedulePresets.WORK))))
        assertFalse(OnboardingRules.canContinue(OnboardingStep.PROFANITY, empty))
        assertTrue(OnboardingRules.canContinue(OnboardingStep.PROFANITY, empty.copy(profanityAllowed = false)))
    }

    @Test
    fun `optional steps never block`() {
        listOf(
            OnboardingStep.SECONDARY_GOALS,
            OnboardingStep.GOAL_REASON,
            OnboardingStep.EXCUSES,
            OnboardingStep.FORBIDDEN,
            OnboardingStep.SARCASM,
            OnboardingStep.COUNTRY,
        ).forEach { step -> assertTrue(OnboardingRules.canContinue(step, empty), step.name) }
    }

    @Test
    fun `profile is trimmed and keeps the answers`() {
        val profile = OnboardingRules.toProfile(
            empty.copy(
                name = "  Alex ",
                mainGoal = " ser el mejor programador del mundo ",
                secondaryGoals = listOf(" leer ", "  "),
                sarcasmLevel = 5,
                profanityAllowed = true,
                country = "México",
            ),
        )

        assertEquals("Alex", profile.name)
        assertEquals("ser el mejor programador del mundo", profile.mainGoal)
        assertEquals(listOf("leer"), profile.secondaryGoals)
        assertEquals(5, profile.sarcasmLevel)
        assertTrue(profile.profanityAllowed)
        assertEquals("México", profile.country)
    }

    @Test
    fun `saved answers round trip into the draft`() {
        val draft = empty.copy(name = "Alex", mainGoal = "programar", profanityAllowed = false, ranges = listOf(SchedulePresets.NIGHTS))
        val profile = OnboardingRules.toProfile(draft)

        val restored = OnboardingRules.fromSaved(profile, emptyList(), draft.ranges)

        assertEquals(draft, restored)
    }
}
