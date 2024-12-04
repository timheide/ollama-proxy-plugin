package de.timheide.ollamaproxyplugin.models

data class ChatOptions(
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val maxTokens: Int? = null
)