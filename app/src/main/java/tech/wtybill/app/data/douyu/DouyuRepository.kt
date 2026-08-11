package tech.wtybill.app.data.douyu

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.wtybill.app.net.NetworkClients

interface RoomRepository {
    suspend fun loadRoom(roomId: String): RoomInfo
    suspend fun loadShowTime(roomId: String): Long?
}

class DouyuRepository : RoomRepository {
    private val api = DouyuApi(NetworkClients.base)

    override suspend fun loadRoom(roomId: String): RoomInfo = withContext(Dispatchers.IO) { api.room(roomId) }
    override suspend fun loadShowTime(roomId: String): Long? = withContext(Dispatchers.IO) { api.showTime(roomId) }
}
