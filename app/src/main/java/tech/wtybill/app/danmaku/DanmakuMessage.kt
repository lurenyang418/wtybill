package tech.wtybill.app.danmaku

data class DanmakuMessage(
    val username: String,
    val text: String,
    val color: Int,
    val id: Long = 0L,
)
