package com.fakenews.comics.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fakenews.comics.data.model.LoginRequest
import com.fakenews.comics.data.net.ApiException
import com.fakenews.comics.data.net.ApiRunner
import com.fakenews.comics.data.net.FakenewsApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val api: FakenewsApi,
    private val runner: ApiRunner,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _loggedIn = Channel<Unit>(Channel.BUFFERED)
    val loggedIn = _loggedIn.receiveAsFlow()

    fun onUsernameChange(value: String) {
        _state.update { it.copy(username = value, error = null) }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value, error = null) }
    }

    fun login() {
        val current = _state.value
        if (current.submitting) return
        if (current.username.isBlank() || current.password.isEmpty()) {
            _state.update { it.copy(error = "请输入用户名和密码") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            try {
                runner.call {
                    api.login(LoginRequest(current.username.trim(), current.password))
                }
                _loggedIn.send(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                val message = when (e.status) {
                    401 -> "用户名或密码错误"
                    429 -> "尝试次数过多，请稍后再试"
                    else -> e.message
                }
                _state.update { it.copy(submitting = false, error = message) }
            } catch (e: Exception) {
                _state.update { it.copy(submitting = false, error = e.message ?: "登录失败") }
            }
        }
    }
}
