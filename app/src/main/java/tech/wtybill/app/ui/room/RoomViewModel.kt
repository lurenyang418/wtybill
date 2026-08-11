package tech.wtybill.app.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import tech.wtybill.app.config.AppConfig
import tech.wtybill.app.data.douyu.DouyuRepository
import tech.wtybill.app.data.douyu.RoomRepository

class RoomViewModel(private val repository: RoomRepository = DouyuRepository()) : ViewModel() {
    private val _state = MutableStateFlow<RoomUiState>(RoomUiState.Idle)
    val state: StateFlow<RoomUiState> = _state.asStateFlow()
    private var refreshJob: Job? = null
    private var refreshGeneration = 0L

    init { refresh() }

    fun refresh() {
        val generation = ++refreshGeneration
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.value = RoomUiState.Loading
            try {
                val (room, time) = coroutineScope {
                    val roomRequest = async { repository.loadRoom(AppConfig.ROOM_ID) }
                    val timeRequest = async {
                        try {
                            repository.loadShowTime(AppConfig.ROOM_ID)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            null
                        }
                    }
                    roomRequest.await() to timeRequest.await()
                }
                if (generation != refreshGeneration) return@launch
                _state.value = if (room.isLive) RoomUiState.Ready(room, time) else RoomUiState.Offline(room, time)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (generation != refreshGeneration) return@launch
                _state.value = RoomUiState.Error(error.message ?: "加载失败")
            }
        }
    }
}
