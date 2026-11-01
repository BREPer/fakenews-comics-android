package com.fakenews.comics.ui.boot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fakenews.comics.data.net.ApiException
import com.fakenews.comics.data.net.ApiRunner
import com.fakenews.comics.data.net.FakenewsApi
import com.fakenews.comics.data.session.ServerStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface BootUiState {
    data object Loading : BootUiState
    data object NoServer : BootUiState
    data object NeedLogin : BootUiState
    data object Ready : BootUiState
    data class Error(val message: String) : BootUiState
}

@HiltViewModel
class BootViewModel @Inject constructor(
    private val serverStore: ServerStore,
    private val api: FakenewsApi,
    private val runner: ApiRunner,
) : ViewModel() {
    private val _state = MutableStateFlow<BootUiState>(BootUiState.Loading)
    val state: StateFlow<BootUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = BootUiState.Loading
            if (serverStore.baseUrl == null) {
                _state.value = BootUiState.NoServer
                return@launch
            }
            _state.value = try {
                // health 与 authStatus 互不依赖，**并行发**：串行时首屏要等 2×RTT
                // （弱网 RTT 150ms 就是 300ms+，NAS 唤醒那次实测更久），并行只等最慢的那个。
                // 语义与串行版保持一致：health 失败仍然报 Error（连不通就别谈登录），
                // auth 401 仍然走 NeedLogin —— 两个异常谁先抛都会被外层 catch 接住。
                coroutineScope {
                    val health = async { runner.call { api.health() } }
                    val auth = async { runner.call { api.authStatus() } }
                    health.await()
                    val a = auth.await()
                    if (a.enabled && !a.authenticated) {
                        BootUiState.NeedLogin
                    } else {
                        BootUiState.Ready
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.status == 401) BootUiState.NeedLogin else BootUiState.Error(e.message)
            } catch (e: Exception) {
                BootUiState.Error(e.message ?: "连接失败")
            }
        }
    }
}
