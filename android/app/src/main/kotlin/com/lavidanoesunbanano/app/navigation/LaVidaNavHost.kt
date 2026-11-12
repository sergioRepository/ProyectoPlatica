package com.lavidanoesunbanano.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.FadeIn
import com.lavidanoesunbanano.app.core.designsystem.FadeOut
import com.lavidanoesunbanano.app.core.designsystem.MainTab
import com.lavidanoesunbanano.app.feature.consent.ConsentScreen
import com.lavidanoesunbanano.app.feature.generation.GenerationScreen
import com.lavidanoesunbanano.app.feature.history.HistoryScreen
import com.lavidanoesunbanano.app.feature.home.HomeScreen
import com.lavidanoesunbanano.app.feature.onboarding.EditAnswerScreen
import com.lavidanoesunbanano.app.feature.onboarding.OnboardingScreen
import com.lavidanoesunbanano.app.feature.onboarding.OnboardingStep
import com.lavidanoesunbanano.app.feature.settings.SettingsScreen
import com.lavidanoesunbanano.app.feature.setup.SetupScreen
import kotlinx.serialization.Serializable

@Serializable
data object ConsentRoute

@Serializable
data object OnboardingRoute

/** `regenerate`: se pidió desde Ajustes; al terminar se vuelve allá. */
@Serializable
data class GenerationRoute(val regenerate: Boolean = false)

@Serializable
data object HomeRoute

@Serializable
data object HistoryRoute

@Serializable
data object SettingsRoute

@Serializable
data object SetupRoute

/** `step`: nombre de un [OnboardingStep]. */
@Serializable
data class EditAnswerRoute(val step: String)

/** `openSetup`: se abrió desde la notificación de bloqueo apagado. `openPlans`: desde la pantalla de bloqueo. */
@Composable
fun LaVidaNavHost(startDestination: Any, openSetup: Boolean = false, openPlans: Boolean = false) {
    val navController = rememberNavController()
    LaunchedEffect(openSetup) {
        // Sin onboarding terminado no hay bloqueo que configurar todavía.
        if (openSetup && startDestination == HomeRoute) navController.navigate(SetupRoute)
    }
    LaunchedEffect(openPlans) {
        // Paso 7: PlansRoute
        if (openPlans && startDestination == HomeRoute) navController.navigate(SettingsRoute)
    }
    val openTab: (MainTab) -> Unit = { tab ->
        // Las pestañas no se apilan: Atrás siempre lleva a Inicio.
        when (tab) {
            MainTab.HOME -> navController.popBackStack<HomeRoute>(inclusive = false)
            MainTab.HISTORY -> navController.navigate(HistoryRoute) {
                popUpTo<HomeRoute>()
                launchSingleTop = true
            }
            MainTab.SETTINGS -> navController.navigate(SettingsRoute) {
                popUpTo<HomeRoute>()
                launchSingleTop = true
            }
        }
    }
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { FadeIn },
        exitTransition = { FadeOut },
        popEnterTransition = { FadeIn },
        popExitTransition = { FadeOut },
    ) {
        composable<ConsentRoute> {
            ConsentScreen(
                onDone = { next ->
                    navController.navigate(next) {
                        popUpTo<ConsentRoute> { inclusive = true }
                    }
                },
            )
        }
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
                    firstTime = false,
                )
            } else {
                GenerationScreen(
                    onDone = {
                        // Inicio queda debajo: Atrás desde los permisos no cierra la app.
                        navController.navigate(HomeRoute) {
                            popUpTo<GenerationRoute> { inclusive = true }
                        }
                        navController.navigate(SetupRoute)
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
                onTab = openTab,
                onOpenSetup = { navController.navigate(SetupRoute) },
                onOpenPlans = { navController.navigate(SettingsRoute) }, // Paso 7: PlansRoute
            )
        }
        composable<HistoryRoute> { HistoryScreen(onTab = openTab) }
        composable<SettingsRoute> {
            SettingsScreen(
                onTab = openTab,
                onEditStep = { step -> navController.navigate(EditAnswerRoute(step.name)) },
                onRegenerate = { navController.navigate(GenerationRoute(regenerate = true)) },
                onOpenSetup = { navController.navigate(SetupRoute) },
                onErased = {
                    navController.navigate(ConsentRoute) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
            )
        }
        composable<SetupRoute> { SetupScreen(onContinue = { navController.popBackStack() }) }
        composable<EditAnswerRoute> { entry ->
            val step = OnboardingStep.valueOf(entry.toRoute<EditAnswerRoute>().step)
            EditAnswerScreen(step = step, onDone = { navController.popBackStack() })
        }
    }
}
