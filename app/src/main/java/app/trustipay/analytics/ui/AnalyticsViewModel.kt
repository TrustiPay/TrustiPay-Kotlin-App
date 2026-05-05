package app.trustipay.analytics.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trustipay.AppContainer
import app.trustipay.analytics.data.AnalyticsRepository
import app.trustipay.api.dto.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AnalyticsUiState(
    val isLoading: Boolean = false,
    val summary: SummaryReportResponse? = null,
    val categories: CategoriesReportResponse? = null,
    val fhs: FhsReportResponse? = null,
    val recommendations: RecommendationsReportResponse? = null,
    val profile: BehaviorProfileResponse? = null,
    val error: String? = null
)

class AnalyticsViewModel(
    private val repository: AnalyticsRepository = AppContainer.analyticsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                // Fetch all reports in parallel
                val summaryJob = launch { fetchSummary() }
                val categoriesJob = launch { fetchCategories() }
                val fhsJob = launch { fetchFhs() }
                val recsJob = launch { fetchRecommendations() }
                val profileJob = launch { fetchProfile() }
                
                // Wait for completion (optional, since they update state individually)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Unknown error occurred") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun fetchSummary() {
        repository.getSummary(groupBy = "day").onSuccess { data ->
            _uiState.update { it.copy(summary = data) }
        }.onFailure { e ->
            _uiState.update { it.copy(error = e.message) }
        }
    }

    private suspend fun fetchCategories() {
        repository.getCategories().onSuccess { data ->
            _uiState.update { it.copy(categories = data) }
        }.onFailure { e ->
            _uiState.update { it.copy(error = e.message) }
        }
    }

    private suspend fun fetchFhs() {
        repository.getFhs().onSuccess { data ->
            _uiState.update { it.copy(fhs = data) }
        }.onFailure { e ->
            _uiState.update { it.copy(error = e.message) }
        }
    }

    private suspend fun fetchRecommendations() {
        repository.getRecommendations().onSuccess { data ->
            _uiState.update { it.copy(recommendations = data) }
        }.onFailure { e ->
            _uiState.update { it.copy(error = e.message) }
        }
    }

    private suspend fun fetchProfile() {
        repository.getBehaviorProfile().onSuccess { data ->
            _uiState.update { it.copy(profile = data) }
        }.onFailure { e ->
            _uiState.update { it.copy(error = e.message) }
        }
    }
}
