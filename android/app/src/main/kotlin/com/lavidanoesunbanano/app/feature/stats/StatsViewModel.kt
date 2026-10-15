package com.lavidanoesunbanano.app.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lavidanoesunbanano.app.data.repository.HistoryRepository
import com.lavidanoesunbanano.app.domain.stats.Stats
import com.lavidanoesunbanano.app.domain.stats.StatsCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StatsUiState(
    val days: Int,
    val stats: Stats,
    /** Día que se lee arriba de la gráfica; por defecto, hoy (el último). */
    val selectedDay: Int,
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    history: HistoryRepository,
) : ViewModel() {

    private val period = MutableStateFlow(PERIODS.first())
    private val selected = MutableStateFlow<Int?>(null)

    /** `null` mientras carga. */
    val state: StateFlow<StatsUiState?> = combine(history.observe(), period, selected) { snapshot, days, selected ->
        StatsUiState(
            days = days,
            stats = StatsCalculator.compute(snapshot.attempts, snapshot.excuses, snapshot.today, days, snapshot.zone),
            selectedDay = selected?.coerceIn(0, days - 1) ?: (days - 1),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setPeriod(days: Int) {
        period.value = days
        selected.value = null
    }

    fun selectDay(index: Int) {
        selected.value = index
    }

    companion object {
        val PERIODS = listOf(7, HistoryRepository.MAX_DAYS)
    }
}
