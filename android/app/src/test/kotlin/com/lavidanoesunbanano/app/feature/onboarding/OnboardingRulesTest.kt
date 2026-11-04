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

    @Test
    fun `the flow asks the block first, then the messages, and ends in the summary`() {
        assertEquals(
            listOf(
                OnboardingStep.APPS,
                OnboardingStep.SCHEDULE,
                OnboardingStep.NAME,
                OnboardingStep.MAIN_GOAL,
                OnboardingStep.SARCASM,
                OnboardingStep.PROFANITY,
                OnboardingStep.COUNTRY,
                OnboardingStep.FORBIDDEN,
                OnboardingStep.SUMMARY,
            ),
            OnboardingStep.FLOW,
        )
    }

    @Test
    fun `answers left out of the flow can still be edited alone`() {
        listOf(OnboardingStep.SECONDARY_GOALS, OnboardingStep.GOAL_REASON, OnboardingStep.EXCUSES).forEach { step ->
            assertFalse(step in OnboardingStep.FLOW, step.name)
            assertEquals(step, OnboardingUiState(editingStep = step).step)
        }
    }

    @Test
    fun `progress counts inside the stage`() {
        val schedule = OnboardingUiState(stepIndex = OnboardingStep.FLOW.indexOf(OnboardingStep.SCHEDULE))

        assertEquals(2, schedule.stagePosition)
        assertEquals(2, schedule.stageSteps.size)
    }

    @Test
    fun `the first step only goes back when it came from the summary`() {
        assertFalse(OnboardingUiState().canGoBack)
        assertTrue(OnboardingUiState(reviewUntil = OnboardingStep.APPS).canGoBack)
    }
}
