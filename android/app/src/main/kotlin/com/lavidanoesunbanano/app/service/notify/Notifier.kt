package com.lavidanoesunbanano.app.service.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lavidanoesunbanano.app.MainActivity
import com.lavidanoesunbanano.app.R
import com.lavidanoesunbanano.app.domain.model.AppInfo
import com.lavidanoesunbanano.app.domain.schedule.ScheduleFormatter
import com.lavidanoesunbanano.app.domain.setup.ServiceHealth
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** Las dos notificaciones de la app: la cuenta regresiva de un desbloqueo y el bloqueo apagado. */
@Singleton
class Notifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    private val manager = NotificationManagerCompat.from(context)

    /** Silenciosa, con cronómetro hacia atrás; se quita sola cuando vence el desbloqueo. */
    fun showUnlock(app: AppInfo, until: Instant) {
        val remaining = Duration.between(clock.instant(), until).toMillis()
        if (remaining <= 0) return
        val time = ScheduleFormatter.time(LocalTime.ofInstant(until, clock.zone))
        notify(
            unlockId(app.packageName),
            NotificationCompat.Builder(context, CHANNEL_UNLOCKS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notify_unlock_title, app.label))
                .setContentText(context.getString(R.string.notify_unlock_text, time))
                .setWhen(until.toEpochMilli())
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setTimeoutAfter(remaining)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(openApp(openSetup = false))
                .build(),
        )
    }

    /** El servicio está apagado o Android lo detuvo: tocarla abre la guía. */
    fun showBlockerOff(health: ServiceHealth) {
        val title = if (health == ServiceHealth.STOPPED) R.string.notify_stopped_title else R.string.notify_off_title
        notify(
            BLOCKER_OFF_ID,
            NotificationCompat.Builder(context, CHANNEL_STATUS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(title))
                .setContentText(context.getString(R.string.notify_off_text))
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(openApp(openSetup = true))
                .build(),
        )
    }

    fun cancelBlockerOff() = manager.cancel(BLOCKER_OFF_ID)

    // Se revisa el permiso aquí mismo; sin él, Android descarta la notificación.
    @SuppressLint("MissingPermission")
    private fun notify(id: Int, notification: android.app.Notification) {
        if (!canNotify()) return
        ensureChannels()
        manager.notify(id, notification)
    }

    private fun canNotify(): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && manager.areNotificationsEnabled()
    }

    private fun ensureChannels() {
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_UNLOCKS, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.notify_channel_unlocks))
                    .setDescription(context.getString(R.string.notify_channel_unlocks_desc))
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_STATUS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.notify_channel_status))
                    .setDescription(context.getString(R.string.notify_channel_status_desc))
                    .build(),
            ),
        )
    }

    private fun openApp(openSetup: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_SETUP, openSetup)
        return PendingIntent.getActivity(
            context,
            if (openSetup) REQUEST_SETUP else REQUEST_HOME,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun unlockId(packageName: String): Int = UNLOCK_ID_BASE + (packageName.hashCode() and 0xFFFF)

    private companion object {
        const val CHANNEL_UNLOCKS = "desbloqueos"
        const val CHANNEL_STATUS = "estado"
        const val BLOCKER_OFF_ID = 1
        const val UNLOCK_ID_BASE = 1_000
        const val REQUEST_HOME = 0
        const val REQUEST_SETUP = 1
    }
}
