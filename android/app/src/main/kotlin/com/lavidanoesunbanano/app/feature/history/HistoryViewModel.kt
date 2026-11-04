package com.lavidanoesunbanano.app.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lavidanoesunbanano.app.data.repository.HistoryRepository
import com.lavidanoesunbanano.app.domain.model.ExcuseRecord
import com.lavidanoesunbanano.app.domain.stats.HistoryBuilder
import com.lavidanoesunbanano.app.domain.stats.HistoryDay
import com.lavidanoesunbanano.app.domain.stats.HistoryFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val days: List<HistoryDay>,
    val today: LocalDate,
    val zone: ZoneId,
    val filter: HistoryFilter = HistoryFilter.ALL,
    /** Emergencias por revisar: salen arriba, sin importar el filtro. */
    val toReview: List<ExcuseRecord> = emptyList(),
    /** Se acaba de marcar una: confirma que no se perdió. */
    val justReviewed: Boolean = false,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val history: HistoryRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter.ALL)
    private val justReviewed = MutableStateFlow(false)

    /** `null` mientras carga. */
    val state: StateFlow<HistoryUiState?> = combine(history.observe(), filter, justReviewed) { snapshot, filter, reviewed ->
        HistoryUiState(
            days = HistoryBuilder.filter(HistoryBuilder.build(snapshot.attempts, snapshot.excuses, snapshot.zone), filter),
            today = snapshot.today,
            zone = snapshot.zone,
            filter = filter,
            toReview = snapshot.excuses.filter { it.needsReview },
            justReviewed = reviewed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setFilter(value: HistoryFilter) {
        filter.value = value
    }

    fun markReviewed(excuseId: Long) {
        justReviewed.value = true
        viewModelScope.launch { history.markReviewed(excuseId) }
    }
}
