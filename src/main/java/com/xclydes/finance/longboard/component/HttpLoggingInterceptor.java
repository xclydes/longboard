package com.xclydes.finance.longboard.component;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okio.Buffer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.zip.GZIPInputStream;

@Slf4j
public class HttpLoggingInterceptor implements Interceptor {

    private final Boolean logHeaders;
    private final Collection<String> redactHeaders;
    private final long maxBodySize;

    public HttpLoggingInterceptor() {
        this(true, Collections.emptyList());
    }

    public HttpLoggingInterceptor(final Boolean logHeaders, final Collection<String> redactHeaders) {
        this(logHeaders, redactHeaders, 1024*1024);
    }

    public HttpLoggingInterceptor(final Boolean logHeaders, final Collection<String> redactHeaders, final long maxBodySize) {
        this.logHeaders = logHeaders;
        this.redactHeaders = redactHeaders;
        this.maxBodySize = maxBodySize;
    }

    @Override
    public Response intercept(final Chain chain) throws IOException {
        final long reqID = System.currentTimeMillis();
        // Start with an empty request
        final StringBuilder logMsg = new StringBuilder()
                .append('[').append(reqID).append(']').append("\r\n");
        Response response = null;
        try {
            // Get the request
            Request request = chain.request();
            // Generate the request body
            logMsg.append("===[Request]===\r\n").append(asString(request)).append("\r\n");
            // Generate the response body
            response = chain.proceed(request);
            // Generate the response body
            logMsg.append("===[Response]===\r\n")
                .append(asString(response))
                .append("\r\n");
        } finally {
            if(logMsg.length() > 0) {
                log.trace("{}\r\n===", logMsg);
            }
        }

        return response;
    }

    private String asString(final Headers headers) {
        final StringBuilder bldr = new StringBuilder();
        headers.toMultimap().forEach((headerName, value) -> {
            final String headerValue = !redactHeaders.contains(headerName) ?
                String.join(" | ", value) :
                "<redacted>";
            bldr.append(headerName)
                .append(": ")
                .append(headerValue)
                .append("\r\n");
        });
        bldr.append("\r\n");
        return bldr.toString();
    }

    private String asString(final Request request) throws IOException {
        final StringBuilder msg = new StringBuilder()
            .append(request.method())
            .append(" ")
            .append(request.url());
        // Generate the headers if requested
        if(logHeaders) {
            msg.append("\r\n")
                .append(asString(request.headers()));
        }
        final RequestBody requestBody = request.body();
        try (final Buffer buffer = new Buffer()) {
            // Convert the body to a string
            requestBody.writeTo(buffer);
            // Append this to the return message
            msg.append("\r\n") .append(buffer.readUtf8());
        }
        return msg.toString();
    }

    private String asString(final Response response) throws IOException {
        final StringBuilder msg = new StringBuilder()
            .append(response.code())
            .append(" - ")
            .append(response.message());
        // Get the response body
        final ResponseBody responseBody = response.peekBody(this.maxBodySize);
        // Generate the headers if requested
        if(logHeaders) {
            msg.append("\r\n").append(asString(response.headers()));
        }
        // Generate the response body
        String bodyStr = null;
        try {
            // Decode the existing body str
            final InputStream gzipInStream = new GZIPInputStream(responseBody.byteStream());
            // Use this instead
            bodyStr = new String(gzipInStream.readAllBytes());
        }catch (Throwable ignored) {
            log.error(ignored.getMessage());
            try {
                bodyStr = responseBody.string();
            } catch (IOException ignored1) {  }
        }
        // If theres content
        if(bodyStr != null && !bodyStr.isEmpty()) {
            msg.append("\r\n").append(bodyStr);
        }
        // Log the response
        return msg.toString();
    }


}
