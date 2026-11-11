package com.lavidanoesunbanano.app.feature.setup

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

    // Google Play no deja pedir la exención directa a un bloqueador: se abre la lista del sistema.
    fun openBatterySettings(context: Context) =
        start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

    fun openUsageAccess(context: Context) =
        start(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    private fun packageUri(context: Context) = "package:${context.packageName}".toUri()

    private fun start(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context)))
        }
    }
}
