package de.timheide.ollamaproxyplugin.provider

import de.timheide.ollamaproxyplugin.models.*
import kotlinx.serialization.json.JsonObject

interface LLMProvider {
    suspend fun chat(messages: List<Message>, options: ChatOptions): Result<ChatResponse>
    fun getModels(): List<Model>
    suspend fun getModelDetails(modelName: String): Result<JsonObject>
}
