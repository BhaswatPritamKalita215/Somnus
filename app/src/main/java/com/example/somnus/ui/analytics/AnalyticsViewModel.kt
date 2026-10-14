package com.example.somnus.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.somnus.data.repository.SleepAnalyticsRepository
import com.example.somnus.domain.analytics.AnalyticsReport
import com.example.somnus.domain.analytics.DateRangeOption
import com.example.somnus.domain.analytics.SleepDateRange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class AnalyticsUiState(
    val selectedOption: DateRangeOption = DateRangeOption.LAST_7_DAYS,
    val selectedRange: SleepDateRange = buildDateRange(DateRangeOption.LAST_7_DAYS),
    val report: AnalyticsReport? = null,
    val isLoading: Boolean = false,
    val selectedDayDetail: com.example.somnus.domain.analytics.DaySleepSummary? = null
)

private fun buildDateRange(
    option: DateRangeOption,
    customStart: LocalDate = LocalDate.now().minusDays(6),
    customEnd: LocalDate = LocalDate.now()
): SleepDateRange {
    val today = LocalDate.now()
    return when (option) {
        DateRangeOption.LAST_7_DAYS -> SleepDateRange(
            startDate = today.minusDays(6),
            endDate = today,
            label = "Last 7 Days",
            option = option
        )
        DateRangeOption.LAST_30_DAYS -> SleepDateRange(
            startDate = today.minusDays(29),
            endDate = today,
            label = "Last 30 Days",
            option = option
        )
        DateRangeOption.LAST_90_DAYS -> SleepDateRange(
            startDate = today.minusDays(89),
            endDate = today,
            label = "Last 90 Days",
            option = option
        )
        DateRangeOption.CUSTOM -> SleepDateRange(
            startDate = customStart,
            endDate = customEnd,
            label = "$customStart to $customEnd",
            option = option
        )
    }
}

class AnalyticsViewModel(
    private val analyticsRepository: SleepAnalyticsRepository
) : ViewModel() {

    private val _selectedOption = MutableStateFlow(DateRangeOption.LAST_7_DAYS)
    private val _customRange = MutableStateFlow<Pair<LocalDate, LocalDate>?>(null)
    private val _selectedDay = MutableStateFlow<com.example.somnus.domain.analytics.DaySleepSummary?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<AnalyticsUiState> = kotlinx.coroutines.flow.combine(
        _selectedOption,
        _customRange,
        _selectedDay
    ) { option, custom, selectedDay ->
        val range = if (option == DateRangeOption.CUSTOM && custom != null) {
            buildDateRange(option, custom.first, custom.second)
        } else {
            buildDateRange(option)
        }
        range to selectedDay
    }.flatMapLatest { (range, selectedDay) ->
        analyticsRepository.getAnalyticsReport(range).map { report ->
            AnalyticsUiState(
                selectedOption = range.option,
                selectedRange = range,
                report = report,
                isLoading = false,
                selectedDayDetail = selectedDay
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AnalyticsUiState()
    )

    fun selectDateRangeOption(option: DateRangeOption) {
        _selectedOption.value = option
        _selectedDay.value = null
    }

    fun setCustomDateRange(start: LocalDate, end: LocalDate) {
        if (!start.isAfter(end)) {
            _customRange.value = start to end
            _selectedOption.value = DateRangeOption.CUSTOM
            _selectedDay.value = null
        }
    }

    fun selectDayDetail(summary: com.example.somnus.domain.analytics.DaySleepSummary?) {
        _selectedDay.value = summary
    }

    companion object {
        fun provideFactory(
            analyticsRepository: SleepAnalyticsRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AnalyticsViewModel(analyticsRepository) as T
            }
        }
    }
}
