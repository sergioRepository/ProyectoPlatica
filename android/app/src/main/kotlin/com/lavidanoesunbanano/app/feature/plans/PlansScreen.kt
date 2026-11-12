package com.lavidanoesunbanano.app.feature.plans

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.core.designsystem.InfoPill
import com.lavidanoesunbanano.app.core.designsystem.LaVidaTheme
import com.lavidanoesunbanano.app.core.designsystem.MonoLabel
import com.lavidanoesunbanano.app.core.designsystem.Notice
import com.lavidanoesunbanano.app.core.designsystem.NoticeTone
import com.lavidanoesunbanano.app.core.designsystem.Panel
import com.lavidanoesunbanano.app.core.designsystem.PolicyLink
import com.lavidanoesunbanano.app.core.designsystem.PrimaryButton
import com.lavidanoesunbanano.app.core.designsystem.RowDivider
import com.lavidanoesunbanano.app.core.designsystem.ScreenColumn
import com.lavidanoesunbanano.app.core.designsystem.SmallButton
import com.lavidanoesunbanano.app.core.designsystem.TextLink
import com.lavidanoesunbanano.app.core.designsystem.dashed
import com.lavidanoesunbanano.app.core.designsystem.toon
import com.lavidanoesunbanano.app.data.billing.BillingRules
import com.lavidanoesunbanano.app.data.billing.PurchaseStatus
import com.lavidanoesunbanano.app.data.billing.StorePrice
import com.lavidanoesunbanano.app.data.billing.StoreProduct
import com.lavidanoesunbanano.app.domain.billing.PlanTier
import com.lavidanoesunbanano.app.domain.schedule.ScheduleFormatter
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Currency

@Composable
fun PlansScreen(viewModel: PlansViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val uriHandler = LocalUriHandler.current
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    val billing = state.billing
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val selectedPrice = billing.prices[storeProduct(state.selected)]
    val action = primaryAction(state.validated, state.device?.subscriptionState, state.selected, selectedPrice, billing.fallback)
    val manage = { uriHandler.openUri(manageUrl(state.validated, state.device?.subscriptionState)) }

    ScreenColumn {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MonoLabel(stringResource(R.string.plans_breadcrumb))
            Text(text = stringResource(R.string.plans_title), style = type.screenTitle, color = colors.textPrimary)
            Text(text = stringResource(R.string.plans_subtitle), style = type.body, color = colors.textBody)

            SubscriptionNotice(state.device?.subscriptionState, state.device?.expiryTime, manage)
            PurchaseNotice(billing.purchase, onRetry = { activity?.let(viewModel::retry) }, onDismiss = viewModel::dismissStatus)
            if (billing.fallback) {
                Notice(tone = NoticeTone.NOTE) {
                    Text(text = stringResource(R.string.plans_debug_prices), style = type.small, color = colors.textBody)
                }
            }

            AllInclude()

            Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val monthly = billing.prices[StoreProduct.MONTHLY]
                PlanTier.entries.forEach { tier ->
                    val price = if (tier.isPaid) billing.prices[storeProduct(tier)] else null
                    PlanCard(
                        tier = tier,
                        selected = state.selected == tier,
                        isYourPlan = state.validated == tier,
                        price = price,
                        savings = if (tier == PlanTier.SEMESTER && monthly != null && price != null) BillingRules.savingsPercent(monthly, price) else null,
                        onSelect = { viewModel.select(tier) },
                    )
                }
            }

            PrimaryButton(
                text = when (action) {
                    is PrimaryAction.Subscribe -> subscribeLabel(selectedPrice)
                    is PrimaryAction.Change -> {
                        val changePeriod = periodLabel(selectedPrice?.billingPeriod)
                        if (selectedPrice != null && changePeriod != null) {
                            stringResource(R.string.plans_change_to_price, stringResource(state.selected.nameRes), selectedPrice.formatted, stringResource(changePeriod))
                        } else {
                            stringResource(R.string.plans_change_to, stringResource(state.selected.nameRes))
                        }
                    }
                    PrimaryAction.Current -> stringResource(R.string.plans_current)
                    PrimaryAction.Manage -> stringResource(R.string.plans_manage)
                    PrimaryAction.Unavailable -> stringResource(R.string.plans_unavailable)
                },
                onClick = { if (action == PrimaryAction.Manage) manage() else activity?.let(viewModel::onPrimary) },
                modifier = Modifier.fillMaxWidth(),
                enabled = action != PrimaryAction.Current && action != PrimaryAction.Unavailable && !billing.busy,
            )

            if (action is PrimaryAction.Change && selectedPrice != null) {
                Text(
                    text = stringResource(
                        if (state.selected == PlanTier.SEMESTER) R.string.plans_change_upgrade else R.string.plans_change_downgrade,
                        selectedPrice.formatted,
                    ),
                    style = type.small,
                    color = colors.textBody,
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Siempre visibles; con Gratis seleccionado se muestran los datos del plan Mensual.
                val termsPrice = if (state.selected.isPaid) selectedPrice else billing.prices[StoreProduct.MONTHLY]
                val period = periodLabel(termsPrice?.billingPeriod)
                if (termsPrice != null && period != null) {
                    Terms(stringResource(R.string.plans_terms_price, termsPrice.formatted, stringResource(period)))
                }
                Terms(stringResource(R.string.plans_terms_renewal))
                Terms(stringResource(R.string.plans_terms_cancel))
                TextLink(text = stringResource(R.string.plans_manage_link), onClick = manage)
                PolicyLink()
                val termsUrl = stringResource(R.string.terms_url)
                TextLink(text = stringResource(R.string.plans_terms_link), onClick = { uriHandler.openUri(termsUrl) })
                TextLink(text = stringResource(R.string.plans_credits_link), onClick = viewModel::toggleCredits)
            }

            if (state.creditsOpen) {
                CreditsSection(
                    balance = state.device?.credits,
                    price = billing.prices[StoreProduct.CREDITS],
                    canBuy = !billing.fallback && !billing.busy,
                    onBuy = { activity?.let(viewModel::buyCredits) },
                )
            }
        }
    }
}

@Composable
private fun subscribeLabel(price: StorePrice?): String {
    val period = periodLabel(price?.billingPeriod)
    return if (price != null && period != null) {
        stringResource(R.string.plans_subscribe, price.formatted, stringResource(period))
    } else {
        stringResource(R.string.plans_unavailable)
    }
}

@Composable
private fun Terms(text: String) {
    Text(text = text, style = LaVidaTheme.type.small, color = LaVidaTheme.colors.textBody, textAlign = TextAlign.Center)
}

@Composable
private fun AllInclude() {
    val colors = LaVidaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .dashed(RoundedCornerShape(14.dp), colors.border)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MonoLabel(stringResource(R.string.plans_all_include))
        Benefit(true, stringResource(R.string.plans_all_block))
        Benefit(true, stringResource(R.string.plans_all_emergency))
    }
}

@Composable
private fun Benefit(ok: Boolean, text: String) {
    val colors = LaVidaTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = if (ok) "✓" else "✕",
            style = LaVidaTheme.type.small.copy(fontWeight = FontWeight.ExtraBold),
            color = if (ok) colors.approved else colors.rejected,
        )
        Text(text = text, modifier = Modifier.weight(1f), style = LaVidaTheme.type.small, color = colors.textBody)
    }
}

@Composable
private fun PlanCard(
    tier: PlanTier,
    selected: Boolean,
    isYourPlan: Boolean,
    price: StorePrice?,
    savings: Int?,
    onSelect: () -> Unit,
) {
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .toon(shape, if (selected) colors.accent else colors.surface, colors.border, shadow = if (selected) 4.dp else 2.dp)
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            RadioMark(selected)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = stringResource(tier.nameRes), style = type.section, color = colors.textPrimary)
                    if (isYourPlan) {
                        Text(
                            text = stringResource(R.string.plans_your_plan),
                            modifier = Modifier
                                .background(colors.border, CircleShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            style = type.mono.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.accent,
                        )
                    }
                }
                Text(
                    text = stringResource(
                        when (tier) {
                            PlanTier.FREE -> R.string.plans_free_subtitle
                            PlanTier.MONTHLY -> R.string.plans_monthly_subtitle
                            PlanTier.SEMESTER -> R.string.plans_semester_subtitle
                        },
                    ),
                    style = type.small,
                    color = if (selected) colors.textBody else colors.textSecondary,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = when {
                        tier == PlanTier.FREE -> stringResource(R.string.plans_free_price)
                        price != null -> price.formatted
                        else -> stringResource(R.string.plans_price_unavailable)
                    },
                    style = type.figure,
                    color = colors.textPrimary,
                )
                if (tier == PlanTier.SEMESTER && price != null) {
                    equivalentMonthly(price)?.let {
                        Text(text = stringResource(R.string.plans_semester_equivalent, it), style = type.caption, color = colors.textBody)
                    }
                }
                if (savings != null) InfoPill(stringResource(R.string.plans_savings, savings))
            }
        }
        RowDivider()
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (tier == PlanTier.FREE) {
                Benefit(true, stringResource(R.string.plans_benefit_evaluations, tier.dailyEvaluations))
                Benefit(false, stringResource(R.string.plans_benefit_no_review))
                Benefit(false, stringResource(R.string.plans_benefit_credits_only))
            } else {
                Benefit(true, stringResource(R.string.plans_benefit_evaluations_up_to, tier.dailyEvaluations))
                Benefit(tier.autoReview, stringResource(R.string.plans_benefit_review))
                Benefit(true, stringResource(R.string.plans_benefit_included, tier.includedAccesses))
            }
        }
    }
}

/** Círculo de selección de 26 dp, con punto de tinta cuando está marcado. */
@Composable
private fun RadioMark(selected: Boolean) {
    val colors = LaVidaTheme.colors
    Box(
        modifier = Modifier
            .size(26.dp)
            .background(colors.surface, CircleShape)
            .border(2.dp, colors.border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(12.dp).background(colors.border, CircleShape))
    }
}

/** Equivalente mensual del precio de la tienda, en su misma moneda; `null` si no se puede calcular. */
private fun equivalentMonthly(price: StorePrice): String? {
    val micros = BillingRules.monthlyMicros(price) ?: return null
    return runCatching {
        NumberFormat.getCurrencyInstance().apply { currency = Currency.getInstance(price.currencyCode) }.format(micros / 1_000_000.0)
    }.getOrNull()
}

@Composable
private fun SubscriptionNotice(subscriptionState: String?, expiryTime: String?, onManage: () -> Unit) {
    val text = when (subscriptionState) {
        "SUBSCRIPTION_STATE_IN_GRACE_PERIOD" -> stringResource(R.string.plans_notice_grace)
        "SUBSCRIPTION_STATE_ON_HOLD" -> stringResource(R.string.plans_notice_hold)
        "SUBSCRIPTION_STATE_CANCELED" -> expiryDate(expiryTime)?.let { stringResource(R.string.plans_notice_canceled, it) }
        "SUBSCRIPTION_STATE_EXPIRED" -> stringResource(R.string.plans_notice_expired)
        else -> null
    } ?: return
    val broken = subscriptionState == "SUBSCRIPTION_STATE_IN_GRACE_PERIOD" || subscriptionState == "SUBSCRIPTION_STATE_ON_HOLD"
    val needsManage = broken || subscriptionState == "SUBSCRIPTION_STATE_CANCELED"
    Notice(tone = if (broken) NoticeTone.ERROR else NoticeTone.INFO) {
        Text(text = text, style = LaVidaTheme.type.small, color = LaVidaTheme.colors.textPrimary)
        if (needsManage) TextLink(text = stringResource(R.string.plans_manage_link), onClick = onManage)
    }
}

private fun expiryDate(iso: String?): String? = iso?.let {
    runCatching { ScheduleFormatter.shortDate(OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()) }.getOrNull()
}

@Composable
private fun PurchaseNotice(status: PurchaseStatus, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val (tone, text) = when (status) {
        PurchaseStatus.Idle, is PurchaseStatus.Launching -> return
        PurchaseStatus.Pending -> NoticeTone.INFO to stringResource(R.string.plans_status_pending)
        PurchaseStatus.Cancelled -> NoticeTone.PLAIN to stringResource(R.string.plans_status_cancelled)
        is PurchaseStatus.Failed ->
            NoticeTone.ERROR to stringResource(if (status.retryable) R.string.plans_status_failed else R.string.plans_status_rejected)
        is PurchaseStatus.Verified -> NoticeTone.SUCCESS to stringResource(
            when (status.outcome) {
                "subscription" -> R.string.plans_status_done
                "granted", "already_granted" -> R.string.plans_status_credits
                else -> R.string.plans_status_received
            },
        )
    }
    Notice(tone = tone) {
        Text(text = text, style = LaVidaTheme.type.small, color = LaVidaTheme.colors.textPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (status is PurchaseStatus.Failed && status.retryable) {
                SmallButton(text = stringResource(R.string.plans_retry), onClick = onRetry)
            }
            // Una compra pendiente sigue abierta en Google Play: no se descarta, para no comprar dos veces.
            if (status != PurchaseStatus.Pending) {
                SmallButton(text = stringResource(R.string.action_accept), onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun CreditsSection(balance: Int?, price: StorePrice?, canBuy: Boolean, onBuy: () -> Unit) {
    val colors = LaVidaTheme.colors
    val type = LaVidaTheme.type
    Panel(spacing = 10.dp) {
        Text(text = stringResource(R.string.plans_credits_title), style = type.section, color = colors.textPrimary)
        listOf(
            R.string.plans_credits_rule_1, R.string.plans_credits_rule_2, R.string.plans_credits_rule_3, R.string.plans_credits_rule_4,
            R.string.plans_credits_rule_5, R.string.plans_credits_rule_6, R.string.plans_credits_rule_7, R.string.plans_credits_rule_8,
        ).forEach { rule ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "•", style = type.small, color = colors.textBody)
                Text(text = stringResource(rule), modifier = Modifier.weight(1f), style = type.small, color = colors.textBody)
            }
        }
        if (balance != null) {
            Text(
                text = pluralStringResource(R.plurals.plans_credits_balance, balance, balance),
                style = type.small.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
            )
        }
        Notice(tone = NoticeTone.INFO) {
            Text(text = stringResource(R.string.plans_credits_rule_8), style = type.small, color = colors.textPrimary)
        }
        PrimaryButton(
            text = stringResource(R.string.plans_buy_credits, price?.formatted ?: stringResource(R.string.plans_price_unavailable)),
            onClick = onBuy,
            modifier = Modifier.fillMaxWidth(),
            enabled = price != null && canBuy,
        )
    }
}
