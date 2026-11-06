package me.rerere.rikkahub.data.ai.tools.local

import android.net.Uri
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.QuickJsInterruptedException
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.CustomExtension
import me.rerere.rikkahub.data.model.CustomExtensionLimits
import me.rerere.rikkahub.data.model.CustomExtensionValidationError
import me.rerere.rikkahub.data.model.allowsApiHost
import me.rerere.rikkahub.data.model.validationErrors
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.util.Base64
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

/**
 * Dedicated, policy-enforced HTTP bridge for custom tools. It deliberately does not use
 * SearchService's shared client, which has application request logging interceptors.
 */
class CustomToolHttpRuntime(
    private val baseClient: OkHttpClient,
    private val secretStore: CustomToolSecretProvider,
    private val storage: CustomToolStorageProvider,
    private val filesManager: FilesManager? = null,
) {
    private val activeExecutions = ConcurrentHashMap<String, MutableSet<Job>>()
    private val revokedToolIds = ConcurrentHashMap.newKeySet<String>()

    /** Permanently invalidates this immutable tool ID and cancels any work already running. */
    fun revokeTool(toolId: String) {
        require(toolId.isNotBlank())
        revokedToolIds.add(toolId)
        activeExecutions[toolId]?.toList()?.forEach { job ->
            job.cancel(CancellationException("Custom tool was deleted"))
        }
    }

    /** Re-enables an ID only when a deletion attempt failed before its definition was removed. */
    fun restoreToolAfterFailedDeletion(toolId: String) {
        revokedToolIds.remove(toolId)
    }

    /** Waits for all in-flight work to stop before its private files are removed. */
    suspend fun revokeToolAndJoin(toolId: String) {
        revokeTool(toolId)
        activeExecutions[toolId]?.toList()?.joinAll()
    }


    suspend fun evaluate(
        extension: CustomExtension,
        code: String,
        variables: Map<String, String> = emptyMap(),
        storageOverride: CustomToolStorageProvider? = null,
        conversationId: String? = null,
        timeoutMillis: Long = DEFAULT_EXECUTION_TIMEOUT_MS,
    ): String = withContext(Dispatchers.Default) {
        if (extension.id in revokedToolIds) throw CancellationException("Custom tool was deleted")
        require(code.length <= CustomExtensionLimits.MAX_IMPLEMENTATION_LENGTH) {
            "Custom tool implementation exceeds the size limit"
        }
        val validationErrors = extension.validationErrors()
        if (extension.apiAccessEnabled) {
            require(validationErrors.isEmpty()) {
                "Custom tool API permissions or definition are invalid"
            }
        }
        val invalidValueDeclarations = validationErrors.filter {
            it == CustomExtensionValidationError.LOCAL_NETWORK_WITHOUT_API_ACCESS ||
                it == CustomExtensionValidationError.SECRETS_WITHOUT_API_ACCESS ||
                it == CustomExtensionValidationError.TOO_MANY_SECRETS ||
                it == CustomExtensionValidationError.TOO_MANY_VARIABLES ||
                it == CustomExtensionValidationError.INVALID_SECRET_DECLARATION ||
                it == CustomExtensionValidationError.INVALID_VARIABLE_DECLARATION ||
                it == CustomExtensionValidationError.DUPLICATE_VALUE_NAME
        }
        require(invalidValueDeclarations.isEmpty()) {
            "Custom tool Secret or Variable declarations are invalid"
        }
        if (extension.apiAccessEnabled) {
            val missingSecrets = secretStore.missingRequiredSecrets(extension)
            require(missingSecrets.isEmpty()) {
                "Missing required Secrets: ${missingSecrets.joinToString(", ")}"
            }
        }
        val declaredVariableNames = extension.variables.map { it.name }.toSet()
        val safeVariables = variables.filterKeys { it in declaredVariableNames }
        val missingVariables = extension.variables
            .filter { it.required && safeVariables[it.name].isNullOrBlank() }
            .map { it.name }
        require(missingVariables.isEmpty()) {
            "Missing required Variables: ${missingVariables.joinToString(", ")}"
        }
        require(safeVariables.values.all {
            it.toByteArray(Charsets.UTF_8).size <= CustomExtensionLimits.MAX_SECRET_VALUE_LENGTH
        }) {
            "A custom tool Variable exceeds the 8 KiB limit"
        }
        val variablesJson = kotlinx.serialization.json.JsonObject(
            safeVariables.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) }
        ).toString()
        val executionStorage = storageOverride ?: storage
        var pendingImage: CustomToolImagePayload? = null
        var savedMediaUri: Uri? = null
        val logs = StringBuilder()
        var logsTruncated = false
        fun appendLog(line: String) {
            val remaining = MAX_LOG_CHARS - logs.length
            if (remaining <= 0) {
                logsTruncated = true
                return
            }
            val entry = if (logs.isEmpty()) line else "\n$line"
            logs.append(entry.take(remaining))
            if (entry.length > remaining) logsTruncated = true
        }
        fun errorPayload(message: String) = buildJsonObject { put("error", message) }.toString()

        val executionJob = currentCoroutineContext().job
        val jobs = activeExecutions.computeIfAbsent(extension.id) { ConcurrentHashMap.newKeySet<Job>() }
        jobs.add(executionJob)
        if (extension.id in revokedToolIds) {
            jobs.remove(executionJob)
            if (jobs.isEmpty()) activeExecutions.remove(extension.id, jobs)
            throw CancellationException("Custom tool was deleted")
        }
        try {
            withTimeoutOrNull(timeoutMillis.milliseconds) {
                val toolClient = baseClient.newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .retryOnConnectionFailure(false)
                    // DNS validation must apply to the destination host itself, not a remote
                    // proxy resolver that could resolve the same hostname differently.
                    .proxy(Proxy.NO_PROXY)
                    .dns(safeDns(extension))
                    .build()

                quickJs(Dispatchers.Default) {
                    memoryLimit = JS_MEMORY_LIMIT_BYTES
                    maxStackSize = JS_MAX_STACK_BYTES
                    evaluationTimeoutMillis = timeoutMillis
                    function("__rikkahubLog") { args -> appendLog(args[0] as String) }

                    if (extension.apiAccessEnabled) {
                        var requestCount = 0
                        function("__customToolHttpRequest") { args ->
                            requestCount += 1
                            require(requestCount <= MAX_REQUESTS_PER_EXECUTION) {
                                "Custom tool exceeded the HTTP request limit"
                            }
                            executeHttpRequest(
                                extension = extension,
                                client = toolClient,
                                executionJob = executionJob,
                                url = args.getOrNull(0) as? String ?: error("HTTP request URL is required"),
                                method = args.getOrNull(1) as? String ?: "GET",
                                headersJson = args.getOrNull(2) as? String,
                                body = args.getOrNull(3) as? String,
                                bodyEncoding = args.getOrNull(4) as? String ?: "text",
                            )
                        }
                        function("__customToolSetImage") { args ->
                            require(pendingImage == null) {
                                "A custom tool can publish only one image per invocation"
                            }
                            val encodedInput = args.getOrNull(0) as? String
                                ?: error("Image data must be Base64 or an ArrayBuffer")
                            val encoded = encodedInput.substringAfter("base64,", encodedInput)
                            require(encoded.length <= MAX_IMAGE_BASE64_CHARS) {
                                "Image output exceeds the 8 MiB size limit"
                            }
                            val bytes = runCatching { Base64.getDecoder().decode(encoded) }
                                .getOrElse { throw IllegalArgumentException("Image data is not valid Base64") }
                            val mimeType = args.getOrNull(1) as? String
                                ?: error("Image content type is required")
                            val detectedMimeType = CustomToolImageValidator.validate(bytes, mimeType)
                            val requestedName = (args.getOrNull(2) as? String).orEmpty()
                            val displayName = requestedName
                                .filter { it.isLetterOrDigit() || it in " ._-" }
                                .take(120)
                                .ifBlank { "generated-image" }
                            pendingImage = CustomToolImagePayload(bytes, detectedMimeType, displayName)
                            true
                        }
                        evaluate<Unit>(FETCH_POLYFILL + "\n" + MEDIA_POLYFILL + "\nvoid 0;")
                    }
                    if (extension.storageEnabled) {
                        var storageOperationCount = 0
                        fun requireStorageOperation() {
                            storageOperationCount += 1
                            require(storageOperationCount <= MAX_STORAGE_OPERATIONS_PER_EXECUTION) {
                                "Custom tool exceeded the storage operation limit"
                            }
                        }
                        function("__customToolStorageRead") { args ->
                            requireStorageOperation()
                            val name = args.getOrNull(0) as? String ?: error("Storage item name is required")
                            val bytes = executionStorage.readBytes(extension.id, name)
                            buildJsonObject {
                                put("found", bytes != null)
                                if (bytes != null) put("value", bytes.toString(Charsets.UTF_8))
                            }.toString()
                        }
                        function("__customToolStorageWrite") { args ->
                            requireStorageOperation()
                            val name = args.getOrNull(0) as? String ?: error("Storage item name is required")
                            val value = args.getOrNull(1) as? String ?: error("Storage text value is required")
                            executionStorage.writeBytes(extension.id, name, value.toByteArray(Charsets.UTF_8))
                            true
                        }
                        function("__customToolStorageReadBase64") { args ->
                            requireStorageOperation()
                            val name = args.getOrNull(0) as? String ?: error("Storage item name is required")
                            val bytes = executionStorage.readBytes(extension.id, name)
                            buildJsonObject {
                                put("found", bytes != null)
                                if (bytes != null) put("value", Base64.getEncoder().encodeToString(bytes))
                            }.toString()
                        }
                        function("__customToolStorageWriteBase64") { args ->
                            requireStorageOperation()
                            val name = args.getOrNull(0) as? String ?: error("Storage item name is required")
                            val value = args.getOrNull(1) as? String ?: error("Storage binary value is required")
                            require(value.length <= MAX_BASE64_STORAGE_CHARS) {
                                "Stored item exceeds the 1 MiB per-file limit"
                            }
                            val bytes = runCatching { Base64.getDecoder().decode(value) }
                                .getOrElse { throw IllegalArgumentException("Storage binary value is invalid Base64") }
                            executionStorage.writeBytes(extension.id, name, bytes)
                            true
                        }
                        function("__customToolStorageDelete") { args ->
                            requireStorageOperation()
                            val name = args.getOrNull(0) as? String ?: error("Storage item name is required")
                            executionStorage.delete(extension.id, name)
                        }
                        function("__customToolStorageList") { _ ->
                            requireStorageOperation()
                            buildJsonArray {
                                executionStorage.list(extension.id).forEach { add(JsonPrimitive(it)) }
                            }.toString()
                        }
                        evaluate<Unit>(STORAGE_POLYFILL + "\nvoid 0;")
                    }

                    val result = evaluate<String?>(
                        """
                        (() => {
                            const log = globalThis.__rikkahubLog;
                            delete globalThis.__rikkahubLog;
                            const stringify = JSON.stringify;
                            const toString = String;
                            const format = value => {
                                if (value !== null && typeof value === 'object') {
                                    try { return stringify(value); } catch (_) {}
                                }
                                return toString(value);
                            };
                            globalThis.console = {};
                            for (const level of ['log', 'info', 'warn', 'error', 'debug']) {
                                console[level] = (...args) => {
                                    let line = '[' + (level === 'debug' ? 'LOG' : level.toUpperCase()) + ']';
                                    for (const arg of args) {
                                        line += ' ' + format(arg);
                                        if (line.length > $MAX_LOG_CHARS) {
                                            line = line.slice(0, $MAX_LOG_CHARS) + ' [truncated]';
                                            break;
                                        }
                                    }
                                    log(line);
                                };
                            }
                            globalThis.variables = Object.freeze(JSON.parse(${JsonPrimitive(variablesJson)}));
                            const result = (0, eval)(${JsonPrimitive(code)});
                            if (result == null) return null;
                            const type = typeof result;
                            const text = type === 'object' || type === 'function'
                                ? stringify(result) : toString(result);
                            if (text != null && text.length > $MAX_RESULT_CHARS) {
                                throw new Error('JavaScript result exceeds output limit');
                            }
                            return text == null ? null : text;
                        })()
                        """.trimIndent()
                    )
                    buildJsonObject {
                        if (logs.isNotEmpty()) {
                            put("logs", logs.toString() + if (logsTruncated) "\n[Logs truncated]" else "")
                        }
                        put("result", result?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull)
                    }.toString()
                }
            } ?: errorPayload("Custom tool execution timed out after ${timeoutMillis}ms")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: QuickJsInterruptedException) {
            errorPayload("Custom tool execution timed out after ${timeoutMillis}ms")
        } catch (error: QuickJsException) {
            errorPayload((error.message ?: "Custom tool execution failed").take(MAX_LOG_CHARS))
        } catch (error: Exception) {
            errorPayload((error.message ?: "Custom tool execution failed").take(MAX_LOG_CHARS))
        } finally {
            jobs.remove(executionJob)
            if (jobs.isEmpty()) activeExecutions.remove(extension.id, jobs)
        }
    }

    private fun executeHttpRequest(
        extension: CustomExtension,
        client: OkHttpClient,
        executionJob: Job,
        url: String,
        method: String,
        headersJson: String?,
        body: String?,
        bodyEncoding: String,
    ): String {
        require(url.length <= MAX_URL_LENGTH) { "HTTP request URL exceeds the size limit" }
        var currentMethod = method.uppercase(Locale.ROOT)
        require(currentMethod in ALLOWED_METHODS) { "HTTP method is not supported" }
        val rawHeaders = parseRequestHeaders(headersJson)
        val requestBytes = when (bodyEncoding) {
            "text" -> body?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
            "base64" -> {
                val encoded = body.orEmpty()
                require(encoded.length <= MAX_BASE64_REQUEST_CHARS) {
                    "HTTP request body exceeds the size limit"
                }
                runCatching { Base64.getDecoder().decode(encoded) }
                    .getOrElse { throw IllegalArgumentException("HTTP request body encoding is invalid") }
            }
            else -> throw IllegalArgumentException("HTTP request body encoding is not supported")
        }
        require(requestBytes.size <= MAX_REQUEST_BODY_BYTES) { "HTTP request body exceeds the size limit" }
        require(currentMethod !in setOf("GET", "HEAD") || requestBytes.isEmpty()) {
            "GET and HEAD requests cannot include a body"
        }

        val contentType = rawHeaders.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value
        var currentBody = when {
            currentMethod in setOf("POST", "PUT", "PATCH") || requestBytes.isNotEmpty() ->
                requestBytes.toRequestBody(contentType?.toMediaTypeOrNull())
            else -> null
        }
        var currentUrl = parseAndValidateUrl(extension, url)
        val resolvedHeaders = resolveSecretHeaders(extension, rawHeaders)
        val secretsUsed = resolvedHeaders.second
        val headers = resolvedHeaders.first
        var redirects = 0

        while (true) {
            executionJob.ensureActive()
            validateUrl(extension, currentUrl)
            val requestBuilder = Request.Builder().url(currentUrl)
            headers.forEach { (name, value) -> requestBuilder.header(name, value) }
            requestBuilder.method(currentMethod, currentBody)
            val call = client.newCall(requestBuilder.build())
            val cancellation = Job(executionJob)
            cancellation.invokeOnCompletion { cause ->
                if (cause is CancellationException) call.cancel()
            }

            val response = try {
                call.execute()
            } catch (_: IOException) {
                cancellation.complete()
                throw IllegalStateException("Custom tool HTTP request failed")
            }

            val responseResult = try {
                response.use { received ->
                    if (received.code in REDIRECT_CODES && received.header("Location") != null) {
                        if (redirects >= MAX_REDIRECTS) {
                            throw IllegalStateException("Custom tool HTTP redirect limit exceeded")
                        }
                        val redirected = currentUrl.resolve(received.header("Location")!!)
                            ?: throw IllegalStateException("Custom tool HTTP redirect URL is invalid")
                        require(sameOrigin(currentUrl, redirected)) {
                            "Custom tool HTTP redirects must remain on the same origin"
                        }
                        currentUrl = parseAndValidateUrl(extension, redirected.toString())
                        if (received.code == 303 ||
                            (received.code in setOf(301, 302) && currentMethod !in setOf("GET", "HEAD"))
                        ) {
                            currentMethod = "GET"
                            currentBody = null
                        }
                        redirects += 1
                        null
                    } else {
                        responseToJson(received, currentUrl, secretsUsed)
                    }
                }
            } finally {
                // Keep the cancellation hook active while the response body is streamed.
                cancellation.complete()
            }
            if (responseResult != null) return responseResult
        }
    }

    private fun parseAndValidateUrl(extension: CustomExtension, rawUrl: String): HttpUrl {
        val url = rawUrl.toHttpUrlOrNull() ?: throw IllegalArgumentException("HTTP request URL is invalid")
        require(url.username.isEmpty() && url.password.isEmpty() && url.fragment == null) {
            "HTTP request URL cannot contain credentials or a fragment"
        }
        validateUrl(extension, url)
        return url
    }

    private fun validateUrl(extension: CustomExtension, url: HttpUrl): List<InetAddress> {
        // Mixed public/private DNS answers create an ambiguous target and can bypass
        // destination checks when DNS resolution is performed inconsistently.
        require(url.host.length <= 253 && extension.allowsApiHost(url.host)) {
            "HTTP request host is not in this tool's allowlist"
        }
        require(url.scheme == "https" || url.scheme == "http") {
            "Only HTTP and HTTPS request URLs are supported"
        }
        val addresses = resolveAndValidate(extension, url.host)
        val localAddressCount = addresses.count(::isLocalAddress)
        require(localAddressCount == 0 || localAddressCount == addresses.size) {
            "HTTP request host resolves to mixed local and public addresses"
        }
        val isLocalDestination = addresses.all(::isLocalAddress)
        if (isLocalDestination) {
            require(extension.allowLocalNetwork) {
                "Local-network requests require the separate local-network permission"
            }
        } else {
            require(url.scheme == "https" && url.port == 443) {
                "Public API hosts must use HTTPS on the standard port"
            }
        }
        return addresses
    }

    private fun resolveAndValidate(extension: CustomExtension, host: String): List<InetAddress> {
        val addresses = try {
            Dns.SYSTEM.lookup(host)
        } catch (_: UnknownHostException) {
            throw IllegalArgumentException("HTTP request host could not be resolved")
        }
        require(addresses.isNotEmpty()) { "HTTP request host has no addresses" }
        require(addresses.all { isSafeAddress(it, extension.allowLocalNetwork) }) {
            "HTTP request host resolves to a blocked network address"
        }
        return addresses
    }

    private fun safeDns(extension: CustomExtension): Dns = Dns { hostname ->
        // The custom-tool client is direct so OkHttp resolves the validated destination host.
        if (!extension.allowsApiHost(hostname)) {
            Dns.SYSTEM.lookup(hostname)
        } else {
            try {
                resolveAndValidate(extension, hostname)
            } catch (_: IllegalArgumentException) {
                throw UnknownHostException("Custom tool host resolves to a blocked network address")
            }
        }
    }

    private fun parseRequestHeaders(source: String?): Map<String, String> {
        if (source.isNullOrBlank() || source == "null") return emptyMap()
        val objectValue = runCatching { Json.parseToJsonElement(source).jsonObject }
            .getOrElse { throw IllegalArgumentException("HTTP request headers must be an object") }
        require(objectValue.size <= MAX_REQUEST_HEADERS) { "Too many HTTP request headers" }
        val result = linkedMapOf<String, String>()
        var totalBytes = 0
        objectValue.forEach { (name, element) ->
            val value = (element as? JsonPrimitive)?.contentOrNull
                ?: throw IllegalArgumentException("HTTP request header values must be strings or primitives")
            require(name.isNotBlank() && name.length <= 128 && value.length <= MAX_HEADER_VALUE_CHARS) {
                "HTTP request header exceeds the size limit"
            }
            require(name.lowercase(Locale.ROOT) !in BLOCKED_REQUEST_HEADERS) {
                "HTTP request contains a restricted header"
            }
            totalBytes += name.toByteArray(Charsets.UTF_8).size + value.toByteArray(Charsets.UTF_8).size
            require(totalBytes <= MAX_REQUEST_HEADER_BYTES) { "HTTP request headers exceed the size limit" }
            result[name] = value
        }
        return result
    }

    private fun responseToJson(
        response: okhttp3.Response,
        url: HttpUrl,
        secretsUsed: List<String>,
    ): String {
        val responseBody = response.body
        val mediaType = responseBody?.contentType()
        val expectedLength = responseBody?.contentLength() ?: 0L
        val isText = mediaType != null && (
            mediaType.type == "text" ||
                mediaType.subtype.contains("json") || mediaType.subtype.contains("xml") ||
                mediaType.subtype.contains("javascript") || mediaType.subtype == "x-www-form-urlencoded"
            )
        val limit = if (isText) MAX_TEXT_RESPONSE_BYTES else MAX_BINARY_RESPONSE_BYTES
        require(expectedLength < 0 || expectedLength <= limit) {
            "Custom tool HTTP response exceeds the size limit"
        }

        val bytes = try {
            responseBody?.byteStream()?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= limit) { "Custom tool HTTP response exceeds the size limit" }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            } ?: ByteArray(0)
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (_: IOException) {
            throw IllegalStateException("Custom tool HTTP response could not be read")
        }

        val charset: Charset = mediaType?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        val decodedText = if (isText) bytes.toString(charset) else ""
        val responseText = when {
            !isText -> ""
            mediaType?.subtype?.contains("json") == true -> redactJsonSecrets(decodedText, secretsUsed)
            else -> redactSecrets(decodedText, secretsUsed)
        }
        if (!isText && secretsUsed.any { secret ->
                secret.toByteArray(Charsets.UTF_8).let { needle ->
                    needle.isNotEmpty() && (
                        bytes.containsBytes(needle) ||
                            bytes.containsBytes(Base64.getEncoder().encode(needle))
                        )
                }
            }
        ) {
            throw IllegalStateException("The API response contained a configured Secret and was blocked")
        }
        // Keep every response view consistent: arrayBuffer() must not bypass text redaction.
        val safeBodyBytes = if (isText) responseText.toByteArray(charset) else bytes
        val headerObject = buildJsonObject {
            response.headers.names()
                .filterNot { it.lowercase(Locale.ROOT) in BLOCKED_RESPONSE_HEADERS }
                .forEach { name ->
                    val values = response.headers.values(name).map { redactSecrets(it, secretsUsed) }
                    put(name.lowercase(Locale.ROOT), values.joinToString(", "))
                }
        }
        return buildJsonObject {
            put("status", response.code)
            put("ok", response.isSuccessful)
            put("statusText", redactSecrets(response.message, secretsUsed))
            put(
                "url",
                redactSecrets(url.newBuilder().query(null).fragment(null).build().toString(), secretsUsed)
            )
            put("headers", headerObject)
            put("isText", isText)
            put("body", responseText)
            put("bodyBase64", Base64.getEncoder().encodeToString(safeBodyBytes))
        }.toString()
    }

    private fun resolveSecretHeaders(
        extension: CustomExtension,
        headers: Map<String, String>,
    ): Pair<Map<String, String>, List<String>> {
        val declaredSecrets = extension.secrets.map { it.name }.toSet()
        val usedSecrets = linkedSetOf<String>()
        val resolved = headers.mapValues { (_, value) ->
            SECRET_REFERENCE_PATTERN.replace(value) { match ->
                val name = match.groupValues[1]
                require(name in declaredSecrets) {
                    "HTTP request references an undeclared Secret: $name"
                }
                val secret = secretStore.getSecret(extension.id, name)
                    ?: throw IllegalStateException("Secret '$name' has not been configured")
                usedSecrets += secret
                secret
            }.also { result ->
                require(!result.contains("{{secret:")) {
                    "HTTP request contains an invalid Secret reference"
                }
            }
        }
        val totalBytes = resolved.entries.sumOf { (name, value) ->
            name.toByteArray(Charsets.UTF_8).size + value.toByteArray(Charsets.UTF_8).size
        }
        require(totalBytes <= MAX_REQUEST_HEADER_BYTES &&
            resolved.values.all { it.length <= MAX_HEADER_VALUE_CHARS }
        ) { "Resolved HTTP request headers exceed the size limit" }
        return resolved to usedSecrets.toList()
    }

    private fun redactSecrets(value: String, secrets: List<String>): String =
        secrets.filter { it.isNotEmpty() }.fold(value) { text, secret ->
            text.replace(secret, REDACTED_SECRET, ignoreCase = false)
        }

    /** Parse JSON before redaction so escaped spellings cannot bypass secret filtering. */
    private fun redactJsonSecrets(value: String, secrets: List<String>): String {
        val parsed = runCatching { Json.parseToJsonElement(value) }.getOrNull()
            ?: return redactSecrets(value, secrets)
        fun sanitize(element: JsonElement): JsonElement = when (element) {
            is JsonObject -> JsonObject(element.entries.associate { (key, child) ->
                redactSecrets(key, secrets) to sanitize(child)
            })
            is JsonArray -> JsonArray(element.map(::sanitize))
            is JsonPrimitive -> if (element.isString) {
                JsonPrimitive(redactSecrets(element.content, secrets))
            } else {
                element
            }
            else -> element
        }
        return sanitize(parsed).toString()
    }

    private fun ByteArray.containsBytes(needle: ByteArray): Boolean {
        if (needle.isEmpty() || needle.size > size) return false
        for (start in 0..(size - needle.size)) {
            var match = true
            for (offset in needle.indices) {
                if (this[start + offset] != needle[offset]) {
                    match = false
                    break
                }
            }
            if (match) return true
        }
        return false
    }

    private fun sameOrigin(first: HttpUrl, second: HttpUrl): Boolean =
        first.scheme == second.scheme && first.host == second.host && first.port == second.port

    private fun isSafeAddress(address: InetAddress, allowLocalNetwork: Boolean): Boolean {
        if (address.isAnyLocalAddress || address.isMulticastAddress) return false
        val bytes = address.address
        val ipv4 = when {
            address is Inet4Address -> bytes
            bytes.size == 16 && bytes.take(10).all { it == 0.toByte() } &&
                bytes[10] == 0xff.toByte() && bytes[11] == 0xff.toByte() -> bytes.copyOfRange(12, 16)
            else -> null
        }
        if (ipv4 != null) {
            val first = ipv4[0].toInt() and 0xff
            val second = ipv4[1].toInt() and 0xff
            val third = ipv4[2].toInt() and 0xff
            val fourth = ipv4[3].toInt() and 0xff
            val reserved = first == 0 || first >= 224 ||
                (first == 100 && second in 64..127) ||
                (first == 192 && second == 0 && (third == 0 || third == 2)) ||
                (first == 192 && second == 88 && third == 99) ||
                (first == 198 && second in 18..19) ||
                (first == 198 && second == 51 && third == 100) ||
                (first == 203 && second == 0 && third == 113)
            if (reserved) return false

            // Cloud metadata addresses remain blocked even with local-network access enabled.
            if ((first == 169 && second == 254 && third == 169 && fourth == 254) ||
                (first == 169 && second == 254 && third == 170 && fourth == 2) ||
                (first == 100 && second == 100 && third == 100 && fourth == 200) ||
                (first == 168 && second == 63 && third == 129 && fourth == 16)
            ) return false

            val local = first == 10 || first == 127 ||
                (first == 172 && second in 16..31) ||
                (first == 192 && second == 168) ||
                (first == 169 && second == 254)
            return !local || allowLocalNetwork
        }

        if (address is java.net.Inet6Address) {
            if (address.isLinkLocalAddress || address.isSiteLocalAddress) return false
            val isLoopback = address.isLoopbackAddress
            val isUniqueLocal = (bytes[0].toInt() and 0xfe) == 0xfc
            val isDocumentation = (bytes[0].toInt() and 0xff) == 0x20 &&
                (bytes[1].toInt() and 0xff) == 0x01 &&
                (bytes[2].toInt() and 0xff) == 0x0d &&
                (bytes[3].toInt() and 0xff) == 0xb8
            if (isDocumentation) return false
            if (isLoopback || isUniqueLocal) return allowLocalNetwork
        }
        return true
    }

    private fun isLocalAddress(address: InetAddress): Boolean {
        val bytes = address.address
        if (address is Inet4Address) {
            val first = bytes[0].toInt() and 0xff
            val second = bytes[1].toInt() and 0xff
            return first == 10 || first == 127 ||
                (first == 172 && second in 16..31) ||
                (first == 192 && second == 168) ||
                (first == 169 && second == 254)
        }
        if (address is java.net.Inet6Address) {
            return address.isLoopbackAddress || (bytes[0].toInt() and 0xfe) == 0xfc
        }
        return false
    }

    companion object {
        fun create(
            secretStore: CustomToolSecretStore,
            storage: CustomToolStorage,
        ): CustomToolHttpRuntime {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .callTimeout(25, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                // Deliberately no request, body, or header logging interceptors.
                .build()
            return CustomToolHttpRuntime(client, secretStore, storage)
        }

        private const val DEFAULT_EXECUTION_TIMEOUT_MS = 45_000L
        private const val JS_MEMORY_LIMIT_BYTES = 64L * 1024 * 1024
        private const val JS_MAX_STACK_BYTES = 256L * 1024
        private const val MAX_LOG_CHARS = 64 * 1024
        private const val MAX_RESULT_CHARS = 1024 * 1024
        private const val MAX_URL_LENGTH = 2_048
        private const val MAX_REQUEST_BODY_BYTES = 1024 * 1024
        private const val MAX_BASE64_REQUEST_CHARS = ((MAX_REQUEST_BODY_BYTES + 2) / 3) * 4
        private const val MAX_BASE64_STORAGE_CHARS = ((1024 * 1024 + 2) / 3) * 4
        private const val MAX_REQUEST_HEADERS = 40
        private const val MAX_HEADER_VALUE_CHARS = 8 * 1024
        private const val MAX_REQUEST_HEADER_BYTES = 32 * 1024
        private const val MAX_TEXT_RESPONSE_BYTES = 1024 * 1024
        private const val MAX_BINARY_RESPONSE_BYTES = 8 * 1024 * 1024
        private const val MAX_REDIRECTS = 5
        private const val MAX_REQUESTS_PER_EXECUTION = 20
        private const val MAX_STORAGE_OPERATIONS_PER_EXECUTION = 100
        private const val REDACTED_SECRET = "[REDACTED]"
        private val SECRET_REFERENCE_PATTERN = Regex("""\{\{secret:([A-Z][A-Z0-9_]{0,63})}}""")
        private val ALLOWED_METHODS = setOf("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private val BLOCKED_REQUEST_HEADERS = setOf(
            "host", "content-length", "transfer-encoding", "connection",
            "proxy-authorization", "proxy-connection", "keep-alive", "upgrade", "te", "trailer",
        )
        private val BLOCKED_RESPONSE_HEADERS = setOf(
            "set-cookie", "set-cookie2", "www-authenticate", "proxy-authenticate",
            "authentication-info", "proxy-authentication-info",
        )
        private const val STORAGE_POLYFILL = """
globalThis.storage = Object.freeze({
    readText(name) {
        const result = JSON.parse(__customToolStorageRead(String(name)));
        return result.found ? result.value : null;
    },
    writeText(name, value) {
        return __customToolStorageWrite(String(name), String(value));
    },
    readBase64(name) {
        const result = JSON.parse(__customToolStorageReadBase64(String(name)));
        return result.found ? result.value : null;
    },
    writeBase64(name, value) {
        return __customToolStorageWriteBase64(String(name), String(value));
    },
    delete(name) {
        return __customToolStorageDelete(String(name));
    },
    list() {
        return JSON.parse(__customToolStorageList());
    }
});
"""
        private const val FETCH_POLYFILL = """
globalThis.fetch = function(url, options) {
    options = options || {};
    const method = (options.method || 'GET').toUpperCase();
    const headers = options.headers ? JSON.stringify(options.headers) : null;
    let body = options.body;
    let bodyEncoding = 'text';

    const bytesToBase64 = bytes => {
        const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
        let output = '';
        for (let index = 0; index < bytes.length; index += 3) {
            const a = bytes[index];
            const hasB = index + 1 < bytes.length;
            const hasC = index + 2 < bytes.length;
            const b = hasB ? bytes[index + 1] : 0;
            const c = hasC ? bytes[index + 2] : 0;
            output += chars[a >> 2];
            output += chars[((a & 3) << 4) | (b >> 4)];
            output += hasB ? chars[((b & 15) << 2) | (c >> 6)] : '=';
            output += hasC ? chars[c & 63] : '=';
        }
        return output;
    };
    const base64ToBytes = encoded => {
        const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
        const padding = encoded.endsWith('==') ? 2 : encoded.endsWith('=') ? 1 : 0;
        const output = new Uint8Array(Math.floor(encoded.length * 3 / 4) - padding);
        let offset = 0;
        for (let index = 0; index < encoded.length; index += 4) {
            const a = chars.indexOf(encoded[index]);
            const b = chars.indexOf(encoded[index + 1]);
            const c = encoded[index + 2] === '=' ? 0 : chars.indexOf(encoded[index + 2]);
            const d = encoded[index + 3] === '=' ? 0 : chars.indexOf(encoded[index + 3]);
            if (offset < output.length) output[offset++] = (a << 2) | (b >> 4);
            if (offset < output.length) output[offset++] = ((b & 15) << 4) | (c >> 2);
            if (offset < output.length) output[offset++] = ((c & 3) << 6) | d;
        }
        return output;
    };

    if (body && typeof body === 'object') {
        if (body instanceof ArrayBuffer) {
            body = bytesToBase64(new Uint8Array(body));
            bodyEncoding = 'base64';
        } else if (ArrayBuffer.isView(body)) {
            body = bytesToBase64(new Uint8Array(body.buffer, body.byteOffset, body.byteLength));
            bodyEncoding = 'base64';
        } else {
            body = JSON.stringify(body);
        }
    } else if (typeof body !== 'string') {
        body = null;
    }

    const data = JSON.parse(__customToolHttpRequest(url, method, headers, body, bodyEncoding));
    const responseHeaders = data.headers || {};
    const headerApi = {
        get: name => responseHeaders[String(name).toLowerCase()] ?? null,
        has: name => Object.prototype.hasOwnProperty.call(responseHeaders, String(name).toLowerCase()),
        forEach: callback => Object.keys(responseHeaders).forEach(key => callback(responseHeaders[key], key))
    };
    return {
        status: data.status,
        ok: data.ok,
        statusText: data.statusText,
        url: data.url,
        headers: headerApi,
        text: function() {
            if (!data.isText) throw new Error('Response body is binary; use arrayBuffer()');
            return data.body;
        },
        json: function() {
            if (!data.isText) throw new Error('Response body is binary; use arrayBuffer()');
            return JSON.parse(data.body);
        },
        arrayBuffer: function() {
            return base64ToBytes(data.bodyBase64 || '').buffer;
        }
    };
};
"""
    }
}
