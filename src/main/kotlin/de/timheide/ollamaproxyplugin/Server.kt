package de.timheide.ollamaproxyplugin

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.timheide.ollamaproxyplugin.models.BadRequestException
import de.timheide.ollamaproxyplugin.models.ChatOptions
import de.timheide.ollamaproxyplugin.models.Message
import de.timheide.ollamaproxyplugin.provider.LLMProvider
import de.timheide.ollamaproxyplugin.settings.OllamaSettingsState
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.flow.collect
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

private val logger = LoggerFactory.getLogger("ChatEndpoint")

class Server(private val llmProvider: LLMProvider) {
    private var ktorServer: ApplicationEngine? = null
    fun start() {
        val port = OllamaSettingsState.instance.port
        ktorServer = embeddedServer(Netty, port = port, configure = {
            // Configure longer timeouts for streaming responses
            responseWriteTimeoutSeconds = 600 // 10 minutes for streaming
        }) {
            install(CallLogging) {
                level = Level.DEBUG
                filter { call ->
                    call.request.path().startsWith("/api/chat")
                }
                format { call ->
                    val status = call.response.status()
                    val httpMethod = call.request.httpMethod.value
                    val path = call.request.path()
                    "[$httpMethod $path] responded with $status"
                }
            }
            install(ContentNegotiation) {
                jackson {
                    registerModule(KotlinModule.Builder().build())
                    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    setSerializationInclusion(JsonInclude.Include.NON_NULL)
                }
            }

            install(DoubleReceive) {       }

           routing {
                get("/") {
                    call.respondText("Ollama is running")
                }

                get("/api/tags") {
                    call.respond(mapOf("models" to llmProvider.getModels()))
                }

               post("/api/show") {
                   val request = call.receive<JsonObject>()

                   val modelName = request["name"]?.jsonPrimitive?.content
                       ?: throw BadRequestException("Model name is required")

                   val response = llmProvider.getModelDetails(modelName).getOrThrow()

                   call.response.header("Content-Type", "application/json")
                   call.respond(response)
               }
               post("/api/chat") {
                   try {
                       val requestBody = call.receive<String>()
                       logger.debug("Request body: $requestBody")

                       val mapper = jacksonObjectMapper()
                       val request = mapper.readTree(requestBody)

                       val messages = request["messages"]?.map {
                           Message(
                               role = it["role"]?.asText() ?: "user",
                               content = it["content"]?.asText() ?: ""
                           )
                       } ?: throw BadRequestException("Invalid messages format")

                       val options = ChatOptions(
                           model = request["model"]?.asText() ?: "claude-3-5-sonnet-20241022",
                           temperature = request["options"]?.get("temperature")?.floatValue() ?: 0.7f,
                           topP = request["options"]?.get("top_p")?.floatValue() ?: 0.9f
                       )

                       val isStreaming = request["stream"]?.booleanValue() ?: false

                       if (isStreaming) {
                           call.response.header("Content-Type", "application/json")
                           call.response.header("Cache-Control", "no-cache")
                           call.response.header("Connection", "keep-alive")

                           var lastResponse: de.timheide.ollamaproxyplugin.models.ChatResponse? = null
                           var previousContentLength = 0
                           
                           call.respondTextWriter {
                               llmProvider.chatStream(messages, options).collect { result ->
                                   result.fold(
                                       onSuccess = { response ->
                                           lastResponse = response
                                           // Extract only the new content delta for JetBrains AI Assistant
                                           val deltaContent = if (response.content.length > previousContentLength) {
                                               response.content.substring(previousContentLength)
                                           } else {
                                               ""
                                           }
                                           previousContentLength = response.content.length
                                           
                                           // Only send a chunk if there's new content
                                           if (deltaContent.isNotEmpty()) {
                                               val responseBody = mapOf(
                                                   "model" to response.model,
                                                   "created_at" to response.createdAt,
                                                   "message" to mapOf(
                                                       "role" to "assistant",
                                                       "content" to deltaContent  // Send only the delta
                                                   ),
                                                   "done" to false,
                                                   "total_duration" to 0,
                                                   "load_duration" to 0,
                                                   "prompt_eval_count" to response.promptTokens,
                                                   "eval_count" to response.completionTokens,
                                                   "eval_duration" to 0
                                               )
                                               write("${mapper.writeValueAsString(responseBody)}\n")
                                               flush()
                                           }
                                       },
                                       onFailure = { error ->
                                           logger.error("Streaming error", error)
                                           write("${mapper.writeValueAsString(mapOf("error" to error.message))}\n")
                                           flush()
                                       }
                                   )
                               }
                               
                               // Send final "done" message (empty content for delta format)
                               lastResponse?.let { response ->
                                   val finalResponseBody = mapOf(
                                       "model" to response.model,
                                       "created_at" to response.createdAt,
                                       "message" to mapOf(
                                           "role" to "assistant",
                                           "content" to ""  // Empty content in final message for delta format
                                       ),
                                       "done" to true,
                                       "total_duration" to 0,
                                       "load_duration" to 0,
                                       "prompt_eval_count" to response.promptTokens,
                                       "eval_count" to response.completionTokens,
                                       "eval_duration" to 0
                                   )
                                   write("${mapper.writeValueAsString(finalResponseBody)}\n")
                                   flush()
                               }
                           }
                       } else {
                           val response = llmProvider.chat(messages, options).getOrThrow()
                           val responseBody = mapOf(
                               "model" to response.model,
                               "created_at" to response.createdAt,
                               "message" to mapOf(
                                   "role" to "assistant",
                                   "content" to response.content
                               ),
                               "done" to true,
                               "total_duration" to 0,
                               "load_duration" to 0,
                               "prompt_eval_count" to response.promptTokens,
                               "eval_count" to response.completionTokens,
                               "eval_duration" to 0
                           )

                           logger.debug("Response body: ${mapper.writeValueAsString(responseBody)}")
                           call.respond(responseBody)
                       }
                   } catch (e: Exception) {
                       logger.error("Error processing chat request", e)
                       throw e
                   }
               }


           }
        }.start(wait = false)
    }
    fun stop() {
        ktorServer?.stop(1000, 2000) // graceful shutdown with 1s grace period and 2s timeout
        ktorServer = null
    }
}
