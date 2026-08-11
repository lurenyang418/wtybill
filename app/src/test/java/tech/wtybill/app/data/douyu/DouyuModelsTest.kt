package tech.wtybill.app.data.douyu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DouyuModelsTest {
    private fun room(showStatus: Int, videoLoop: Int, status: Int = 0) = RoomInfo(
        roomId = "57321",
        title = "title",
        anchorName = "anchor",
        anchorAvatar = null,
        cover = null,
        hot = 0,
        introduction = null,
        showStatus = showStatus,
        videoLoop = videoLoop,
        status = status,
    )

    @Test fun liveStatusRequiresLiveFlagAndNonLoopingVideo() {
        assertTrue(room(showStatus = 1, videoLoop = 0).isLive)
        assertFalse(room(showStatus = 0, videoLoop = 0).isLive)
        assertFalse(room(showStatus = 1, videoLoop = 1).isLive)
        assertFalse(room(showStatus = 2, videoLoop = 0).isLive)
        assertFalse(room(showStatus = 2, videoLoop = 0, status = 1).isLive)
    }
}
