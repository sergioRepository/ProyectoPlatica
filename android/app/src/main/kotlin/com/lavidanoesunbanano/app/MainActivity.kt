package com.lavidanoesunbanano.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.applyWindowTheme
import com.lavidanoesunbanano.app.navigation.LaVidaNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Solo la primera vez: al recrear la Activity no se vuelve a abrir la guía.
        val openSetup = savedInstanceState == null && intent.getBooleanExtra(EXTRA_OPEN_SETUP, false)
        applyWindowTheme()

        setContent {
            LaVidaTheme {
                val start by viewModel.startDestination.collectAsStateWithLifecycle()
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(LaVidaTheme.colors.background),
                ) {
                    start?.let { LaVidaNavHost(startDestination = it, openSetup = openSetup) }
                }
            }
        }
    }

    companion object {
        /** La notificación de bloqueo apagado abre la app directo en la guía. */
        const val EXTRA_OPEN_SETUP = "open_setup"
    }
}
