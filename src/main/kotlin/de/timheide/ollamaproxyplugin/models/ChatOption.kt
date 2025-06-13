package de.timheide.ollamaproxyplugin.models

data class ChatOptions(
    val model: String = "claude-3-5-sonnet-20241022",
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val maxTokens: Int? = null
)