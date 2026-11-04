package com.lavidanoesunbanano.app.feature.onboarding

import com.lavidanoesunbanano.app.domain.apps.AppOrdering
import com.lavidanoesunbanano.app.domain.model.AppInfo
import com.lavidanoesunbanano.app.domain.model.BlockRange
import com.lavidanoesunbanano.app.domain.model.Profile
import java.time.DayOfWeek
import java.time.LocalTime

/** Las etapas de la barra de progreso. */
enum class OnboardingStage { BLOCK, MESSAGES, SUMMARY }

/** Un paso por pantalla. `stage` nulo: no sale en el onboarding, se llena desde Ajustes. */
enum class OnboardingStep(val stage: OnboardingStage? = null) {
    APPS(OnboardingStage.BLOCK),
    SCHEDULE(OnboardingStage.BLOCK),
    NAME(OnboardingStage.MESSAGES),
    MAIN_GOAL(OnboardingStage.MESSAGES),
    SARCASM(OnboardingStage.MESSAGES),
    PROFANITY(OnboardingStage.MESSAGES),
    COUNTRY(OnboardingStage.MESSAGES),
    FORBIDDEN(OnboardingStage.MESSAGES),
    SUMMARY(OnboardingStage.SUMMARY),
    SECONDARY_GOALS,
    GOAL_REASON,
    EXCUSES,
    ;

    companion object {
        /** Los pasos del onboarding, en orden. */
        val FLOW: List<OnboardingStep> = entries.filter { it.stage != null }
    }
}

data class OnboardingDraft(
    val name: String = "",
    val mainGoal: String = "",
    val secondaryGoals: List<String> = emptyList(),
    val goalReason: String = "",
    val selectedApps: List<AppInfo> = emptyList(),
    val ranges: List<BlockRange> = emptyList(),
    val typicalExcuses: String = "",
    val sarcasmLevel: Int = 3,
    val profanityAllowed: Boolean? = null,
    val country: String = OnboardingRules.DEFAULT_COUNTRY,
    val forbiddenTopics: String = "",
)

object OnboardingRules {
    // Mismos límites que el backend (banano/schemas/profile.py).
    const val MAX_NAME = 40
    const val MIN_GOAL = 3
    const val MAX_GOAL = 200
    const val MAX_SECONDARY_GOAL = 120
    const val MAX_SECONDARY_GOALS = 10
    const val MAX_REASON = 500
    const val MAX_EXCUSES = 500
    const val MAX_FORBIDDEN = 300

    /** Desde este nivel el juez puede insultar a la persona (INSULTS_FROM_LEVEL en el backend). */
    const val INSULTS_FROM_LEVEL = 4

    const val DEFAULT_COUNTRY = "Colombia"
    const val NEUTRAL_COUNTRY = "español neutro"
    val COUNTRIES = listOf(
        "Colombia", "México", "Argentina", "Chile", "Perú", "Venezuela", "Ecuador", "Uruguay", "España",
    )

    fun canContinue(step: OnboardingStep, draft: OnboardingDraft): Boolean = when (step) {
        OnboardingStep.NAME -> draft.name.isNotBlank()
        OnboardingStep.MAIN_GOAL -> draft.mainGoal.trim().length >= MIN_GOAL
        OnboardingStep.APPS -> draft.selectedApps.isNotEmpty()
        OnboardingStep.SCHEDULE -> draft.ranges.isNotEmpty()
        OnboardingStep.SARCASM -> draft.sarcasmLevel in 1..5
        OnboardingStep.PROFANITY -> draft.profanityAllowed != null
        OnboardingStep.COUNTRY -> draft.country.isNotBlank()
        OnboardingStep.SECONDARY_GOALS,
        OnboardingStep.GOAL_REASON,
        OnboardingStep.EXCUSES,
        OnboardingStep.FORBIDDEN,
        OnboardingStep.SUMMARY,
        -> true
    }

    fun toProfile(draft: OnboardingDraft) = Profile(
        name = draft.name.trim(),
        mainGoal = draft.mainGoal.trim(),
        secondaryGoals = draft.secondaryGoals.map { it.trim() }.filter { it.isNotEmpty() },
        goalReason = draft.goalReason.trim(),
        typicalExcuses = draft.typicalExcuses.trim(),
        sarcasmLevel = draft.sarcasmLevel,
        profanityAllowed = draft.profanityAllowed ?: false,
        country = draft.country,
        forbiddenTopics = draft.forbiddenTopics.trim(),
    )

    fun fromSaved(profile: Profile, apps: List<AppInfo>, ranges: List<BlockRange>) = OnboardingDraft(
        name = profile.name,
        mainGoal = profile.mainGoal,
        secondaryGoals = profile.secondaryGoals,
        goalReason = profile.goalReason,
        selectedApps = apps,
        ranges = ranges,
        typicalExcuses = profile.typicalExcuses,
        sarcasmLevel = profile.sarcasmLevel,
        profanityAllowed = profile.profanityAllowed,
        country = profile.country,
        forbiddenTopics = profile.forbiddenTopics,
    )
}

object SchedulePresets {
    private val WEEKDAYS = DayOfWeek.entries.take(5).toSet()
    private val EVERY_DAY = DayOfWeek.entries.toSet()

    val WORK = BlockRange(WEEKDAYS, LocalTime.of(8, 0), LocalTime.of(18, 0))
    val NIGHTS = BlockRange(EVERY_DAY, LocalTime.of(22, 0), LocalTime.of(6, 0))
    val MORNINGS = BlockRange(EVERY_DAY, LocalTime.of(6, 0), LocalTime.of(9, 0))
}

data class OnboardingUiState(
    val stepIndex: Int = 0,
    val draft: OnboardingDraft = OnboardingDraft(),
    val secondaryInput: String = "",
    /** `null` mientras se cargan. */
    val installedApps: List<AppInfo>? = null,
    val appQuery: String = "",
    val saving: Boolean = false,
    val finished: Boolean = false,
    /** Ya se intentó cargar el perfil guardado; antes de eso no se puede editar ni guardar un paso. */
    val profileLoaded: Boolean = false,
    /** El paso que se edita desde Ajustes; `null` en el onboarding completo. */
    val editingStep: OnboardingStep? = null,
    /** Lo guardado antes de editar: sin cambios no hay nada que guardar. */
    val savedDraft: OnboardingDraft? = null,
    /** Se vino del resumen a cambiar algo: al llegar a este paso se vuelve al resumen. */
    val reviewUntil: OnboardingStep? = null,
) {
    val editing: Boolean get() = editingStep != null
    val step: OnboardingStep get() = editingStep ?: OnboardingStep.FLOW[stepIndex]
    val isLastStep: Boolean get() = step == OnboardingStep.SUMMARY
    val canContinue: Boolean get() = !saving && OnboardingRules.canContinue(step, draft)
    val hasChanges: Boolean get() = draft != savedDraft

    /** Desde el primer paso solo se vuelve si se vino del resumen. */
    val canGoBack: Boolean get() = stepIndex > 0 || reviewUntil != null

    /** Pasos de la etapa actual y cuál es este, desde 1. */
    val stageSteps: List<OnboardingStep> get() = OnboardingStep.FLOW.filter { it.stage == step.stage }
    val stagePosition: Int get() = stageSteps.indexOf(step) + 1
    val visibleApps: List<AppInfo>? get() = installedApps?.let { AppOrdering.filter(it, appQuery) }
    val selectedPackages: Set<String> get() = draft.selectedApps.mapTo(mutableSetOf()) { it.packageName }
}

/** Lo que la pantalla le puede pedir al ViewModel; permite previews sin Hilt. */
interface OnboardingActions {
    fun next()
    fun back()

    /** Desde el resumen: abre `step` y vuelve al resumen al pasar `until`. */
    fun review(step: OnboardingStep, until: OnboardingStep = step)
    fun onNameChange(value: String)
    fun onMainGoalChange(value: String)
    fun onSecondaryInputChange(value: String)
    fun addSecondaryGoal()
    fun removeSecondaryGoal(index: Int)
    fun onReasonChange(value: String)
    fun onAppQueryChange(value: String)
    fun toggleApp(app: AppInfo)
    fun addRange(range: BlockRange)
    fun removeRange(index: Int)
    fun onExcusesChange(value: String)
    fun onSarcasmChange(level: Int)
    fun onProfanityChange(allowed: Boolean)
    fun onCountryChange(country: String)
    fun onForbiddenChange(value: String)
}
