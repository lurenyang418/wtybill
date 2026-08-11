package tech.wtybill.app.danmaku

import org.junit.Assert.assertEquals
import org.junit.Test

class DanmakuBufferTest {
    @Test fun keepsOnlyConfiguredRecentMessages() {
        val buffer = DanmakuBuffer(2)
        buffer.add(DanmakuMessage("a", "one", 1))
        buffer.add(DanmakuMessage("b", "two", 2))
        buffer.add(DanmakuMessage("c", "three", 3))
        assertEquals(listOf("two", "three"), buffer.snapshot().map { it.text })
    }

    @Test fun allocatorReplacesOnlyOneTrackSnapshotPerMessage() {
        val allocator = DanmakuTrackAllocator(trackCount = 2, maxMessagesPerTrack = 1)
        val first = allocator.add(DanmakuMessage("a", "one", 1))
        val second = allocator.add(DanmakuMessage("b", "two", 2))
        assertEquals(listOf("one"), first[0].messages.map { it.text })
        assertEquals(listOf("two"), second[1].messages.map { it.text })
    }

    @Test fun allocatorAssignsDistinctIdsToDuplicateMessages() {
        val allocator = DanmakuTrackAllocator(trackCount = 1, maxMessagesPerTrack = 2)
        val first = allocator.add(DanmakuMessage("a", "same", 1)).single().messages.single()
        val second = allocator.add(DanmakuMessage("a", "same", 1)).single().messages.last()
        assertEquals(listOf("same", "same"), allocator.snapshot().single().messages.map { it.text })
        assert(first.id != second.id)
    }
}
