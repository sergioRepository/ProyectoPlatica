package com.lavidanoesunbanano.app.feature.block

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.FadeIn
import com.lavidanoesunbanano.app.core.designsystem.FadeOut
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.MonoLabel
import com.lavidanoesunbanano.app.core.designsystem.PrimaryButton
import com.lavidanoesunbanano.app.core.designsystem.SecondaryButton

/**
 * Fondo plano; arriba la app, la hora y el intento en mono; al centro el mensaje; abajo
 * los tres botones. Emergencia y 10 minutos más se habilitan cuando llegue el juez.
 *
 * `onShown` se llama en el primer cuadro con el mensaje, para medir la latencia.
 */
@Composable
fun BlockScreen(state: BlockUiState?, onExit: () -> Unit, onShown: () -> Unit) {
    val colors = LaVidaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        AnimatedContent(
            targetState = state,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            transitionSpec = { FadeIn togetherWith FadeOut },
            label = "mensaje",
        ) { current ->
            if (current != null) BlockMessage(current) else Box(Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(
                text = stringResource(R.string.block_emergency),
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
            )
            SecondaryButton(
                text = stringResource(R.string.block_extra_time),
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
            )
            PrimaryButton(
                text = stringResource(R.string.block_exit),
                onClick = onExit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    LaunchedEffect(state != null) {
        if (state != null) {
            withFrameNanos { }
            onShown()
        }
    }
}

@Composable
private fun BlockMessage(state: BlockUiState) {
    Column(Modifier.fillMaxSize()) {
        MonoLabel(stringResource(R.string.block_meta, state.appLabel, state.time, state.attemptNumber))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.CenterStart,
        ) {
            // Con letra grande en Ajustes, el mensaje se desplaza en vez de tapar los botones.
            Text(
                text = state.message ?: stringResource(R.string.block_fallback_message),
                modifier = Modifier.verticalScroll(rememberScrollState()),
                style = LaVidaTheme.type.display,
                color = LaVidaTheme.colors.textPrimary,
            )
        }
    }
}

@Preview
@Composable
private fun BlockPreview() {
    LaVidaTheme {
        BlockScreen(
            state = BlockUiState(
                appLabel = "Instagram",
                time = "23:40",
                attemptNumber = 7,
                message = "¿Instagram otra vez? Así piensas ser el mejor programador del mundo. Qué mierda.",
            ),
            onExit = {},
            onShown = {},
        )
    }
}
