package tech.wtybill.app.data.douyu

data class RoomInfo(
    val roomId: String,
    val title: String,
    val anchorName: String,
    val anchorAvatar: String?,
    val cover: String?,
    val hot: Long,
    val introduction: String?,
    val showStatus: Int,
    val videoLoop: Int,
    val status: Int = 0,
) {
    /** `show_status` is the live-state flag; `status` alone is not sufficient. */
    val isLive: Boolean get() = showStatus == 1 && videoLoop != 1
}

data class StreamRate(val name: String, val rate: Int)
data class CdnLine(val code: String, val name: String? = null)
data class StreamOptions(val rates: List<StreamRate>, val cdns: List<CdnLine>)
data class ResolvedStream(val url: String, val rate: Int, val cdn: String)
