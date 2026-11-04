package com.lavidanoesunbanano.app.domain.apps

import com.lavidanoesunbanano.app.domain.model.AppInfo
import java.text.Collator
import java.util.Locale

/** Ordena las apps instaladas: primero las que suelen robar tiempo, luego alfabético. */
object AppOrdering {
    private const val MAX_NAMED = 3

    /** "Instagram y TikTok"; con muchas, "Instagram, TikTok y 3 más". */
    fun names(labels: List<String>): String = when {
        labels.size <= 1 -> labels.joinToString()
        labels.size <= MAX_NAMED -> "${labels.dropLast(1).joinToString(", ")} y ${labels.last()}"
        else -> "${labels.take(MAX_NAMED - 1).joinToString(", ")} y ${labels.size - MAX_NAMED + 1} más"
    }

    val SUGGESTED = setOf(
        "com.instagram.android",
        "com.instagram.barcelona", // Threads
        "com.zhiliaoapp.musically", // TikTok
        "com.ss.android.ugc.trill", // TikTok (otras regiones)
        "com.google.android.youtube",
        "com.facebook.katana",
        "com.twitter.android",
        "com.reddit.frontpage",
        "com.netflix.mediaclient",
        "com.snapchat.android",
        "com.pinterest",
        "tv.twitch.android.app",
    )

    fun sort(apps: List<AppInfo>, locale: Locale = Locale.forLanguageTag("es")): List<AppInfo> {
        val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
        return apps
            .distinctBy { it.packageName }
            .sortedWith(
                compareBy<AppInfo> { if (it.packageName in SUGGESTED) 0 else 1 }
                    .thenComparator { a, b -> collator.compare(a.label, b.label) },
            )
    }

    fun filter(apps: List<AppInfo>, query: String): List<AppInfo> {
        val needle = query.trim()
        if (needle.isEmpty()) return apps
        return apps.filter {
            it.label.contains(needle, ignoreCase = true) || it.packageName.contains(needle, ignoreCase = true)
        }
    }
}
