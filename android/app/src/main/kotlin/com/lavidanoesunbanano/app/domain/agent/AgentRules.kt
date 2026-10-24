package com.lavidanoesunbanano.app.domain.agent

import com.lavidanoesunbanano.app.domain.usage.UsageEvidence
import com.lavidanoesunbanano.app.domain.usage.UsageStatus
import java.time.Duration
import java.time.Instant

/** Reglas locales del agente: cuándo vale la pena llamarlo y qué órdenes se aceptan. */
object AgentRules {
    val WINDOW: Duration = Duration.ofMinutes(60)
    val THRESHOLD: Duration = Duration.ofMinutes(20)
    val COOLDOWN: Duration = Duration.ofMinutes(30)

    /** Las mismas duraciones que acepta el backend (banano/agent/policy.py). */
    val ALLOWED_MINUTES = setOf(5, 10, 15, 20, 30)

    /**
     * Revisión automática: solo si hay evidencia, no hay enfoque activo, pasó el enfriamiento
     * y las apps de la lista suman el umbral en la última hora. Así no se gasta el LLM en vano.
     */
    fun shouldAnalyze(
        evidence: UsageEvidence,
        listed: Set<String>,
        lastRunAt: Instant?,
        focusUntil: Instant?,
        now: Instant,
    ): Boolean {
        if (evidence.status != UsageStatus.OK) return false
        if (focusUntil != null && focusUntil > now) return false
        if (lastRunAt != null && Duration.between(lastRunAt, now) < COOLDOWN) return false
        val since = now.minus(WINDOW).toEpochMilli()
        val listedMillis = evidence.sessions
            .filter { it.packageName in listed }
            .sumOf { (minOf(it.endMillis, now.toEpochMilli()) - maxOf(it.startMillis, since)).coerceAtLeast(0) }
        return listedMillis >= THRESHOLD.toMillis()
    }

    /** El backend ya validó, pero el teléfono no ejecuta nada fuera de su propia lista. */
    fun acceptsOrder(minutes: Int, targets: List<String>, listed: Set<String>): Boolean =
        minutes in ALLOWED_MINUTES && targets.isNotEmpty() && targets.all { it in listed }
}
