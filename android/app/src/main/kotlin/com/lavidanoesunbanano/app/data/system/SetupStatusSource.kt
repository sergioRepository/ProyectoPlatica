package com.lavidanoesunbanano.app.data.system

import android.content.Context
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.lavidanoesunbanano.app.domain.setup.SetupStatus
import com.lavidanoesunbanano.app.service.blocker.AccessibilityState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Fabricantes con ahorro de batería propio que necesita un paso a mano. */
enum class Manufacturer { SAMSUNG, XIAOMI, OTHER }

/** Lee del teléfono lo que la guía necesita saber. Se consulta al volver a la app. */
@Singleton
class SetupStatusSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun current(): SetupStatus = SetupStatus(
        sdk = Build.VERSION.SDK_INT,
        installedFromFile = installedFromFile(),
        service = AccessibilityState.health(context),
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        overlayAllowed = Settings.canDrawOverlays(context),
        batteryUnrestricted = context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true,
        usageAccessAllowed = usageAccessAllowed(context),
    )

    val manufacturer: Manufacturer
        get() = when (Build.MANUFACTURER.lowercase()) {
            "samsung" -> Manufacturer.SAMSUNG
            "xiaomi", "redmi", "poco" -> Manufacturer.XIAOMI
            else -> Manufacturer.OTHER
        }

    /**
     * Android 13+ restringe Accesibilidad a las apps instaladas desde un archivo. Por adb
     * no aplica, así que el paso solo se muestra cuando hace falta.
     */
    private fun installedFromFile(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return try {
            val source = context.packageManager.getInstallSourceInfo(context.packageName).packageSource
            source == PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE || source == PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE
        } catch (e: Exception) {
            false
        }
    }
}
