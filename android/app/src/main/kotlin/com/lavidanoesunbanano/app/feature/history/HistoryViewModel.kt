package com.lavidanoesunbanano.app.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lavidanoesunbanano.app.data.repository.HistoryRepository
import com.lavidanoesunbanano.app.domain.stats.HistoryBuilder
import com.lavidanoesunbanano.app.domain.stats.HistoryDay
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val days: List<HistoryDay>,
    val today: LocalDate,
    val zone: ZoneId,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val history: HistoryRepository,
) : ViewModel() {

    /** `null` mientras carga. */
    val state: StateFlow<HistoryUiState?> = history.observe()
        .map { HistoryUiState(HistoryBuilder.build(it.attempts, it.excuses, it.zone), it.today, it.zone) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun markReviewed(excuseId: Long) {
        viewModelScope.launch { history.markReviewed(excuseId) }
    }
}
