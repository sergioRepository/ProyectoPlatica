package com.lavidanoesunbanano.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.InfoRow
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTextField
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.LinkRow
import com.lavidanoesunbanano.app.core.designsystem.MonoLabel
import com.lavidanoesunbanano.app.core.designsystem.Panel
import com.lavidanoesunbanano.app.core.designsystem.PrimaryButton
import com.lavidanoesunbanano.app.core.designsystem.StatusLine
import com.lavidanoesunbanano.app.domain.model.BackendStatus
import com.lavidanoesunbanano.app.domain.setup.ServiceHealth
import com.lavidanoesunbanano.app.domain.setup.SetupChecklist
import com.lavidanoesunbanano.app.feature.generation.CodeError

@Composable
fun HomeScreen(
    onOpenStats: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSetup: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshOnResume() }
    HomeContent(
        state = state,
        actions = HomeActions(
            onOpenSetup = onOpenSetup,
            onCodeChange = viewModel::onCodeChange,
            onSubmitCode = viewModel::submitCode,
            onOpenStats = onOpenStats,
            onOpenHistory = onOpenHistory,
            onOpenSettings = onOpenSettings,
        ),
    )
}

private class HomeActions(
    val onOpenSetup: () -> Unit,
    val onCodeChange: (String) -> Unit,
    val onSubmitCode: () -> Unit,
    val onOpenStats: () -> Unit,
    val onOpenHistory: () -> Unit,
    val onOpenSettings: () -> Unit,
)

@Composable
private fun HomeContent(state: HomeUiState, actions: HomeActions) {
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
        Spacer(Modifier.height(24.dp))
        Panel {
            InfoRow(label = stringResource(R.string.home_blocker_label), value = blockerText(state.setup?.service))
            InfoRow(label = stringResource(R.string.home_backend_label), value = backendText(state.backend))
        }
        state.setup?.let { setup ->
            val items = SetupChecklist.items(setup)
            if (!SetupChecklist.blockerReady(items)) {
                Spacer(Modifier.height(16.dp))
                Text(text = stringResource(R.string.home_setup_title), style = LaVidaTheme.type.body, color = colors.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text(text = stringResource(R.string.home_setup_hint), style = LaVidaTheme.type.small, color = colors.textSecondary)
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = stringResource(R.string.home_setup_action),
                    onClick = actions.onOpenSetup,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (SetupChecklist.pending(items) > 0) {
                Spacer(Modifier.height(12.dp))
                val pending = SetupChecklist.pending(items)
                LinkRow(
                    title = stringResource(R.string.setup_link),
                    value = pluralStringResource(R.plurals.setup_pending_count, pending, pending),
                    onClick = actions.onOpenSetup,
                )
            }
        }
        state.codeEntry?.let { entry ->
            Spacer(Modifier.height(24.dp))
            CodeForm(entry, actions.onCodeChange, actions.onSubmitCode)
        }

        Spacer(Modifier.height(32.dp))
        MonoLabel(stringResource(R.string.home_today))
        Spacer(Modifier.height(12.dp))
        Panel {
            val today = state.today
            InfoRow(label = stringResource(R.string.home_attempts_label), value = today?.attempts?.toString() ?: "-")
            InfoRow(
                label = stringResource(R.string.home_recovered_label),
                value = today?.let { stringResource(R.string.minutes_approx, it.recoveredMinutes) } ?: "-",
            )
            InfoRow(
                label = stringResource(R.string.home_excuses_label),
                value = today?.let { stringResource(R.string.excuses_tally, it.approved, it.rejected) } ?: "-",
            )
            state.unlocks.forEach { unlock ->
                InfoRow(
                    label = stringResource(R.string.home_unlocked_label),
                    value = stringResource(R.string.home_unlocked_value, unlock.label, unlock.until),
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinkRow(
                title = stringResource(R.string.home_stats),
                value = stringResource(R.string.home_stats_desc),
                onClick = actions.onOpenStats,
            )
            LinkRow(
                title = stringResource(R.string.home_history),
                value = stringResource(R.string.home_history_desc),
                onClick = actions.onOpenHistory,
            )
            LinkRow(
                title = stringResource(R.string.home_settings),
                value = stringResource(R.string.home_settings_desc),
                onClick = actions.onOpenSettings,
            )
        }
    }
}

/** El juez perdió el token: se vuelve a registrar el teléfono sin regenerar la plantilla. */
@Composable
private fun CodeForm(entry: CodeEntry, onCodeChange: (String) -> Unit, onSubmit: () -> Unit) {
    val colors = LaVidaTheme.colors
    Text(text = stringResource(R.string.home_judge_code_title), style = LaVidaTheme.type.body, color = colors.textPrimary)
    Spacer(Modifier.height(6.dp))
    Text(text = stringResource(R.string.home_judge_code_hint), style = LaVidaTheme.type.small, color = colors.textSecondary)
    Spacer(Modifier.height(12.dp))
    LaVidaTextField(
        value = entry.code,
        onValueChange = onCodeChange,
        placeholder = stringResource(R.string.generation_code_placeholder),
        maxLength = 200,
        mono = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
    )
    entry.error?.let { error ->
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                when (error) {
                    CodeError.WRONG -> R.string.generation_code_wrong
                    CodeError.UNREACHABLE -> R.string.generation_code_unreachable
                    CodeError.SERVER -> R.string.generation_code_server
                },
            ),
            style = LaVidaTheme.type.mono,
            color = colors.rejected,
        )
    }
    Spacer(Modifier.height(12.dp))
    if (entry.submitting) {
        StatusLine(stringResource(R.string.status_checking))
    } else {
        PrimaryButton(
            text = stringResource(R.string.generation_code_action),
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = entry.code.isNotBlank(),
        )
    }
}

@Composable
private fun blockerText(health: ServiceHealth?): String = when (health) {
    null -> stringResource(R.string.status_checking)
    ServiceHealth.ACTIVE -> stringResource(R.string.status_blocker_on)
    ServiceHealth.OFF -> stringResource(R.string.status_blocker_off)
    ServiceHealth.STOPPED -> stringResource(R.string.status_blocker_stopped)
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
                backend = BackendStatus.Online("0.1.0"),
                unlocks = listOf(ActiveUnlock("Instagram", "21:55")),
            ),
            actions = HomeActions({}, {}, {}, {}, {}, {}),
        )
    }
}
