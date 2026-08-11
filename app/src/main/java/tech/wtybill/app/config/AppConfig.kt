package tech.wtybill.app.config

object AppConfig {
    const val PROJECT_NAME = "wtybill"
    const val PLATFORM = "douyu"
    const val ROOM_ID = "57321"
    const val DOUYU_DID = "10000000000000000000000000001501"
    const val DOUYU_HOST = "www.douyu.com"
    const val DOUYU_BASE_URL = "https://$DOUYU_HOST"
    const val DOUYU_REFERER = "$DOUYU_BASE_URL/$ROOM_ID"
    const val DOUYU_DANMAKU_ENDPOINT = "wss://danmuproxy.douyu.com:8506"
    const val SETTINGS_DATASTORE_NAME = "${PROJECT_NAME}_settings"
}
