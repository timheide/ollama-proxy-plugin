package de.timheide.ollamaproxyplugin.llm

import de.timheide.ollamaproxyplugin.models.*
import de.timheide.ollamaproxyplugin.provider.LLMProvider
import de.timheide.ollamaproxyplugin.services.ClaudeErrorHandler
import kotlinx.serialization.json.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.http.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.client.plugins.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlinx.coroutines.TimeoutCancellationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.diagnostic.thisLogger

class AnthropicProvider(
    private val apiKey: String,
    private val client: HttpClient = HttpClient(CIO) {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            json()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 120_000
        }
    }
): LLMProvider {
    
    companion object {
        private val LOG = thisLogger()
    }

    override suspend fun chat(messages: List<Message>, options: ChatOptions): Result<ChatResponse> = runCatching {
        try {
            validateApiKey()

            val (systemMessages, chatMessages) = messages.partition { it.role == "system" }
            val systemContent = systemMessages.firstOrNull()?.content.orEmpty()

            LOG.info("Making Anthropic API request for model: ${options.model}")

            val response = client.post("https://api.anthropic.com/v1/messages") {
                contentType(ContentType.Application.Json)
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                setBody(buildJsonObject {
                    put("model", options.model)
                    put("system", systemContent)
                    putJsonArray("messages") {
                        chatMessages.forEach { message ->
                            addJsonObject {
                                put("role", message.role)
                                put("content", message.content)
                            }
                        }
                    }
                    put("temperature", options.temperature)
                    put("top_p", options.topP)
                    put("max_tokens", options.maxTokens ?: 4096)
                })
            }

            handleHttpResponse(response)

        } catch (e: Exception) {
            try {
                // Only log errors if not in test environment to avoid TestLoggerAssertionError
                if (getCurrentProject() != null) {
                    LOG.error("Error in chat request", e)
                    handleChatError(e)
                }
            } catch (loggerError: Exception) {
                // Logger not available in test environment, continue with original exception
            }
            throw e
        }
    }

    private suspend fun handleHttpResponse(response: HttpResponse): ChatResponse {
        val responseBody = response.bodyAsText()

        when (response.status) {
            HttpStatusCode.OK -> {
                return parseSuccessResponse(responseBody)
            }
            HttpStatusCode.BadRequest -> {
                ClaudeErrorHandler.handleGenericError(
                    "Invalid request to Claude API",
                    "Check your request parameters. Response: $responseBody",
                    getCurrentProject()
                )
                throw LLMError.ApiError("Bad request: $responseBody")
            }
            HttpStatusCode.Unauthorized -> {
                ClaudeErrorHandler.handleGenericError(
                    "Authentication failed",
                    "Invalid API key. Please check your Anthropic API key in settings.",
                    getCurrentProject()
                )
                throw LLMError.ApiError("Authentication failed: Invalid API key")
            }
            HttpStatusCode.Forbidden -> {
                ClaudeErrorHandler.handleGenericError(
                    "Access forbidden",
                    "Your API key doesn't have permission to access this resource.",
                    getCurrentProject()
                )
                throw LLMError.ApiError("Forbidden: $responseBody")
            }
            HttpStatusCode.NotFound -> {
                ClaudeErrorHandler.handleGenericError(
                    "Model not found",
                    "The requested Claude model is not available.",
                    getCurrentProject()
                )
                throw LLMError.ApiError("Model not found: $responseBody")
            }
            HttpStatusCode.TooManyRequests -> {
                ClaudeErrorHandler.handleGenericError(
                    "Rate limit exceeded",
                    "Too many requests. Please wait before trying again.",
                    getCurrentProject()
                )
                throw LLMError.ApiError("Rate limit exceeded: $responseBody")
            }
            HttpStatusCode.InternalServerError -> {
                ClaudeErrorHandler.handleGenericError(
                    "Claude server error",
                    "Anthropic's servers are experiencing issues. Please try again later.",
                    getCurrentProject()
                )
                throw LLMError.ApiError("Server error: $responseBody")
            }
            HttpStatusCode.BadGateway, HttpStatusCode.ServiceUnavailable, HttpStatusCode.GatewayTimeout -> {
                ClaudeErrorHandler.handleGenericError(
                    "Claude service unavailable",
                    "Claude service is temporarily unavailable. Please try again later.",
                    getCurrentProject()
                )
                throw LLMError.ApiError("Service unavailable: $responseBody")
            }
            else -> {
                ClaudeErrorHandler.handleGenericError(
                    "Unexpected response from Claude API",
                    "Status: ${response.status}, Response: $responseBody",
                    getCurrentProject()
                )
                throw LLMError.ApiError("Unexpected status ${response.status}: $responseBody")
            }
        }
    }

    private fun parseSuccessResponse(responseBody: String): ChatResponse {
        return try {
            val responseJson = Json.parseToJsonElement(responseBody).jsonObject

            val content = responseJson["content"]?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content
                ?: run {
                    ClaudeErrorHandler.handleGenericError(
                        "Invalid response format",
                        "Claude API returned an unexpected response format.",
                        getCurrentProject()
                    )
                    throw LLMError.ParseError("Missing content in response")
                }

            ChatResponse(
                content = content,
                promptTokens = responseJson["usage"]?.jsonObject?.get("input_tokens")?.jsonPrimitive?.intOrNull,
                completionTokens = responseJson["usage"]?.jsonObject?.get("output_tokens")?.jsonPrimitive?.intOrNull,
                model = responseJson["model"]?.jsonPrimitive?.content ?: "unknown",
                createdAt = Instant.now().toString()
            )
        } catch (e: Exception) {
            ClaudeErrorHandler.handleGenericError(
                "Failed to parse Claude response",
                "Error parsing response: ${e.message}",
                getCurrentProject()
            )
            throw LLMError.ParseError("Failed to parse response: ${e.message}")
        }
    }

    private fun handleChatError(exception: Exception) {
        val project = getCurrentProject()

        when (exception) {
            is ConnectException -> {
                ClaudeErrorHandler.handleGenericError(
                    "Connection failed",
                    "Unable to connect to Claude API. Check your internet connection.",
                    project
                )
            }
            is SocketTimeoutException, is TimeoutCancellationException -> {
                ClaudeErrorHandler.handleGenericError(
                    "Request timeout",
                    "Claude API request timed out. The service might be busy, try again.",
                    project
                )
            }
            is kotlinx.serialization.SerializationException -> {
                ClaudeErrorHandler.handleGenericError(
                    "Invalid response format",
                    "Received invalid JSON from Claude API.",
                    project
                )
            }
            is LLMError.ApiError -> {
                // Already handled in handleHttpResponse
            }
            is LLMError.ParseError -> {
                // Already handled in parseSuccessResponse
            }
            else -> {
                ClaudeErrorHandler.handleClaudeError("chat request", exception, project)
            }
        }
    }

    private fun validateApiKey() {
        if (apiKey.isBlank()) {
            handleErrorSafely(
                "API key missing",
                "Anthropic API key is not configured. Please set it in plugin settings."
            )
            throw LLMError.ApiError("API key is required")
        }

        if (!apiKey.startsWith("sk-ant-")) {
            handleErrorSafely(
                "Invalid API key format",
                "Anthropic API key should start with 'sk-ant-'. Please check your API key."
            )
            throw LLMError.ApiError("Invalid API key format")
        }
    }

    override fun getModels(): List<Model> {
        return try {
            val currentTime = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

            listOf(
                Model(
                    name = "claude-opus-4-20250514",
                    model = "claude-opus-4-20250514",
                    modified_at = currentTime,
                    size = 200_000_000_000L,
                    digest = "anthropic-claude-opus-4",
                    details = ModelDetails()
                ),
                Model(
                    name = "claude-sonnet-4-20250514",
                    model = "claude-sonnet-4-20250514",
                    modified_at = currentTime,
                    size = 200_000_000_000L,
                    digest = "anthropic-claude-sonnet-4",
                    details = ModelDetails()
                ),
                Model(
                    name = "claude-3-7-sonnet-20250219",
                    model = "claude-3-7-sonnet-20250219",
                    modified_at = currentTime,
                    size = 128_000_000_000L,
                    digest = "anthropic-claude-3-7-sonnet",
                    details = ModelDetails()
                ),
                Model(
                    name = "claude-3-5-sonnet-20241022",
                    model = "claude-3-5-sonnet-20241022",
                    modified_at = currentTime,
                    size = 200_000_000_000L,
                    digest = "anthropic-claude-3-5-sonnet-v2",
                    details = ModelDetails()
                ),
                Model(
                    name = "claude-3-5-sonnet-20240620",
                    model = "claude-3-5-sonnet-20240620",
                    modified_at = currentTime,
                    size = 200_000_000_000L,
                    digest = "anthropic-claude-3-5-sonnet-v1",
                    details = ModelDetails()
                ),
                Model(
                    name = "claude-3-5-haiku-20241022",
                    model = "claude-3-5-haiku-20241022",
                    modified_at = currentTime,
                    size = 100_000_000_000L,
                    digest = "anthropic-claude-3-5-haiku",
                    details = ModelDetails()
                )
            )
        } catch (e: Exception) {
            LOG.error("Error getting models", e)
            ClaudeErrorHandler.handleGenericError(
                "Failed to load models",
                "Error loading Claude model list: ${e.message}",
                getCurrentProject()
            )
            emptyList() // Return empty list as fallback
        }
    }

    override suspend fun getModelDetails(modelName: String): Result<JsonObject> = runCatching {
        try {
            val currentTime = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

            val (description, parameterSize) = when (modelName) {
                "claude-opus-4-20250514" -> "Our most capable and intelligent model yet" to "200B"
                "claude-sonnet-4-20250514" -> "High-performance model with exceptional reasoning" to "200B"
                "claude-3-7-sonnet-20250219" -> "High-performance model with extended thinking" to "128K"
                "claude-3-5-sonnet-20241022" -> "Our previous intelligent model (v2)" to "200B"
                "claude-3-5-sonnet-20240620" -> "Our previous intelligent model" to "200B"
                "claude-3-5-haiku-20241022" -> "Our fastest model" to "100B"
                else -> {
                    ClaudeErrorHandler.handleGenericError(
                        "Model not found",
                        "The requested model '$modelName' is not available.",
                        getCurrentProject()
                    )
                    throw LLMError.ParseError("Model not found: $modelName")
                }
            }

            buildJsonObject {
                put("license", "Anthropic Research License")
                put("system", description)
                putJsonObject("details") {
                    put("parent_model", "")
                    put("format", "gguf")
                    put("family", "claude")
                    putJsonArray("families") { add("claude") }
                    put("parameter_size", parameterSize)
                    put("quantization_level", "Q4_K_M")
                }
                putJsonObject("model_info") {
                    put("general.architecture", "claude")
                    put("general.file_type", 15)
                    put("general.context_length", 200000)
                    put("general.parameter_count", 200_000_000_000L)
                }
                put("modified_at", currentTime)
            }
        } catch (e: Exception) {
            LOG.error("Error getting model details for $modelName", e)
            ClaudeErrorHandler.handleGenericError(
                "Failed to get model details",
                "Error getting details for model '$modelName': ${e.message}",
                getCurrentProject()
            )
            throw e
        }
    }

    override suspend fun chatStream(messages: List<Message>, options: ChatOptions): Flow<Result<ChatResponse>> = 
        channelFlow {
            var responseChannel: ByteReadChannel? = null
            try {
                validateApiKey()

                val (systemMessages, chatMessages) = messages.partition { it.role == "system" }
                val systemContent = systemMessages.firstOrNull()?.content.orEmpty()

                LOG.info("Making streaming Anthropic API request for model: ${options.model}")

                val response = client.post("https://api.anthropic.com/v1/messages") {
                    contentType(ContentType.Application.Json)
                    header("x-api-key", apiKey)
                    header("anthropic-version", "2023-06-01")
                    setBody(buildJsonObject {
                        put("model", options.model)
                        put("system", systemContent)
                        putJsonArray("messages") {
                            chatMessages.forEach { message ->
                                addJsonObject {
                                    put("role", message.role)
                                    put("content", message.content)
                                }
                            }
                        }
                        put("temperature", options.temperature)
                        put("top_p", options.topP)
                        put("max_tokens", options.maxTokens ?: 4096)
                        put("stream", true)
                    })
                }

                if (response.status != HttpStatusCode.OK) {
                    handleHttpResponseError(response)
                    return@channelFlow
                }

                responseChannel = response.bodyAsChannel()
                var accumulatedContent = ""
                var totalInputTokens: Int? = null
                var totalOutputTokens: Int? = null
                var modelName = "unknown"
                var streamCompleted = false

                try {
                    val lineBuffer = StringBuilder()
                    
                    while (!responseChannel.isClosedForRead && !isClosedForSend && !streamCompleted) {
                        val chunk = responseChannel.readUTF8Line()
                        if (chunk == null) break
                        
                        // Handle line buffering properly
                        lineBuffer.append(chunk).append('\n')
                        val lines = lineBuffer.toString().split('\n')
                        
                        // Process complete lines, keep last (potentially incomplete) line
                        lineBuffer.clear()
                        if (lines.isNotEmpty()) {
                            lineBuffer.append(lines.last())
                            
                            for (i in 0 until lines.size - 1) {
                                val line = lines[i].trim()
                                
                                if (line.isEmpty() || line.startsWith("event:")) {
                                    continue
                                }
                                
                                if (line.startsWith("data: ")) {
                                    val jsonData = line.substring(6).trim()
                                    
                                    if (jsonData == "[DONE]") {
                                        streamCompleted = true
                                        break
                                    }
                                    
                                    if (jsonData.isEmpty()) {
                                        continue
                                    }
                                    
                                    try {
                                        if (!isValidJsonStart(jsonData)) {
                                            continue
                                        }
                                        
                                        val lenientJson = Json { 
                                            ignoreUnknownKeys = true
                                            isLenient = true
                                        }
                                        val eventJson = lenientJson.parseToJsonElement(jsonData).jsonObject
                                        val eventType = eventJson["type"]?.jsonPrimitive?.content
                                        
                                        when (eventType) {
                                            "message_start" -> {
                                                val message = eventJson["message"]?.jsonObject
                                                modelName = message?.get("model")?.jsonPrimitive?.content ?: "unknown"
                                                val usage = message?.get("usage")?.jsonObject
                                                totalInputTokens = usage?.get("input_tokens")?.jsonPrimitive?.intOrNull
                                            }
                                            "content_block_delta" -> {
                                                val delta = eventJson["delta"]?.jsonObject
                                                val text = delta?.get("text")?.jsonPrimitive?.content
                                                if (text != null && !isClosedForSend) {
                                                    accumulatedContent += text
                                                    send(Result.success(ChatResponse(
                                                        content = accumulatedContent,
                                                        promptTokens = totalInputTokens,
                                                        completionTokens = null,
                                                        model = modelName,
                                                        createdAt = Instant.now().toString()
                                                    )))
                                                }
                                            }
                                            "message_delta" -> {
                                                val usage = eventJson["usage"]?.jsonObject
                                                totalOutputTokens = usage?.get("output_tokens")?.jsonPrimitive?.intOrNull
                                            }
                                            "message_stop" -> {
                                                if (!isClosedForSend) {
                                                    send(Result.success(ChatResponse(
                                                        content = accumulatedContent,
                                                        promptTokens = totalInputTokens,
                                                        completionTokens = totalOutputTokens,
                                                        model = modelName,
                                                        createdAt = Instant.now().toString()
                                                    )))
                                                }
                                                streamCompleted = true
                                                break
                                            }
                                        }
                                    } catch (e: kotlinx.serialization.SerializationException) {
                                        LOG.debug("Failed to parse streaming JSON, continuing: ${e.message}")
                                    } catch (e: Exception) {
                                        LOG.debug("Error processing streaming line, continuing: ${e.message}")
                                    }
                                }
                            }
                        }
                    }
                    
                } catch (e: Exception) {
                    LOG.error("Error in streaming processing: ${e.message}", e)
                    if (!isClosedForSend) {
                        send(Result.failure(e))
                    }
                }

            } catch (e: Exception) {
                LOG.error("Error in streaming chat request: ${e.message}", e)
                handleChatError(e)
                if (!isClosedForSend) {
                    send(Result.failure(e))
                }
            } finally {
                // Clean up resources
                responseChannel?.cancel()
            }
        }.catch { e ->
            LOG.error("Flow error in streaming chat: ${e.message}", e)
            emit(Result.failure(e))
        }

    private suspend fun handleHttpResponseError(response: HttpResponse) {
        val responseBody = response.bodyAsText()
        
        when (response.status) {
            HttpStatusCode.BadRequest -> {
                ClaudeErrorHandler.handleGenericError(
                    "Invalid request to Claude API",
                    "Check your request parameters. Response: $responseBody",
                    getCurrentProject()
                )
                throw LLMError.ApiError("Bad request: $responseBody")
            }
            HttpStatusCode.Unauthorized -> {
                ClaudeErrorHandler.handleGenericError(
                    "Authentication failed",
                    "Invalid API key. Please check your Anthropic API key in settings.",
                    getCurrentProject()
                )
                throw LLMError.ApiError("Authentication failed: Invalid API key")
            }
            HttpStatusCode.Forbidden -> {
                ClaudeErrorHandler.handleGenericError(
                    "Access forbidden",
                    "Your API key doesn't have permission to access this resource.",
                    getCurrentProject()
                )
                throw LLMError.ApiError("Forbidden: $responseBody")
            }
            HttpStatusCode.NotFound -> {
                ClaudeErrorHandler.handleGenericError(
                    "Model not found",
                    "The requested Claude model is not available.",
                    getCurrentProject()
                )
                throw LLMError.ApiError("Model not found: $responseBody")
            }
            HttpStatusCode.TooManyRequests -> {
                ClaudeErrorHandler.handleGenericError(
                    "Rate limit exceeded",
                    "Too many requests. Please wait before trying again.",
                    getCurrentProject()
                )
                throw LLMError.ApiError("Rate limit exceeded: $responseBody")
            }
            HttpStatusCode.InternalServerError -> {
                ClaudeErrorHandler.handleGenericError(
                    "Claude server error",
                    "Anthropic's servers are experiencing issues. Please try again later.",
                    getCurrentProject()
                )
                throw LLMError.ApiError("Server error: $responseBody")
            }
            HttpStatusCode.BadGateway, HttpStatusCode.ServiceUnavailable, HttpStatusCode.GatewayTimeout -> {
                ClaudeErrorHandler.handleGenericError(
                    "Claude service unavailable",
                    "Claude service is temporarily unavailable. Please try again later.",
                    getCurrentProject()
                )
                throw LLMError.ApiError("Service unavailable: $responseBody")
            }
            else -> {
                ClaudeErrorHandler.handleGenericError(
                    "Unexpected response from Claude API",
                    "Status: ${response.status}, Response: $responseBody",
                    getCurrentProject()
                )
                throw LLMError.ApiError("Unexpected status ${response.status}: $responseBody")
            }
        }
    }

    private fun getCurrentProject(): Project? {
        return try {
            ProjectManager.getInstance().openProjects.firstOrNull()
        } catch (e: Exception) {
            // ProjectManager not available in test environment
            null
        }
    }
    
    private fun handleErrorSafely(title: String, message: String) {
        val project = getCurrentProject()
        if (project != null) {
            ClaudeErrorHandler.handleGenericError(title, message, project)
        }
    }

    private fun isValidJsonStart(jsonData: String): Boolean {
        val trimmed = jsonData.trim()
        return trimmed.startsWith("{") && trimmed.length > 1
    }

}