package com.lavidanoesunbanano.app.data.system

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.lavidanoesunbanano.app.domain.usage.UsageEvent
import com.lavidanoesunbanano.app.domain.usage.UsageEvidence
import com.lavidanoesunbanano.app.domain.usage.UsageSessions
import com.lavidanoesunbanano.app.domain.usage.UsageStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Acceso a datos de uso concedido en Ajustes. No es un permiso de los que se piden con diálogo. */
fun usageAccessAllowed(context: Context): Boolean {
    val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

/** Lee las sesiones de uso de las últimas 24 h. Solo paquetes y tiempos; nada de la pantalla. */
@Singleton
class UsageEvidenceSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    suspend fun collect(listed: Set<String>, window: Duration = WINDOW): UsageEvidence =
        withContext(Dispatchers.IO) { read(listed, window) }

    private fun read(listed: Set<String>, window: Duration): UsageEvidence {
        val now = clock.millis()
        if (!usageAccessAllowed(context)) return UsageEvidence(UsageStatus.PERMISSION_MISSING, now)
        return try {
            val manager = context.getSystemService(UsageStatsManager::class.java)
                ?: return UsageEvidence(UsageStatus.ERROR, now)
            val events = manager.queryEvents(now - window.toMillis(), now)
            val simplified = mutableListOf<UsageEvent>()
            val event = UsageEvents.Event()
            while (events.getNextEvent(event)) {
                val resumed = when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> true
                    UsageEvents.Event.ACTIVITY_PAUSED -> false
                    else -> continue
                }
                if (event.packageName == context.packageName) continue
                simplified += UsageEvent(event.packageName, event.timeStamp, resumed)
            }
            val (sessions, truncated) = UsageSessions.minimize(UsageSessions.fromEvents(simplified, now), listed)
            UsageEvidence(UsageStatus.OK, now, sessions, truncated)
        } catch (e: SecurityException) {
            UsageEvidence(UsageStatus.PERMISSION_MISSING, now)
        }
    }

    /** Nombre visible y categoría de Android ("video", "social"...); vacíos si no se sabe. */
    fun describe(packageName: String): Pair<String, String> = try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString() to category(info.category)
    } catch (e: Exception) {
        packageName to ""
    }

    private fun category(value: Int): String = when (value) {
        android.content.pm.ApplicationInfo.CATEGORY_GAME -> "game"
        android.content.pm.ApplicationInfo.CATEGORY_AUDIO -> "audio"
        android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> "video"
        android.content.pm.ApplicationInfo.CATEGORY_IMAGE -> "image"
        android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> "social"
        android.content.pm.ApplicationInfo.CATEGORY_NEWS -> "news"
        android.content.pm.ApplicationInfo.CATEGORY_MAPS -> "maps"
        android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY -> "productivity"
        else -> ""
    }

    private companion object {
        val WINDOW: Duration = Duration.ofHours(24)
    }
}
