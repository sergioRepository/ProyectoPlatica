package com.lavidanoesunbanano.app.feature.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.FadeIn
import com.lavidanoesunbanano.app.core.designsystem.FadeOut
import com.lavidanoesunbanano.app.core.designsystem.InfoRow
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.MonoLabel
import com.lavidanoesunbanano.app.core.designsystem.Panel
import com.lavidanoesunbanano.app.core.designsystem.PrimaryButton
import com.lavidanoesunbanano.app.core.designsystem.SecondaryButton
import com.lavidanoesunbanano.app.domain.model.BackendStatus

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshBlocker() }
    HomeContent(
        state = state,
        onNextPreview = viewModel::nextPreview,
        onEnableBlocker = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
    )
}

@Composable
private fun HomeContent(state: HomeUiState, onNextPreview: () -> Unit, onEnableBlocker: () -> Unit) {
    val colors = LaVidaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(text = stringResource(R.string.app_name), style = LaVidaTheme.type.title, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        MonoLabel(stringResource(R.string.home_subtitle))
        Spacer(Modifier.height(24.dp))
        Panel {
            InfoRow(
                label = stringResource(R.string.home_apps_label),
                value = pluralStringResource(R.plurals.home_apps_count, state.blockedApps, state.blockedApps),
            )
            InfoRow(label = stringResource(R.string.home_schedule_label), value = state.schedule.ifBlank { "-" })
            InfoRow(label = stringResource(R.string.home_messages_label), value = state.messageCount.toString())
            InfoRow(label = stringResource(R.string.home_backend_label), value = backendText(state.backend))
            InfoRow(label = stringResource(R.string.home_blocker_label), value = blockerText(state.blockerEnabled))
        }
        if (state.blockerEnabled == false) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.home_blocker_off_hint),
                style = LaVidaTheme.type.small,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = stringResource(R.string.home_blocker_enable),
                onClick = onEnableBlocker,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        state.preview?.let { preview ->
            Spacer(Modifier.height(24.dp))
            Panel {
                MonoLabel(stringResource(R.string.home_preview_label))
                AnimatedContent(
                    targetState = preview,
                    transitionSpec = { FadeIn togetherWith FadeOut },
                    label = "vista previa",
                ) { text ->
                    Text(text = text, style = LaVidaTheme.type.title, color = colors.textPrimary)
                }
            }
            Spacer(Modifier.height(12.dp))
            SecondaryButton(text = stringResource(R.string.home_preview_next), onClick = onNextPreview)
        }
    }
}

@Composable
private fun blockerText(enabled: Boolean?): String = when (enabled) {
    null -> stringResource(R.string.status_checking)
    true -> stringResource(R.string.status_blocker_on)
    false -> stringResource(R.string.status_blocker_off)
}

@Composable
private fun backendText(status: BackendStatus?): String = when (status) {
    null -> stringResource(R.string.status_checking)
    BackendStatus.NotConfigured -> stringResource(R.string.status_not_configured)
    BackendStatus.Unreachable -> stringResource(R.string.status_unreachable)
    is BackendStatus.Online -> stringResource(R.string.status_online, status.version)
    is BackendStatus.HttpError -> stringResource(R.string.status_http_error, status.code)
}

@Preview
@Composable
private fun HomePreview() {
    LaVidaTheme {
        HomeContent(
            state = HomeUiState(
                blockedApps = 3,
                schedule = "lun a vie 08:00-18:00; todos los días 22:00-06:00",
                messageCount = 53,
                backend = BackendStatus.Online("0.1.0"),
                preview = "Van 3 intentos hoy. Los grandes programadores también viven en TikTok. Mentira.",
                blockerEnabled = false,
            ),
            onNextPreview = {},
            onEnableBlocker = {},
        )
    }
}
