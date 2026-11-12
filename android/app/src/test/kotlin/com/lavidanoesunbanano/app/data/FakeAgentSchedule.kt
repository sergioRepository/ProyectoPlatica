package com.lavidanoesunbanano.app.data

import com.lavidanoesunbanano.app.service.agent.AgentSchedule

/** Guarda cada llamada a `apply` para comprobarla en los tests. */
class FakeAgentSchedule : AgentSchedule {
    val calls = mutableListOf<Boolean>()

    override fun apply(enabled: Boolean) {
        calls += enabled
    }
}
