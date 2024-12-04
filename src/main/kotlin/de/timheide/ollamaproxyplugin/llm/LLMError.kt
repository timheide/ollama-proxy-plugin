package de.timheide.ollama_proxy.llm

sealed class LLMError : Exception() {
    data class ApiError(override val message: String) : LLMError()
    data class ParseError(override val message: String) : LLMError()
}
