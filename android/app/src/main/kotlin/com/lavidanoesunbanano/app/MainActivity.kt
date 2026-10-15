package com.lavidanoesunbanano.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.applyWindowTheme
import com.lavidanoesunbanano.app.core.designsystem.isSystemDark
import com.lavidanoesunbanano.app.data.prefs.SettingsStore
import com.lavidanoesunbanano.app.navigation.LaVidaNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsStore

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Una lectura corta antes del primer cuadro: sin ella, el tema claro arrancaría oscuro.
        val initialTheme = runBlocking { settings.theme.first() }
        applyWindowTheme(initialTheme.isDark(isSystemDark))

        setContent {
            val mode by settings.theme.collectAsStateWithLifecycle(initialTheme)
            val dark = mode.isDark(isSystemInDarkTheme())
            LaunchedEffect(dark) { applyWindowTheme(dark) }
            LaVidaTheme(darkTheme = dark) {
                val start by viewModel.startDestination.collectAsStateWithLifecycle()
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(LaVidaTheme.colors.background),
                ) {
                    start?.let { LaVidaNavHost(startDestination = it) }
                }
            }
        }
    }
}
