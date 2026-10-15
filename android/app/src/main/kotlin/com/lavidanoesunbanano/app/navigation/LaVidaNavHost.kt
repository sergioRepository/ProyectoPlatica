package com.lavidanoesunbanano.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lavidanoesunbanano.app.core.designsystem.FadeIn
import com.lavidanoesunbanano.app.core.designsystem.FadeOut
import com.lavidanoesunbanano.app.feature.generation.GenerationScreen
import com.lavidanoesunbanano.app.feature.home.HomeScreen
import com.lavidanoesunbanano.app.feature.onboarding.OnboardingScreen
import kotlinx.serialization.Serializable

@Serializable
data object OnboardingRoute

@Serializable
data object GenerationRoute

@Serializable
data object HomeRoute

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
                    navController.navigate(GenerationRoute) {
                        popUpTo<OnboardingRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<GenerationRoute> {
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
        composable<HomeRoute> {
            HomeScreen()
        }
    }
}
