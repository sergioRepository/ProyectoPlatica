package com.lavidanoesunbanano.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.FadeIn
import com.lavidanoesunbanano.app.core.designsystem.FadeOut
import com.lavidanoesunbanano.app.feature.generation.GenerationScreen
import com.lavidanoesunbanano.app.feature.history.HistoryScreen
import com.lavidanoesunbanano.app.feature.home.HomeScreen
import com.lavidanoesunbanano.app.feature.onboarding.EditAnswerScreen
import com.lavidanoesunbanano.app.feature.onboarding.OnboardingScreen
import com.lavidanoesunbanano.app.feature.onboarding.OnboardingStep
import com.lavidanoesunbanano.app.feature.settings.SettingsScreen
import com.lavidanoesunbanano.app.feature.stats.StatsScreen
import kotlinx.serialization.Serializable

@Serializable
data object OnboardingRoute

/** `regenerate`: se pidió desde Ajustes; al terminar se vuelve allá. */
@Serializable
data class GenerationRoute(val regenerate: Boolean = false)

@Serializable
data object HomeRoute

@Serializable
data object StatsRoute

@Serializable
data object HistoryRoute

@Serializable
data object SettingsRoute

/** `step`: nombre de un [OnboardingStep]. */
@Serializable
data class EditAnswerRoute(val step: String)

@Composable
fun LaVidaNavHost(startDestination: Any) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { FadeIn },
        exitTransition = { FadeOut },
        popEnterTransition = { FadeIn },
        popExitTransition = { FadeOut },
    ) {
        composable<OnboardingRoute> {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(GenerationRoute()) {
                        popUpTo<OnboardingRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<GenerationRoute> { entry ->
            val route = entry.toRoute<GenerationRoute>()
            if (route.regenerate) {
                GenerationScreen(
                    onDone = { navController.popBackStack() },
                    onEditAnswers = { navController.popBackStack() },
                    doneLabel = R.string.generation_done_back,
                    editAnswersLabel = R.string.generation_done_back,
                )
            } else {
                GenerationScreen(
                    onDone = {
                        navController.navigate(HomeRoute) {
                            popUpTo<GenerationRoute> { inclusive = true }
                        }
                    },
                    onEditAnswers = {
                        navController.navigate(OnboardingRoute) {
                            popUpTo<GenerationRoute> { inclusive = true }
                        }
                    },
                )
            }
        }
        composable<HomeRoute> {
            HomeScreen(
                onOpenStats = { navController.navigate(StatsRoute) },
                onOpenHistory = { navController.navigate(HistoryRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
            )
        }
        composable<StatsRoute> { StatsScreen() }
        composable<HistoryRoute> { HistoryScreen() }
        composable<SettingsRoute> {
            SettingsScreen(
                onEditStep = { step -> navController.navigate(EditAnswerRoute(step.name)) },
                onRegenerate = { navController.navigate(GenerationRoute(regenerate = true)) },
            )
        }
        composable<EditAnswerRoute> { entry ->
            val step = OnboardingStep.valueOf(entry.toRoute<EditAnswerRoute>().step)
            EditAnswerScreen(step = step, onDone = { navController.popBackStack() })
        }
    }
}
