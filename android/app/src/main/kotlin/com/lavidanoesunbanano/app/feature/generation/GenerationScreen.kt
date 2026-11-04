package com.lavidanoesunbanano.app.feature.generation

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.FadeIn
import com.lavidanoesunbanano.app.core.designsystem.FadeOut
import com.lavidanoesunbanano.app.core.designsystem.GhostButton
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.MonoLabel
import com.lavidanoesunbanano.app.core.designsystem.Panel
import com.lavidanoesunbanano.app.core.designsystem.PrimaryButton
import com.lavidanoesunbanano.app.core.designsystem.StatusLine

private val STATUS_TEXTS = listOf(
    R.string.generation_status_1,
    R.string.generation_status_2,
    R.string.generation_status_3,
    R.string.generation_status_4,
    R.string.generation_status_5,
)

@Composable
fun GenerationScreen(
    onDone: () -> Unit,
    onEditAnswers: () -> Unit,
    @StringRes doneLabel: Int = R.string.generation_done_action,
    @StringRes editAnswersLabel: Int = R.string.generation_edit_answers,
    viewModel: GenerationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GenerationContent(
        state = state,
        onRetry = viewModel::generate,
        onDone = onDone,
        onEditAnswers = onEditAnswers,
        doneLabel = doneLabel,
        editAnswersLabel = editAnswersLabel,
    )
}

@Composable
private fun GenerationContent(
    state: GenerationUiState,
    onRetry: () -> Unit,
    onDone: () -> Unit,
    onEditAnswers: () -> Unit,
    @StringRes doneLabel: Int = R.string.generation_done_action,
    @StringRes editAnswersLabel: Int = R.string.generation_edit_answers,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LaVidaTheme.colors.background)
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Spacer(Modifier.height(40.dp))
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { FadeIn togetherWith FadeOut },
            label = "generacion",
        ) { current ->
            Column(Modifier.fillMaxWidth()) {
                when (current) {
                    is GenerationUiState.Working -> Working(current)
                    is GenerationUiState.Done -> Done(current, onDone, doneLabel)
                    is GenerationUiState.Failed -> Failed(current, onRetry, onEditAnswers, editAnswersLabel)
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text = text, style = LaVidaTheme.type.title, color = LaVidaTheme.colors.textPrimary)
}

@Composable
private fun Hint(text: String) {
    Text(text = text, style = LaVidaTheme.type.small, color = LaVidaTheme.colors.textSecondary)
}

@Composable
private fun Working(state: GenerationUiState.Working) {
    Title(stringResource(R.string.generation_working_title))
    Spacer(Modifier.height(8.dp))
    Hint(stringResource(R.string.generation_working_hint))
    Spacer(Modifier.height(32.dp))
    StatusLine(stringResource(STATUS_TEXTS[state.status % STATUS_TEXTS.size]))
}

@Composable
private fun Done(state: GenerationUiState.Done, onDone: () -> Unit, @StringRes doneLabel: Int) {
    Title(stringResource(R.string.generation_done_title))
    Spacer(Modifier.height(8.dp))
    Hint(pluralStringResource(R.plurals.generation_done_count, state.count, state.count))
    state.sample?.let { sample ->
        Spacer(Modifier.height(24.dp))
        Panel {
            MonoLabel(stringResource(R.string.generation_sample_label))
            Text(text = sample, style = LaVidaTheme.type.title, color = LaVidaTheme.colors.textPrimary)
        }
    }
    Spacer(Modifier.height(32.dp))
    PrimaryButton(text = stringResource(doneLabel), onClick = onDone)
}

@Composable
private fun Failed(
    state: GenerationUiState.Failed,
    onRetry: () -> Unit,
    onEditAnswers: () -> Unit,
    @StringRes editAnswersLabel: Int,
) {
    Title(stringResource(R.string.generation_failed_title))
    Spacer(Modifier.height(8.dp))
    Hint(
        stringResource(
            when (state.reason) {
                GenerationFailure.UNREACHABLE -> R.string.generation_failed_unreachable
                GenerationFailure.SERVER -> R.string.generation_failed_server
                GenerationFailure.NOT_CONFIGURED -> R.string.generation_failed_not_configured
                GenerationFailure.MISSING_PROFILE -> R.string.generation_failed_missing_profile
                GenerationFailure.REGISTRATION_CLOSED -> R.string.generation_failed_closed
                GenerationFailure.DAILY_LIMIT -> R.string.generation_failed_daily_limit
            },
        ),
    )
    Spacer(Modifier.height(32.dp))
    if (state.reason != GenerationFailure.MISSING_PROFILE) {
        PrimaryButton(text = stringResource(R.string.action_retry), onClick = onRetry)
        Spacer(Modifier.height(8.dp))
    }
    GhostButton(text = stringResource(editAnswersLabel), onClick = onEditAnswers)
}

@Preview
@Composable
private fun GenerationDonePreview() {
    LaVidaTheme {
        GenerationContent(
            state = GenerationUiState.Done(
                count = 53,
                sample = "Son las 23:40 y sigues en Instagram. Mañana vas a programar con sueño.",
            ),
            onRetry = {},
            onDone = {},
            onEditAnswers = {},
        )
    }
}
