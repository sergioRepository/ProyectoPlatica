package com.lavidanoesunbanano.app.feature.status

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.domain.model.BackendStatus

@Composable
fun StatusScreen(viewModel: StatusViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StatusContent(state = state, onRetry = viewModel::refresh)
}

@Composable
private fun StatusContent(state: StatusUiState, onRetry: () -> Unit) {
    val colors = LaVidaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.app_name),
                color = colors.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.status_subtitle),
                color = colors.textSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, MaterialTheme.shapes.medium)
                .border(1.dp, colors.border, MaterialTheme.shapes.medium)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusRow(
                label = stringResource(R.string.status_backend_label),
                value = backendText(state.backend),
            )
            StatusRow(
                label = stringResource(R.string.status_url_label),
                value = state.baseUrl.ifBlank { stringResource(R.string.status_not_configured) },
            )
            StatusRow(
                label = stringResource(R.string.status_template_label),
                value = pluralStringResource(
                    R.plurals.status_template_messages,
                    state.templateMessages,
                    state.templateMessages,
                ),
            )
        }
        OutlinedButton(
            onClick = onRetry,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, colors.border),
        ) {
            Text(text = stringResource(R.string.status_retry), color = colors.textPrimary)
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    val colors = LaVidaTheme.colors
    Row {
        Text(
            text = label,
            modifier = Modifier.width(88.dp),
            color = colors.textSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
        Text(
            text = value,
            color = colors.textPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
    }
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
private fun StatusContentPreview() {
    LaVidaTheme {
        StatusContent(
            state = StatusUiState(
                backend = BackendStatus.Online("0.1.0"),
                baseUrl = "http://127.0.0.1:8000/",
                templateMessages = 0,
            ),
            onRetry = {},
        )
    }
}
