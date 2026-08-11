package tech.wtybill.app.danmaku

/** Bounded UI handoff buffer; transport callbacks never retain an unbounded history. */
class DanmakuBuffer(private val capacity: Int = 100) {
    private val messages = ArrayDeque<DanmakuMessage>()

    init { require(capacity > 0) }

    @Synchronized
    fun add(message: DanmakuMessage) {
        messages.addLast(message)
        while (messages.size > capacity) messages.removeFirst()
    }

    @Synchronized
    fun snapshot(): List<DanmakuMessage> = messages.toList()

    @Synchronized
    fun clear() = messages.clear()
}
