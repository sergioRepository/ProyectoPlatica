package com.lavidanoesunbanano.app.feature.consent

import androidx.annotation.StringRes
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.PolicyLink
import com.lavidanoesunbanano.app.core.designsystem.PrimaryButton
import com.lavidanoesunbanano.app.core.designsystem.SelectableRow

/** Autorizaciones de datos, una por una y sin marcar. Va antes del registro del teléfono. */
@Composable
fun ConsentScreen(onDone: (Any) -> Unit, viewModel: ConsentViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
        Text(text = stringResource(R.string.consent_title), style = LaVidaTheme.type.title, color = colors.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(text = stringResource(R.string.consent_intro), style = LaVidaTheme.type.small, color = colors.textSecondary)
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Item(R.string.consent_phone_usage_title, R.string.consent_phone_usage_desc, state.phoneUsage, viewModel::togglePhoneUsage)
            Item(R.string.consent_cross_border_title, R.string.consent_cross_border_desc, state.crossBorder, viewModel::toggleCrossBorder)
            Item(R.string.consent_excuses_title, R.string.consent_excuses_desc, state.excuses, viewModel::toggleExcuses)
            Item(R.string.consent_analytics_title, R.string.consent_analytics_desc, state.analytics, viewModel::toggleAnalytics)
        }
        Spacer(Modifier.height(12.dp))
        PolicyLink()
        Spacer(Modifier.height(16.dp))
        Text(text = stringResource(R.string.consent_age), style = LaVidaTheme.type.small, color = colors.textSecondary)
        if (!state.phoneUsage || !state.crossBorder) {
            Spacer(Modifier.height(8.dp))
            Text(text = stringResource(R.string.consent_required_hint), style = LaVidaTheme.type.small, color = colors.rejected)
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            text = stringResource(R.string.consent_continue),
            onClick = { viewModel.accept(onDone) },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canContinue,
        )
    }
}

@Composable
private fun Item(@StringRes title: Int, @StringRes description: Int, selected: Boolean, onClick: () -> Unit) {
    SelectableRow(
        title = stringResource(title),
        subtitle = stringResource(description),
        selected = selected,
        onClick = onClick,
        multiple = true,
    )
}
