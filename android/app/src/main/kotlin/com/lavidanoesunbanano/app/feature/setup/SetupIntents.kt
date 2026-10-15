package com.lavidanoesunbanano.app.feature.setup

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

/** Abre la pantalla exacta del sistema para cada paso; si no existe, la info de la app. */
object SetupIntents {

    fun openAppDetails(context: Context) =
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context)))

    fun openAccessibility(context: Context) =
        start(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openNotificationSettings(context: Context) = start(
        context,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    )

    fun openOverlay(context: Context) =
        start(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri(context)))

    // La app no se publica en Play Store: pedir la exención directamente es lo más claro.
    @SuppressLint("BatteryLife")
    fun requestBatteryExemption(context: Context) =
        start(context, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context)))

    private fun packageUri(context: Context) = "package:${context.packageName}".toUri()

    private fun start(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context)))
        }
    }
}
