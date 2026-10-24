package com.lavidanoesunbanano.app.domain.usage

/** Un evento de UsageStatsManager ya simplificado: la app pasó al frente o salió de él. */
data class UsageEvent(val packageName: String, val atMillis: Long, val resumed: Boolean)

/** Un rato continuo de una app al frente. */
data class UsageSession(val packageName: String, val startMillis: Long, val endMillis: Long) {
    val minutes: Double get() = (endMillis - startMillis) / 60_000.0
}

enum class UsageStatus(val wire: String) { OK("ok"), PERMISSION_MISSING("permission_missing"), ERROR("error") }

/** Lo que el teléfono le manda al agente como evidencia. `sessions` vacía con OK es cero uso. */
data class UsageEvidence(
    val status: UsageStatus,
    val collectedAtMillis: Long,
    val sessions: List<UsageSession> = emptyList(),
    val truncated: Boolean = false,
)

object UsageSessions {

    /**
     * Empareja RESUMED con su PAUSED. Una app al frente sin cierre se corta en `now`, y un
     * RESUMED de otra app cierra la anterior: Android no siempre manda el PAUSED.
     */
    fun fromEvents(events: List<UsageEvent>, now: Long): List<UsageSession> {
        val sessions = mutableListOf<UsageSession>()
        var open: UsageEvent? = null
        for (event in events.sortedBy { it.atMillis }) {
            val current = open
            if (event.resumed) {
                if (current != null && current.packageName != event.packageName) {
                    sessions += UsageSession(current.packageName, current.atMillis, event.atMillis)
                }
                // La misma app reanudada sigue siendo la misma sesión.
                if (current?.packageName != event.packageName) open = event
            } else if (current != null && current.packageName == event.packageName) {
                sessions += UsageSession(current.packageName, current.atMillis, event.atMillis)
                open = null
            }
        }
        open?.let { sessions += UsageSession(it.packageName, it.atMillis, now) }
        return sessions.filter { it.endMillis > it.startMillis }
    }

    /**
     * Minimización: solo las apps de la lista y las `otherApps` con más uso, y como mucho
     * `maxSessions`, las más recientes. `truncated` dice si se recortaron sesiones.
     */
    fun minimize(
        sessions: List<UsageSession>,
        listed: Set<String>,
        otherApps: Int = 5,
        maxSessions: Int = 300,
    ): Pair<List<UsageSession>, Boolean> {
        val topOthers = sessions.filter { it.packageName !in listed }
            .groupBy { it.packageName }
            .entries.sortedByDescending { (_, s) -> s.sumOf { it.endMillis - it.startMillis } }
            .take(otherApps).map { it.key }.toSet()
        val kept = sessions.filter { it.packageName in listed || it.packageName in topOthers }
        val newest = kept.sortedByDescending { it.startMillis }.take(maxSessions)
        return newest.sortedBy { it.startMillis } to (kept.size > maxSessions)
    }
}
