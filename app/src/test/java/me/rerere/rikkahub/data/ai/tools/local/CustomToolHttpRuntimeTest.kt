package me.rerere.rikkahub.data.ai.tools.local

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.model.CustomExtension
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class CustomToolHttpRuntimeTest {

    private fun localExtension(
        allowedHosts: List<String> = listOf("127.0.0.1"),
    ) = CustomExtension(
        name = "Local API test",
        description = "Exercises the isolated custom-tool HTTP runtime",
        apiAccessEnabled = true,
        allowedApiHosts = allowedHosts,
        allowLocalNetwork = true,
    )

    private fun runtime() = CustomToolHttpRuntime(
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    )

    private fun startServer(): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            when (exchange.requestURI.path) {
                "/binary" -> {
                    val body = byteArrayOf(0, 20, 30)
                    exchange.responseHeaders.add("Content-Type", "application/octet-stream")
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                "/redirect" -> {
                    exchange.responseHeaders.add(
                        "Location",
                        "http://127.0.0.2:${server.address.port}/echo",
                    )
                    exchange.sendResponseHeaders(302, -1)
                    exchange.close()
                }
                else -> {
                    val requestBody = exchange.requestBody.use { it.readBytes() }
                        .toString(StandardCharsets.UTF_8)
                    val reply = buildJsonObject {
                        put("method", exchange.requestMethod)
                        put("header", exchange.requestHeaders.getFirst("X-Test").orEmpty())
                        put("body", requestBody)
                    }.toString().toByteArray(StandardCharsets.UTF_8)
                    exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
                    exchange.responseHeaders.add("X-Reply", "local-test")
                    exchange.sendResponseHeaders(200, reply.size.toLong())
                    exchange.responseBody.use { it.write(reply) }
                }
            }
        }
        server.start()
        return server
    }

    @Test
    fun optedInToolCanSendBoundedHttpRequestsAndReadJsonAndHeaders() = runBlocking {
        val server = startServer()
        try {
            val code = """
                const response = fetch("http://127.0.0.1:${server.address.port}/echo", {
                    method: "POST",
                    headers: { "X-Test": "hello" },
                    body: "payload"
                });
                const data = response.json();
                response.status + ":" + response.headers.get("x-reply") + ":" +
                    data.method + ":" + data.header + ":" + data.body
            """.trimIndent()

            val output = runtime().evaluate(localExtension(), code)
            val result = Json.parseToJsonElement(output).jsonObject["result"]?.jsonPrimitive?.content

            assertEquals("200:local-test:POST:hello:payload", result)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun binaryResponseCanBeReadAsAnArrayBuffer() = runBlocking {
        val server = startServer()
        try {
            val code = """
                const response = fetch("http://127.0.0.1:${server.address.port}/binary");
                new Uint8Array(response.arrayBuffer())[1]
            """.trimIndent()

            val output = runtime().evaluate(localExtension(), code)
            val result = Json.parseToJsonElement(output).jsonObject["result"]?.jsonPrimitive?.content

            assertEquals("20", result)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun disabledApiCapabilityDoesNotInstallFetch() = runBlocking {
        val extension = localExtension().copy(
            apiAccessEnabled = false,
            allowedApiHosts = emptyList(),
            allowLocalNetwork = false,
        )

        val output = runtime().evaluate(extension, "typeof fetch")
        val result = Json.parseToJsonElement(output).jsonObject["result"]?.jsonPrimitive?.content

        assertEquals("undefined", result)
    }

    @Test
    fun hostOutsideTheAllowlistIsRejectedBeforeARequestIsSent() = runBlocking {
        val server = startServer()
        try {
            val output = runtime().evaluate(
                localExtension(allowedHosts = listOf("127.0.0.2")),
                """fetch("http://127.0.0.1:${server.address.port}/echo").status""",
            )
            val payload = Json.parseToJsonElement(output).jsonObject
            val error = payload["error"]?.jsonPrimitive?.content.orEmpty()

            assertFalse(payload.containsKey("result"))
            assertTrue(error.contains("allowlist"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun crossOriginRedirectIsRejected() = runBlocking {
        val server = startServer()
        try {
            val output = runtime().evaluate(
                localExtension(),
                """fetch("http://127.0.0.1:${server.address.port}/redirect").status""",
            )
            val payload = Json.parseToJsonElement(output).jsonObject
            val error = payload["error"]?.jsonPrimitive?.content.orEmpty()

            assertFalse(payload.containsKey("result"))
            assertTrue(error.contains("same origin"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun localNetworkHostsMustBeExplicitlyEnabledInTheToolDefinition() {
        val extension = localExtension().copy(allowLocalNetwork = false)

        assertTrue(extension.validationErrors().isNotEmpty())
    }
}
