package de.timheide.ollamaproxyplugin.llm

import de.timheide.ollamaproxyplugin.models.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.assertj.core.api.Assertions.*

class StreamingIntegrationTest {

    private lateinit var mockEngine: MockEngine
    private lateinit var httpClient: HttpClient
    private lateinit var anthropicProvider: AnthropicProvider

    @BeforeEach
    fun setUp() {
        mockEngine = MockEngine { 
            respond(
                content = """{"content":[{"text":"Test response"}],"usage":{"input_tokens":10,"output_tokens":5},"model":"claude-3-5-sonnet-20241022"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        httpClient = HttpClient(mockEngine) {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 120_000
            }
        }
        anthropicProvider = AnthropicProvider("sk-ant-test-key", httpClient)
    }

    @Test
    fun `test streaming response matches Anthropic API format`() = runTest {
        // Test that basic streaming functionality is set up correctly
        val streamContent = """
            data: {"type":"message_start","message":{"model":"claude-3-5-sonnet-20241022","usage":{"input_tokens":10}}}

            data: {"type":"content_block_delta","delta":{"text":"Hello"}}

            data: {"type":"message_stop"}

        """.trimIndent()

        mockEngine = MockEngine { 
            respond(
                content = ByteReadChannel(streamContent),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/event-stream")
            )
        }
        httpClient = HttpClient(mockEngine) {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 120_000
            }
        }
        anthropicProvider = AnthropicProvider("sk-ant-test-key", httpClient)

        val messages = listOf(Message(role = "user", content = "Hello"))
        val options = ChatOptions(model = "claude-3-5-sonnet-20241022")

        val results = anthropicProvider.chatStream(messages, options).toList()

        // Should get streaming responses
        assertThat(results).isNotEmpty
        val successfulResults = results.filter { it.isSuccess }
        assertThat(successfulResults).isNotEmpty
    }

    @Test
    fun `test streaming handles interrupted JSON gracefully`() = runTest {
        // Test malformed streaming response
        val streamContent = """
            data: {"type":"message_start","message":{"model":"claude-3-5-sonnet-20241022","usage":{"input_tokens":10}}}

            data: {"type":"content_block_delta","delta":{"text":"Test"}

            data: {"type":"message_stop"}

        """.trimIndent()

        mockEngine = MockEngine { 
            respond(
                content = ByteReadChannel(streamContent),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/event-stream")
            )
        }
        httpClient = HttpClient(mockEngine) {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 120_000
            }
        }
        anthropicProvider = AnthropicProvider("sk-ant-test-key", httpClient)

        val messages = listOf(Message(role = "user", content = "Hello"))
        val options = ChatOptions(model = "claude-3-5-sonnet-20241022")

        val results = anthropicProvider.chatStream(messages, options).toList()

        // Should handle gracefully without throwing
        assertThat(results).isNotEmpty
    }

    @Test
    fun `test streaming with empty or malformed JSON chunks`() = runTest {
        // Test empty/malformed chunks
        val streamContent = """
            data: {"type":"message_start","message":{"model":"claude-3-5-sonnet-20241022","usage":{"input_tokens":10}}}

            data: 

            data: }

            data: {"type":"message_stop"}

        """.trimIndent()

        mockEngine = MockEngine { 
            respond(
                content = ByteReadChannel(streamContent),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/event-stream")
            )
        }
        httpClient = HttpClient(mockEngine) {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 120_000
            }
        }
        anthropicProvider = AnthropicProvider("sk-ant-test-key", httpClient)

        val messages = listOf(Message(role = "user", content = "Hello"))
        val options = ChatOptions(model = "claude-3-5-sonnet-20241022")

        val results = anthropicProvider.chatStream(messages, options).toList()

        // Should handle gracefully
        assertThat(results).isNotEmpty
    }

    @Test
    fun `test streaming error recovery`() = runTest {
        // Test error recovery with mixed valid/invalid JSON
        val streamContent = """
            data: {"type":"message_start","message":{"model":"claude-3-5-sonnet-20241022","usage":{"input_tokens":10}}}

            data: invalid

            data: {"type":"content_block_delta","delta":{"text":"Hello"}}

            data: {"type":"message_stop"}

        """.trimIndent()

        mockEngine = MockEngine { 
            respond(
                content = ByteReadChannel(streamContent),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/event-stream")
            )
        }
        httpClient = HttpClient(mockEngine) {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 120_000
            }
        }
        anthropicProvider = AnthropicProvider("sk-ant-test-key", httpClient)

        val messages = listOf(Message(role = "user", content = "Hello"))
        val options = ChatOptions(model = "claude-3-5-sonnet-20241022")

        val results = anthropicProvider.chatStream(messages, options).toList()

        // Should recover from errors and continue
        assertThat(results).isNotEmpty
    }

    @Test
    fun `test JetBrains AI Assistant typical usage pattern`() = runTest {
        // Test typical usage pattern from JetBrains AI Assistant
        val streamContent = """
            data: {"type":"message_start","message":{"model":"claude-3-5-sonnet-20241022","usage":{"input_tokens":25}}}

            data: {"type":"content_block_delta","delta":{"text":"Here's how to solve your problem:"}}

            data: {"type":"content_block_delta","delta":{"text":"\n\n1. First step"}}

            data: {"type":"message_stop"}

        """.trimIndent()

        mockEngine = MockEngine { 
            respond(
                content = ByteReadChannel(streamContent),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/event-stream")
            )
        }
        httpClient = HttpClient(mockEngine) {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 120_000
            }
        }
        anthropicProvider = AnthropicProvider("sk-ant-test-key", httpClient)

        val messages = listOf(
            Message(role = "system", content = "You are a helpful coding assistant"),
            Message(role = "user", content = "How do I fix this error?")
        )
        val options = ChatOptions(model = "claude-3-5-sonnet-20241022")

        val results = anthropicProvider.chatStream(messages, options).toList()

        // Should handle typical JetBrains AI Assistant usage
        assertThat(results).isNotEmpty
        val successfulResults = results.filter { it.isSuccess }
        assertThat(successfulResults).isNotEmpty
    }
}