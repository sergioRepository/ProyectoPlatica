package com.lavidanoesunbanano.app.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import com.lavidanoesunbanano.app.domain.apps.AppOrdering
import com.lavidanoesunbanano.app.domain.model.AppInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Apps con ícono en el launcher. Basta la consulta `<queries>` del manifiesto, sin pedir
 * QUERY_ALL_PACKAGES.
 */
@Singleton
class InstalledAppsSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun launchableApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
        val apps = activities
            .map { AppInfo(it.activityInfo.packageName, it.loadLabel(packageManager).toString()) }
            .filter { it.packageName != context.packageName }
        AppOrdering.sort(apps)
    }
}
