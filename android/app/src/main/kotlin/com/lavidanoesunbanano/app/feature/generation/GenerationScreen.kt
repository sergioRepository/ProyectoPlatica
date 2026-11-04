package com.lavidanoesunbanano.app.feature.generation

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.Banana
import com.lavidanoesunbanano.app.core.designsystem.BananaMood
import com.lavidanoesunbanano.app.core.designsystem.FadeIn
import com.lavidanoesunbanano.app.core.designsystem.FadeOut
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.Panel
import com.lavidanoesunbanano.app.core.designsystem.PrimaryButton
import com.lavidanoesunbanano.app.core.designsystem.ProgressBar
import com.lavidanoesunbanano.app.core.designsystem.ScreenColumn
import com.lavidanoesunbanano.app.core.designsystem.SecondaryButton

private val STATUS_TEXTS = listOf(
    R.string.generation_status_1,
    R.string.generation_status_2,
    R.string.generation_status_3,
    R.string.generation_status_4,
    R.string.generation_status_5,
)

/** Pasadas estas frases (más de medio minuto), se avisa que va lento. */
private const val SLOW_AFTER_STATUSES = 9

@Composable
fun GenerationScreen(
    onDone: () -> Unit,
    onEditAnswers: () -> Unit,
    @StringRes doneLabel: Int = R.string.generation_done_action,
    @StringRes editAnswersLabel: Int? = null,
    firstTime: Boolean = true,
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
        firstTime = firstTime,
    )
}

@Composable
private fun GenerationContent(
    state: GenerationUiState,
    onRetry: () -> Unit,
    onDone: () -> Unit,
    onEditAnswers: () -> Unit,
    @StringRes doneLabel: Int = R.string.generation_done_action,
    @StringRes editAnswersLabel: Int? = null,
    firstTime: Boolean = true,
) {
    ScreenColumn(Modifier.padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 28.dp)) {
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { FadeIn togetherWith FadeOut },
            label = "generacion",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            Column(Modifier.fillMaxSize()) {
                when (current) {
                    is GenerationUiState.Working -> Working(current)
                    is GenerationUiState.Done -> Done(current, onDone, doneLabel, firstTime)
                    is GenerationUiState.Failed -> Failed(current, onRetry, onEditAnswers, editAnswersLabel)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.Working(state: GenerationUiState.Working) {
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Banana(BananaMood.WAITING, 132.dp, Modifier.align(Alignment.CenterHorizontally))
        Text(
            text = stringResource(R.string.generation_working_title),
            modifier = Modifier.fillMaxWidth(),
            style = type.screenTitle,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        ProgressBar()
        Text(
            text = stringResource(STATUS_TEXTS[state.status % STATUS_TEXTS.size]),
            modifier = Modifier.fillMaxWidth(),
            style = type.monoBody,
            color = colors.textBody,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(
                if (state.status >= SLOW_AFTER_STATUSES) R.string.generation_working_slow else R.string.generation_working_hint,
            ),
            modifier = Modifier.fillMaxWidth(),
            style = type.small,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ColumnScope.Done(
    state: GenerationUiState.Done,
    onDone: () -> Unit,
    @StringRes doneLabel: Int,
    firstTime: Boolean,
) {
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Banana(BananaMood.HAPPY, 116.dp)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.generation_done_title),
                style = type.hero.copy(fontSize = 56.sp, lineHeight = 56.sp),
                color = colors.textPrimary,
            )
            Text(
                text = pluralStringResource(R.plurals.generation_done_count, state.count, state.count),
                style = type.body,
                color = colors.textPrimary,
            )
        }
        state.sample?.let { sample ->
            Panel(padding = 18.dp) {
                Text(
                    text = sample,
                    style = type.section.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                )
            }
        }
    }
    Spacer(Modifier.padding(top = 16.dp))
    if (firstTime) {
        Text(
            text = stringResource(R.string.generation_done_step),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            style = type.caption,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
    PrimaryButton(text = stringResource(doneLabel), onClick = onDone, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ColumnScope.Failed(
    state: GenerationUiState.Failed,
    onRetry: () -> Unit,
    onEditAnswers: () -> Unit,
    @StringRes editAnswersLabel: Int?,
) {
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    val reason = state.reason
    // Reintentar no sirve si el límite es del día, el registro está cerrado o faltan datos.
    val canRetry = reason != GenerationFailure.MISSING_PROFILE &&
        reason != GenerationFailure.DAILY_LIMIT &&
        reason != GenerationFailure.REGISTRATION_CLOSED
    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Banana(BananaMood.FLAT, 104.dp)
        Text(text = stringResource(reason.title), style = type.screenTitle, color = colors.textPrimary)
        Panel(spacing = 8.dp) {
            Text(text = stringResource(reason.body), style = type.body, color = colors.textPrimary)
            Text(
                text = stringResource(R.string.generation_saved),
                style = type.small.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
            )
        }
    }
    Spacer(Modifier.padding(top = 16.dp))
    val editLabel = stringResource(
        editAnswersLabel ?: if (reason == GenerationFailure.MISSING_PROFILE) {
            R.string.generation_complete_answers
        } else {
            R.string.generation_edit_answers
        },
    )
    if (canRetry) {
        PrimaryButton(text = stringResource(R.string.generation_retry), onClick = onRetry, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.padding(top = 12.dp))
        SecondaryButton(text = editLabel, onClick = onEditAnswers, modifier = Modifier.fillMaxWidth())
    } else {
        PrimaryButton(text = editLabel, onClick = onEditAnswers, modifier = Modifier.fillMaxWidth())
    }
}

@get:StringRes
private val GenerationFailure.title: Int
    get() = when (this) {
        GenerationFailure.UNREACHABLE, GenerationFailure.SERVER, GenerationFailure.NOT_CONFIGURED -> R.string.generation_failed_title
        GenerationFailure.MISSING_PROFILE -> R.string.generation_failed_missing_title
        GenerationFailure.REGISTRATION_CLOSED -> R.string.generation_failed_closed_title
        GenerationFailure.DAILY_LIMIT -> R.string.generation_failed_limit_title
    }

@get:StringRes
private val GenerationFailure.body: Int
    get() = when (this) {
        GenerationFailure.UNREACHABLE -> R.string.generation_failed_unreachable
        GenerationFailure.SERVER -> R.string.generation_failed_server
        GenerationFailure.NOT_CONFIGURED -> R.string.generation_failed_not_configured
        GenerationFailure.MISSING_PROFILE -> R.string.generation_failed_missing_profile
        GenerationFailure.REGISTRATION_CLOSED -> R.string.generation_failed_closed
        GenerationFailure.DAILY_LIMIT -> R.string.generation_failed_daily_limit
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
