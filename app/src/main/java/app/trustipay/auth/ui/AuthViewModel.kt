package app.trustipay.auth.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.trustipay.AppContainer
import app.trustipay.api.ApiResult
import app.trustipay.auth.domain.AuthToken
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

sealed class AuthNavEvent {
    object NavigateToHome : AuthNavEvent()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val repository get() = AppContainer.authRepository

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _navEvents = Channel<AuthNavEvent>(Channel.BUFFERED)
    val navEvents = _navEvents.receiveAsFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            when (val result = repository.login(email.trim(), password)) {
                is ApiResult.Success -> {
                    _uiState.value = AuthUiState.Idle
                    _navEvents.send(AuthNavEvent.NavigateToHome)
                }
                is ApiResult.HttpError -> _uiState.value = AuthUiState.Error(
                    if (result.code == 401) "Invalid email or password" else result.message
                )
                is ApiResult.NetworkError -> _uiState.value = AuthUiState.Error(result.cause.message ?: "Network error — check your connection")
                ApiResult.AuthError -> _uiState.value = AuthUiState.Error("Invalid email or password")
            }
        }
    }

    fun register(fullName: String, email: String, phone: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            when (val result = repository.register(fullName, email.trim(), phone.trim(), password)) {
                is ApiResult.Success -> {
                    _uiState.value = AuthUiState.Idle
                    _navEvents.send(AuthNavEvent.NavigateToHome)
                }
                is ApiResult.HttpError -> _uiState.value = AuthUiState.Error(result.message)
                is ApiResult.NetworkError -> _uiState.value = AuthUiState.Error(result.cause.message ?: "Network error — check your connection")
                ApiResult.AuthError -> _uiState.value = AuthUiState.Error("Authentication error")
            }
        }
    }

    fun logout() {
        repository.logout()
    }

    fun bypassLogin() {
        viewModelScope.launch {
            val dummyToken = AuthToken(
                accessToken = "dummy_token",
                refreshToken = "dummy_refresh",
                expiresAt = System.currentTimeMillis() / 1000 + 36000,
                userId = "test-user-id",
                displayName = "Test User"
            )
            AppContainer.tokenStore.save(dummyToken)
            _navEvents.send(AuthNavEvent.NavigateToHome)
        }
    }

    fun clearError() {
        _uiState.value = AuthUiState.Idle
    }
}
