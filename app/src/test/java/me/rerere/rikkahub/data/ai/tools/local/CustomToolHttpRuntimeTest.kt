package me.rerere.rikkahub.data.ai.tools.local

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.files.ConversationMediaStore
import me.rerere.rikkahub.data.model.CustomExtension
import me.rerere.rikkahub.data.model.validationErrors
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
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

    private class MemoryStorage : CustomToolStorageProvider {
        private val values = mutableMapOf<Pair<String, String>, ByteArray>()
        override fun readBytes(toolId: String, name: String): ByteArray? = values[toolId to name]
        override fun writeBytes(toolId: String, name: String, bytes: ByteArray) {
            values[toolId to name] = bytes.copyOf()
        }
        override fun delete(toolId: String, name: String): Boolean = values.remove(toolId to name) != null
        override fun list(toolId: String): List<String> = values.keys.filter { it.first == toolId }.map { it.second }.sorted()
        override fun deleteToolStorage(toolId: String) {
            values.keys.filter { it.first == toolId }.forEach(values::remove)
        }
    }

    private class MemoryMediaStore : ConversationMediaStore {
        var savedConversationId: String? = null
        var savedBytes: ByteArray? = null
        var savedMimeType: String? = null
        var discarded: String? = null

        override suspend fun saveConversationMedia(
            conversationId: String,
            bytes: ByteArray,
            displayName: String,
            mimeType: String,
        ): String {
            savedConversationId = conversationId
            savedBytes = bytes.copyOf()
            savedMimeType = mimeType
            return "file:///conversation_media/$conversationId/generated.png"
        }

        override fun discardConversationMedia(uri: String) {
            discarded = uri
        }
    }

    private fun runtime(
        secrets: Map<String, String> = emptyMap(),
        storage: CustomToolStorageProvider = MemoryStorage(),
        mediaStore: ConversationMediaStore? = null,
    ) = CustomToolHttpRuntime(
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build(),
        object : CustomToolSecretProvider {
            override fun getSecret(toolId: String, name: String): String? = secrets[name]
            override fun missingRequiredSecrets(extension: CustomExtension): List<String> =
                extension.secrets.filter { it.required && secrets[it.name].isNullOrEmpty() }.map { it.name }
        },
        storage,
        mediaStore,
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
                "/binary-no-type" -> {
                    val body = byteArrayOf(0, 20, 30)
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                "/escaped-json" -> {
                    val reply = """{"secret":"never${'\\'}u002dreturn-this-secret"}"""
                        .toByteArray(StandardCharsets.UTF_8)
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, reply.size.toLong())
                    exchange.responseBody.use { it.write(reply) }
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
                    exchange.responseHeaders.add("Set-Cookie", "session=must-not-reach-model")
                    exchange.sendResponseHeaders(200, reply.size.toLong())
                    exchange.responseBody.use { it.write(reply) }
                }
            }
        }
        server.start()
        return server
    }


    @Test
    fun imagePublicationIsSavedToConversationOwnedMediaWithItsConversationId() = runBlocking {
        val mediaStore = MemoryMediaStore()
        val bytes = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 2, 0, 0, 0, 3,
        )
        val encoded = java.util.Base64.getEncoder().encodeToString(bytes)
        val conversationId = "12345678-1234-1234-1234-123456789abc"
        val output = runtime(mediaStore = mediaStore).evaluate(
            extension = localExtension(),
            code = """media.image("$encoded", "image/png", "generated.png"); "ready"""",
            conversationId = conversationId,
        )
        val payload = Json.parseToJsonElement(output).jsonObject

        assertEquals(conversationId, mediaStore.savedConversationId)
        assertTrue(bytes.contentEquals(mediaStore.savedBytes))
        assertEquals("image/png", mediaStore.savedMimeType)
        assertEquals(
            "file:///conversation_media/$conversationId/generated.png",
            payload["mediaUri"]?.jsonPrimitive?.content,
        )
        assertEquals("image/png", payload["mediaMimeType"]?.jsonPrimitive?.content)
    }

    @Test
    fun toolExecutionLeaseIsCancelledAndJoinedWhenToolIsRevoked() = runBlocking {
        val runtime = runtime()
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val invocation = async {
            runtime.runToolExecution("leased-tool") {
                started.complete(Unit)
                kotlinx.coroutines.awaitCancellation()
            }
        }
        started.await()

        runtime.revokeToolAndJoin("leased-tool")

        assertTrue(invocation.isCancelled)
        assertTrue(runCatching { runtime.runToolExecution("leased-tool") { Unit } }.isFailure)
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
                response.status + ":" + response.headers.has("set-cookie") + ":" +
                    response.headers.get("x-reply") + ":" + data.method + ":" +
                    data.header + ":" + data.body
            """.trimIndent()

            val output = runtime().evaluate(localExtension(), code)
            val result = Json.parseToJsonElement(output).jsonObject["result"]?.jsonPrimitive?.content

            assertEquals("200:false:local-test:POST:hello:payload", result)
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
    fun cancellingDuringResponseBodyReadClosesTheHttpCall() = runBlocking {
        val bodyStarted = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/slow-body") { exchange ->
            exchange.responseHeaders.add("Content-Type", "text/plain")
            exchange.sendResponseHeaders(200, 10)
            exchange.responseBody.use { output ->
                output.write(byteArrayOf('a'.code.toByte()))
                output.flush()
                bodyStarted.countDown()
                Thread.sleep(4_000)
                runCatching { output.write("123456789".toByteArray()) }
            }
        }
        server.start()
        try {
            val task = async {
                runtime().evaluate(
                    localExtension(),
                    """fetch("http://127.0.0.1:${server.address.port}/slow-body").text()""",
                )
            }
            assertTrue(withContext(Dispatchers.IO) { bodyStarted.await(2, TimeUnit.SECONDS) })
            task.cancel()
            withTimeout(2_000) { task.join() }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun responseWithoutContentTypeIsPreservedAsBinary() = runBlocking {
        val server = startServer()
        try {
            val output = runtime().evaluate(
                localExtension(),
                """new Uint8Array(fetch("http://127.0.0.1:${server.address.port}/binary-no-type").arrayBuffer())[1]""",
            )
            val result = Json.parseToJsonElement(output).jsonObject["result"]?.jsonPrimitive?.content

            assertEquals("20", result)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun namedSecretIsInjectedIntoHeaderAndRedactedFromToolResult() = runBlocking {
        val server = startServer()
        val secret = "never-return-this-secret"
        try {
            val extension = localExtension().copy(
                secrets = listOf(me.rerere.rikkahub.data.model.CustomToolValueDeclaration("API_KEY"))
            )
            val code = """
                const response = fetch("http://127.0.0.1:${server.address.port}/echo", {
                    headers: { "X-Test": "{{secret:API_KEY}}" }
                });
                response.json().header
            """.trimIndent()

            val output = runtime(mapOf("API_KEY" to secret)).evaluate(extension, code)
            val payload = Json.parseToJsonElement(output).jsonObject
            val result = payload["result"]?.jsonPrimitive?.content.orEmpty()

            assertEquals("[REDACTED]", result)
            assertFalse(output.contains(secret))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun arrayBufferCannotBypassSecretRedactionForTextResponses() = runBlocking {
        val server = startServer()
        val secret = "never-return-this-secret"
        try {
            val extension = localExtension().copy(
                secrets = listOf(me.rerere.rikkahub.data.model.CustomToolValueDeclaration("API_KEY"))
            )
            val code = """
                const response = fetch("http://127.0.0.1:${server.address.port}/echo", {
                    headers: { "X-Test": "{{secret:API_KEY}}" }
                });
                Array.from(new Uint8Array(response.arrayBuffer()))
                    .map(value => String.fromCharCode(value)).join("")
            """.trimIndent()

            val output = runtime(mapOf("API_KEY" to secret)).evaluate(extension, code)
            val result = Json.parseToJsonElement(output).jsonObject["result"]?.jsonPrimitive?.content.orEmpty()

            assertFalse(output.contains(secret))
            assertTrue(result.contains("[REDACTED]"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun escapedJsonCannotBypassSecretRedaction() = runBlocking {
        val server = startServer()
        val secret = "never-return-this-secret"
        try {
            val extension = localExtension().copy(
                secrets = listOf(me.rerere.rikkahub.data.model.CustomToolValueDeclaration("API_KEY"))
            )
            val code = """
                fetch("http://127.0.0.1:${server.address.port}/escaped-json", {
                    headers: { "X-Test": "{{secret:API_KEY}}" }
                }).json().secret
            """.trimIndent()

            val output = runtime(mapOf("API_KEY" to secret)).evaluate(extension, code)
            val result = Json.parseToJsonElement(output).jsonObject["result"]?.jsonPrimitive?.content

            assertEquals("[REDACTED]", result)
            assertFalse(output.contains(secret))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun secretsCannotBeDeclaredWithoutApiAccess() = runBlocking {
        val extension = CustomExtension(
            name = "No API",
            description = "",
            secrets = listOf(me.rerere.rikkahub.data.model.CustomToolValueDeclaration("API_KEY")),
        )

        val error = runCatching { runtime().evaluate(extension, "1") }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("Secret or Variable declarations are invalid"))
    }

    @Test
    fun requiredSecretMustExistBeforeApiToolRuns() = runBlocking {
        val extension = localExtension().copy(
            secrets = listOf(me.rerere.rikkahub.data.model.CustomToolValueDeclaration("API_KEY"))
        )

        val error = runCatching { runtime().evaluate(extension, "1") }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("Missing required Secrets"))
    }

    @Test
    fun declaredVariablesAreAvailableToJavaScript() = runBlocking {
        val extension = CustomExtension(
            name = "Variable test",
            description = "",
            variables = listOf(me.rerere.rikkahub.data.model.CustomToolValueDeclaration("API_BASE"))
        )

        val output = runtime().evaluate(extension, "globalThis.variables.API_BASE", mapOf("API_BASE" to "https://api.example.com"))
        val result = Json.parseToJsonElement(output).jsonObject["result"]?.jsonPrimitive?.content

        assertEquals("https://api.example.com", result)
    }

    @Test
    fun undeclaredSecretReferenceIsRejected() = runBlocking {
        val server = startServer()
        try {
            val output = runtime(mapOf("API_KEY" to "value")).evaluate(
                localExtension(),
                """fetch("http://127.0.0.1:${server.address.port}/echo", { headers: { "X-Test": "{{secret:API_KEY}}" } }).status""",
            )
            val error = Json.parseToJsonElement(output).jsonObject["error"]?.jsonPrimitive?.content.orEmpty()

            assertTrue(error.contains("undeclared Secret"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun optedInToolCanPersistAndReadItsPrivateTextStorage() = runBlocking {
        val extension = CustomExtension(
            name = "Storage test",
            description = "",
            storageEnabled = true,
        )
        val storage = MemoryStorage()
        val output = runtime(storage = storage).evaluate(
            extension,
            """
                storage.writeText("cache.json", "{\"value\":42}");
                JSON.parse(storage.readText("cache.json")).value + ":" + storage.list().join(",")
            """.trimIndent(),
        )
        val result = Json.parseToJsonElement(output).jsonObject["result"]?.jsonPrimitive?.content

        assertEquals("42:cache.json", result)
    }

    @Test
    fun builderTestStorageDoesNotOverwritePersistentToolStorage() = runBlocking {
        val extension = CustomExtension(id = "tool-isolated", name = "Builder storage test", description = "", storageEnabled = true)
        val persistent = MemoryStorage().apply {
            writeBytes(extension.id, "cache.txt", "real".toByteArray())
        }
        val temporary = InMemoryCustomToolStorage()
        val output = runtime(storage = persistent).evaluate(
            extension,
            """storage.writeText("cache.txt", "test"); storage.readText("cache.txt")""",
            storageOverride = temporary,
        )
        val result = Json.parseToJsonElement(output).jsonObject["result"]?.jsonPrimitive?.content

        assertEquals("test", result)
        assertEquals("real", persistent.readBytes(extension.id, "cache.txt")!!.decodeToString())
        assertEquals("test", temporary.readBytes(extension.id, "cache.txt")!!.decodeToString())
    }

    @Test
    fun binaryStorageRoundTripsBase64WithoutTextDecoding() = runBlocking {
        val extension = CustomExtension(name = "Binary storage test", description = "", storageEnabled = true)
        val storage = MemoryStorage()
        val output = runtime(storage = storage).evaluate(
            extension,
            """storage.writeBase64("image.bin", "AAUQ"); storage.readBase64("image.bin")""",
        )
        val result = Json.parseToJsonElement(output).jsonObject["result"]?.jsonPrimitive?.content

        assertEquals("AAUQ", result)
    }

    @Test
    fun disabledStorageCapabilityDoesNotInstallStorageApiOrDeleteSavedData() = runBlocking {
        val extension = CustomExtension(name = "No storage", description = "", storageEnabled = false)
        val storage = MemoryStorage().apply {
            writeBytes(extension.id, "cache.txt", "retained".toByteArray())
        }
        val output = runtime(storage = storage).evaluate(extension, "typeof storage")
        val result = Json.parseToJsonElement(output).jsonObject["result"]?.jsonPrimitive?.content

        assertEquals("undefined", result)
        assertEquals("retained", storage.readBytes(extension.id, "cache.txt")!!.decodeToString())
    }

    @Test
    fun deletedToolCannotStartAnotherExecution() = runBlocking {
        val extension = CustomExtension(name = "Deleted tool", description = "")
        val runtime = runtime()
        runtime.revokeTool(extension.id)

        val error = runCatching { runtime.evaluate(extension, "1") }.exceptionOrNull()

        assertTrue(error is java.util.concurrent.CancellationException)
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
    fun customToolHttpClientCannotInheritAProxyOrLoggingInterceptors() {
        val client = CustomToolHttpRuntime.createHttpClient()

        assertEquals(java.net.Proxy.NO_PROXY, client.proxy)
        assertTrue(client.interceptors.isEmpty())
        assertTrue(client.networkInterceptors.isEmpty())
    }

    @Test
    fun ipv6LinkLocalAndSiteLocalRequireTheExplicitLocalNetworkCapability() {
        val runtime = runtime()
        val linkLocal = InetAddress.getByName("fe80::1")
        val siteLocal = InetAddress.getByName("fec0::1")

        assertFalse(runtime.isSafeAddress(linkLocal, allowLocalNetwork = false))
        assertFalse(runtime.isSafeAddress(siteLocal, allowLocalNetwork = false))
        assertTrue(runtime.isSafeAddress(linkLocal, allowLocalNetwork = true))
        assertTrue(runtime.isSafeAddress(siteLocal, allowLocalNetwork = true))
        assertTrue(runtime.isLocalAddress(linkLocal))
        assertTrue(runtime.isLocalAddress(siteLocal))
    }

    @Test
    fun ipv4MappedIpv6AddressesRemainClassifiedAsLocalForMixedDnsChecks() {
        val runtime = runtime()
        val mappedPrivate = InetAddress.getByName("::ffff:192.168.1.10")

        assertTrue(runtime.isLocalAddress(mappedPrivate))
        assertFalse(runtime.isSafeAddress(mappedPrivate, allowLocalNetwork = false))
        assertTrue(runtime.isSafeAddress(mappedPrivate, allowLocalNetwork = true))
    }

    @Test
    fun ipv6CloudMetadataDestinationsRemainBlockedWithLocalNetworkEnabled() {
        val runtime = runtime()
        val awsMetadata = InetAddress.getByName("fd00:ec2::254")
        val googleMetadata = InetAddress.getByName("fd20:ce::254")

        assertFalse(runtime.isSafeAddress(awsMetadata, allowLocalNetwork = true))
        assertFalse(runtime.isSafeAddress(googleMetadata, allowLocalNetwork = true))
    }

    @Test
    fun localNetworkHostsMustBeExplicitlyEnabledInTheToolDefinition() {
        val extension = localExtension().copy(allowLocalNetwork = false)

        assertTrue(extension.validationErrors().isNotEmpty())
    }
}
