package tech.wtybill.app.net

import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

object NetworkClients {
    /** One shared pool/dispatcher; feature clients derive from this base with newBuilder(). */
    val base: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .addInterceptor(IdempotentRetryInterceptor(maxRetries = 1))
        .build()

    /** Coil image requests share the base pool/dispatcher but use image-specific timeouts. */
    val image: OkHttpClient = base.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private class IdempotentRetryInterceptor(private val maxRetries: Int) : Interceptor {
        private val retryableMethods = setOf("GET", "HEAD")
        private val retryableStatusCodes = setOf(408, 425, 429, 500, 502, 503, 504)

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (request.method !in retryableMethods) return chain.proceed(request)
            var attempts = 0
            while (true) {
                val response = try {
                    chain.proceed(request)
                } catch (error: IOException) {
                    if (attempts++ >= maxRetries) throw error
                    continue
                }
                if (response.code !in retryableStatusCodes || attempts++ >= maxRetries) return response
                response.close()
            }
        }
    }
}
