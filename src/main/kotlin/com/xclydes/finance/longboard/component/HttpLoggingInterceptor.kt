package com.xclydes.finance.longboard.component

import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.InputStream
import java.util.zip.GZIPInputStream

@Component
class HttpLoggingInterceptor(val logHeaders: Boolean = true,
                             val redactHeaders: List<String> = emptyList()
                            ) : Interceptor {

    private val log by lazyOf(LoggerFactory.getLogger(javaClass))

    override fun intercept(chain: Interceptor.Chain): Response {
        val reqID = System.currentTimeMillis()
        // Start with an empty request
        var logMsg = "[${reqID}]\r\n"
        try {
            // Get the request
            val request = chain.request()
            // Generate the request body
            logMsg += "===[Request]===\r\n${asString(request)}\r\n"
            // Generate the response body
            return with( chain.proceed( request ) ) {
                // Generate the response body
                logMsg += "===[Response]===\r\n${asString(this)}\r\n"
                // Continue the chain
                return@with this
            }
        }finally {
            if(logMsg.isNotEmpty()) {
                log.trace("$logMsg\r\n===")
            }
        }
    }

    private fun asString(headers: Headers) =
        headers.toMultimap().entries.fold("") { input, entry ->
            input + "${entry.key}: " +
            //If this is not a redacted heaer
            if(!redactHeaders.contains(entry.key)) {
                // Render the entry
                "${entry.value.joinToString(" | ")}\r\n"
            } else {
                "<redacted>"
            }
        }

    private fun asString(request: Request): String {
        var msg = "${request.method()} ${request.url()}"
        // Generate the headers if requested
        if(logHeaders) {
            msg += "\r\n${asString(request.headers())}"
        }
        // Process the body
        request.body()?.let { reqBody ->
            // Convert the body to a string
            val bodyStr = okio.Buffer().also { buf -> reqBody.writeTo(buf) }.readUtf8()
            msg += "\r\n${bodyStr}"
        }
        return msg
    }

    private fun asString(response: Response) : String {
        var msg = "${response.code()} - ${response.message()}"
        // Get the response body
        val responseBody = response.peekBody(1024*1024)
        // Generate the headers if requested
        if(logHeaders) {
            msg += "\r\n${asString(response.headers())}"
        }
        // Generate the response body
        var bodyStr: String
        try {
            // Decode the existing body str
            val gzipInStream: InputStream = GZIPInputStream(responseBody.byteStream())
            // Use this instead
            bodyStr = gzipInStream.readAllBytes().decodeToString()
        }catch (ignored: Throwable) {
            log.error(ignored.message)
            bodyStr = responseBody.string()
        }
        if(bodyStr.isNotEmpty()) {
            msg += "\r\n${bodyStr}"
        }
        // Log the response
        return msg
    }
}
