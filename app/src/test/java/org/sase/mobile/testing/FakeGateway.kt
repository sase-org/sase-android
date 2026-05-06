package org.sase.mobile.testing

import java.io.Closeable
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

class FakeGateway : Closeable {
    private val server = MockWebServer()

    val baseUrl: HttpUrl
        get() = server.url("/")

    fun enqueueJson(
        body: String,
        statusCode: Int = 200,
        headers: Map<String, String> = emptyMap(),
    ) {
        val response = MockResponse()
            .setResponseCode(statusCode)
            .setHeader("Content-Type", "application/json")
            .setBody(body)
        headers.forEach { (name, value) -> response.setHeader(name, value) }
        server.enqueue(response)
    }

    fun enqueueSse(vararg eventJson: String) {
        val body = eventJson.joinToString(separator = "\n\n") { "data: $it" } + "\n\n"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body),
        )
    }

    fun takeRequest(): RecordedRequest = server.takeRequest()

    override fun close() {
        server.shutdown()
    }
}
