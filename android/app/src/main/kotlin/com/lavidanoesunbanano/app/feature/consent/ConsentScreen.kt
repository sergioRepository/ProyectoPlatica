package com.lavidanoesunbanano.app.feature.consent

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.CheckRow
import com.lavidanoesunbanano.app.core.designsystem.IconNotice
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.MonoLabel
import com.lavidanoesunbanano.app.core.designsystem.Panel
import com.lavidanoesunbanano.app.core.designsystem.PolicyLink
import com.lavidanoesunbanano.app.core.designsystem.PrimaryButton
import com.lavidanoesunbanano.app.core.designsystem.ScreenColumn

/** Autorizaciones de datos, una por una y sin marcar. Va antes del registro del teléfono. */
@Composable
fun ConsentScreen(onDone: (Any) -> Unit, viewModel: ConsentViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    ScreenColumn {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MonoLabel(stringResource(R.string.consent_brand))
                Text(text = stringResource(R.string.consent_title), style = type.hero, color = colors.textPrimary)
                Text(text = stringResource(R.string.consent_intro), style = type.body, color = colors.textBody)
            }
            IconNotice(R.drawable.ic_ui_heart, stringResource(R.string.consent_not_medical))
            Group(R.string.consent_section_required) {
                Item(R.string.consent_phone_usage_title, R.string.consent_phone_usage_desc, null, state.phoneUsage, viewModel::togglePhoneUsage)
                Item(R.string.consent_cross_border_title, R.string.consent_cross_border_desc, null, state.crossBorder, viewModel::toggleCrossBorder)
            }
            Group(R.string.consent_section_optional) {
                Item(
                    R.string.consent_excuses_title,
                    R.string.consent_excuses_desc,
                    R.string.consent_excuses_if_not,
                    state.excuses,
                    viewModel::toggleExcuses,
                )
                Item(
                    R.string.consent_analytics_title,
                    R.string.consent_analytics_desc,
                    R.string.consent_analytics_if_not,
                    state.analytics,
                    viewModel::toggleAnalytics,
                )
            }
            Text(text = stringResource(R.string.consent_payments), style = type.small, color = colors.textSecondary)
            Column {
                PolicyLink()
                Text(text = stringResource(R.string.consent_controller), style = type.small, color = colors.textSecondary)
            }
            Spacer(Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!state.phoneUsage || !state.crossBorder) {
                    Text(
                        text = stringResource(R.string.consent_required_hint),
                        modifier = Modifier.fillMaxWidth(),
                        style = type.caption,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
                PrimaryButton(
                    text = stringResource(R.string.consent_continue),
                    onClick = { viewModel.accept(onDone) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.canContinue,
                )
            }
        }
    }
}

@Composable
private fun Group(@StringRes title: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(title),
            style = LaVidaTheme.type.subhead,
            color = LaVidaTheme.colors.textPrimary,
        )
        content()
    }
}

@Composable
private fun Item(
    @StringRes title: Int,
    @StringRes description: Int,
    @StringRes ifNot: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Panel {
        CheckRow(
            title = stringResource(title),
            description = stringResource(description),
            checked = selected,
            onToggle = onClick,
            extra = ifNot?.let { res ->
                {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(stringResource(R.string.consent_if_not))
                            }
                            append(" ")
                            append(stringResource(res))
                        },
                        style = LaVidaTheme.type.caption,
                        color = LaVidaTheme.colors.textPrimary,
                    )
                }
            },
        )
    }
}
