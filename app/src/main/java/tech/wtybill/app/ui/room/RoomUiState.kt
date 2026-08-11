package tech.wtybill.app.ui.room

import tech.wtybill.app.data.douyu.RoomInfo

sealed interface RoomUiState {
    data object Idle : RoomUiState
    data object Loading : RoomUiState
    data class Offline(val room: RoomInfo, val showTime: Long? = null) : RoomUiState
    data class Ready(val room: RoomInfo, val showTime: Long? = null) : RoomUiState
    data class Error(val message: String) : RoomUiState
}
