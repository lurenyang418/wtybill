package tech.wtybill.app.danmaku

import androidx.compose.runtime.Immutable

@Immutable
data class DanmakuTrackSnapshot(val messages: List<DanmakuMessage>)

class DanmakuTrackAllocator(
    private val trackCount: Int = 6,
    private val maxMessagesPerTrack: Int = 6,
) {
    private val tracks = Array(trackCount) { ArrayDeque<DanmakuMessage>() }
    private val snapshots = Array(trackCount) { DanmakuTrackSnapshot(emptyList()) }
    private var nextTrack = 0
    private var nextMessageId = 1L

    init {
        require(trackCount > 0)
        require(maxMessagesPerTrack > 0)
    }

    @Synchronized
    fun add(message: DanmakuMessage): List<DanmakuTrackSnapshot> {
        val index = nextTrack++ % trackCount
        tracks[index].addLast(message.copy(id = nextMessageId++))
        while (tracks[index].size > maxMessagesPerTrack) tracks[index].removeFirst()
        snapshots[index] = DanmakuTrackSnapshot(tracks[index].toList())
        return snapshots.toList()
    }

    @Synchronized
    fun snapshot(): List<DanmakuTrackSnapshot> = snapshots.toList()

    @Synchronized
    fun clear() {
        tracks.forEach { it.clear() }
        for (index in snapshots.indices) snapshots[index] = DanmakuTrackSnapshot(emptyList())
        nextTrack = 0
        nextMessageId = 1L
    }
}
