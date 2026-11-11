package com.lavidanoesunbanano.app.feature.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.Panel
import com.lavidanoesunbanano.app.core.designsystem.PolicyLink
import com.lavidanoesunbanano.app.core.designsystem.PrimaryButton
import com.lavidanoesunbanano.app.core.designsystem.ScreenColumn
import com.lavidanoesunbanano.app.core.designsystem.SecondaryButton

/** Divulgación que Google Play exige antes de mandar a activar Accesibilidad; Atrás cuenta como rechazo. */
@Composable
fun AccessibilityDisclosure(onAccept: () -> Unit, onDecline: () -> Unit) {
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    BackHandler { onDecline() }
    ScreenColumn {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(text = stringResource(R.string.disclosure_title), style = type.title, color = colors.textPrimary)
            Text(text = stringResource(R.string.disclosure_what), style = type.body, color = colors.textBody)
            Panel {
                Text(text = stringResource(R.string.disclosure_data_title), style = type.subhead, color = colors.textPrimary)
                Text(text = stringResource(R.string.disclosure_data), style = type.small, color = colors.textBody)
                Text(text = stringResource(R.string.disclosure_use_title), style = type.subhead, color = colors.textPrimary)
                Text(text = stringResource(R.string.disclosure_use), style = type.small, color = colors.textBody)
            }
            PolicyLink()
            PrimaryButton(
                text = stringResource(R.string.disclosure_accept),
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                text = stringResource(R.string.disclosure_decline),
                onClick = onDecline,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
