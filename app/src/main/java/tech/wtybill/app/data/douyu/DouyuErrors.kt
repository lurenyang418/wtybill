package tech.wtybill.app.data.douyu

sealed class DouyuApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

class DouyuHttpException(val statusCode: Int) : DouyuApiException("斗鱼接口 HTTP $statusCode")

class DouyuProtocolException(message: String, cause: Throwable? = null) : DouyuApiException(message, cause)

open class DouyuSignatureException(message: String, cause: Throwable? = null) : DouyuApiException(message, cause)

class DouyuSandboxUnsupportedException : DouyuSignatureException(
    "当前设备不支持 JavaScriptSandbox，请更新 Android System WebView 或系统组件",
)
