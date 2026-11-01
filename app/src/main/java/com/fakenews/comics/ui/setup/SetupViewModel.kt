package com.fakenews.comics.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fakenews.comics.data.net.ApiException
import com.fakenews.comics.data.net.ApiRunner
import com.fakenews.comics.data.net.FakenewsApi
import com.fakenews.comics.data.session.ServerStore
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

enum class SetupTarget { Login, Shelf }

data class SetupUiState(
    val url: String = "",
    val testing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val serverStore: ServerStore,
    private val api: FakenewsApi,
    private val runner: ApiRunner,
) : ViewModel() {
    private val _state = MutableStateFlow(SetupUiState(url = serverStore.savedUrl.orEmpty()))
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    private val _target = Channel<SetupTarget>(Channel.BUFFERED)
    val target = _target.receiveAsFlow()

    fun onUrlChange(value: String) {
        _state.update { it.copy(url = value, error = null) }
    }

    fun connect() {
        val current = _state.value
        if (current.testing || current.url.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(testing = true, error = null) }
            // 地址必须先落盘才能发请求（拦截器读的是 store 里的值），但只有健康检查
            // 通过才算数：输错端口/域名时若留着坏地址，下次启动会直接卡在错误页。
            val previous = serverStore.savedUrl
            var reached = false
            try {
                serverStore.save(current.url)
                runner.call { api.health() }
                val auth = runner.call { api.authStatus() }
                reached = true
                _target.send(
                    if (auth.enabled && !auth.authenticated) SetupTarget.Login else SetupTarget.Shelf,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                _state.update { it.copy(testing = false, error = e.message) }
            } catch (e: ApiException) {
                _state.update { it.copy(testing = false, error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(testing = false, error = e.message ?: "连接失败") }
            } finally {
                if (!reached) {
                    if (previous != null) serverStore.save(previous) else serverStore.clear()
                }
                _state.update { it.copy(testing = false) }
            }
        }
    }
}
