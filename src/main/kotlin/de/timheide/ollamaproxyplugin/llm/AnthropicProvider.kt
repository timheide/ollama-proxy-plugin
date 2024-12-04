package de.timheide.ollamaproxyplugin.llm

import de.timheide.ollamaproxyplugin.models.*
import de.timheide.ollamaproxyplugin.provider.LLMProvider
import kotlinx.serialization.json.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.http.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import java.time.Instant
import java.time.format.DateTimeFormatter

class AnthropicProvider(
    private val apiKey: String,
    private val client: HttpClient = HttpClient(CIO) {  // Specify CIO engine
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            json()
        }
    }
): LLMProvider {

    override suspend fun chat(messages: List<Message>, options: ChatOptions): Result<ChatResponse> = runCatching {
        val (systemMessages, chatMessages) = messages.partition { it.role == "system" }
        val systemContent = systemMessages.firstOrNull()?.content.orEmpty()

        val response = client.post("https://api.anthropic.com/v1/messages") {
            contentType(ContentType.Application.Json)
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
            setBody(buildJsonObject {
                put("model", "claude-3-5-sonnet-20241022")
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

        if (response.status != HttpStatusCode.OK) {
            throw LLMError.ApiError(response.bodyAsText())
        }

        val responseJson = response.body<JsonObject>()
        ChatResponse(
            content = responseJson["content"]?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content
                ?: throw LLMError.ParseError("Missing content"),
            promptTokens = responseJson["usage"]?.jsonObject?.get("input_tokens")?.jsonPrimitive?.intOrNull,
            completionTokens = responseJson["usage"]?.jsonObject?.get("output_tokens")?.jsonPrimitive?.intOrNull,
            model = "claude-3-5-sonnet-20241022",
            createdAt = Instant.now().toString()
        )
    }

    override fun getModels(): List<Model> {
        val currentTime = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

        return listOf(
            Model(
                name = "claude-3-5-sonnet-20241022",
                model = "claude-3-5-sonnet-20241022",
                modified_at = currentTime,
                size = 175_000_000_000L,
                digest = "anthropic-claude-3-5-sonnet",
                details = ModelDetails()
            ),
            Model(
                name = "claude-3-5-haiku-20241022",
                model = "claude-3-5-haiku-20241022",
                modified_at = currentTime,
                size = 175_000_000_000L,
                digest = "anthropic-claude-3-5-haiku",
                details = ModelDetails()
            ),
            Model(
                name = "claude-3-opus-20240229",
                model = "claude-3-opus-20240229",
                modified_at = currentTime,
                size = 175_000_000_000L,
                digest = "anthropic-claude-3-opus",
                details = ModelDetails()
            )
        )
    }



    override suspend fun getModelDetails(modelName: String): Result<JsonObject> = runCatching {
        val currentTime = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

        val (description, parameterSize) = when (modelName) {
            "claude-3-5-sonnet-20241022" -> "Our most intelligent model" to "200B"
            "claude-3-5-haiku-20241022" -> "Our fastest model" to "100B"
            "claude-3-opus-20240229" -> "Powerful model for highly complex tasks" to "400B"
            else -> throw LLMError.ParseError("Model not found")
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
    }
}
