package de.timheide.ollamaproxyplugin.llm

import de.timheide.ollamaproxyplugin.models.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.serialization.kotlinx.json.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import org.assertj.core.api.Assertions.*
import kotlinx.coroutines.flow.toList

class AnthropicProviderTest {

    private lateinit var mockEngine: MockEngine
    private lateinit var httpClient: HttpClient
    private lateinit var anthropicProvider: AnthropicProvider

    @BeforeEach
    fun setUp() {
        mockEngine = MockEngine { requestData ->
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
            install(io.ktor.client.plugins.HttpTimeout) {
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 120_000
            }
        }
        anthropicProvider = AnthropicProvider("sk-ant-test-key", httpClient)
    }

    @Test
    fun `test successful chat request`() = runTest {
        val messages = listOf(
            Message(role = "user", content = "Hello")
        )
        val options = ChatOptions(model = "claude-3-5-sonnet-20241022")

        val result = anthropicProvider.chat(messages, options)

        assertThat(result.isSuccess).isTrue
        val response = result.getOrNull()
        assertThat(response).isNotNull
        assertThat(response!!.content).isEqualTo("Test response")
        assertThat(response.promptTokens).isEqualTo(10)
        assertThat(response.completionTokens).isEqualTo(5)
        assertThat(response.model).isEqualTo("claude-3-5-sonnet-20241022")
    }

    @Test
    fun `test invalid API key format`() = runTest {
        val invalidProvider = AnthropicProvider("invalid-key", httpClient)
        val messages = listOf(Message(role = "user", content = "Hello"))
        val options = ChatOptions(model = "claude-3-5-sonnet-20241022")

        val result = invalidProvider.chat(messages, options)

        assertThat(result.isFailure).isTrue
        val exception = result.exceptionOrNull()
        println("Exception type: ${exception?.javaClass?.simpleName}")
        println("Exception message: ${exception?.message}")
        assertThat(exception).isInstanceOf(LLMError.ApiError::class.java)
    }

    @Test
    fun `test empty API key`() = runTest {
        val emptyProvider = AnthropicProvider("", httpClient)
        val messages = listOf(Message(role = "user", content = "Hello"))
        val options = ChatOptions(model = "claude-3-5-sonnet-20241022")

        val result = emptyProvider.chat(messages, options)

        assertThat(result.isFailure).isTrue
        val exception = result.exceptionOrNull()
        assertThat(exception).isInstanceOf(Exception::class.java)
    }

    @Test
    fun `test unauthorized response`() = runTest {
        mockEngine = MockEngine { 
            respond(
                content = "Unauthorized",
                status = HttpStatusCode.Unauthorized
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

        val result = anthropicProvider.chat(messages, options)

        assertThat(result.isFailure).isTrue
        val exception = result.exceptionOrNull()
        assertThat(exception).isInstanceOf(Exception::class.java)
    }

    @Test
    fun `test rate limit response`() = runTest {
        mockEngine = MockEngine { 
            respond(
                content = "Rate limit exceeded",
                status = HttpStatusCode.TooManyRequests
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

        val result = anthropicProvider.chat(messages, options)

        assertThat(result.isFailure).isTrue
        val exception = result.exceptionOrNull()
        assertThat(exception).isInstanceOf(Exception::class.java)
    }

    @Test
    fun `test malformed JSON response`() = runTest {
        mockEngine = MockEngine { 
            respond(
                content = "invalid json",
                status = HttpStatusCode.OK
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

        val result = anthropicProvider.chat(messages, options)

        assertThat(result.isFailure).isTrue
        val exception = result.exceptionOrNull()
        assertThat(exception).isInstanceOf(Exception::class.java)
    }

    @Test
    fun `test streaming chat with complete JSON`() = runTest {
        val streamContent = """
            data: {"type":"message_start","message":{"model":"claude-3-5-sonnet-20241022","usage":{"input_tokens":10}}}

            data: {"type":"content_block_delta","delta":{"text":"Hello"}}

            data: {"type":"content_block_delta","delta":{"text":" World"}}

            data: {"type":"message_delta","usage":{"output_tokens":5}}

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

        assertThat(results).isNotEmpty
        // Should have multiple successful results as content builds up
        assertThat(results.all { it.isSuccess }).isTrue
        
        val finalResponse = results.last().getOrNull()
        assertThat(finalResponse).isNotNull
        assertThat(finalResponse!!.content).isEqualTo("Hello World")
        assertThat(finalResponse.promptTokens).isEqualTo(10)
        assertThat(finalResponse.completionTokens).isEqualTo(5)
    }

    @Test
    fun `test streaming with malformed JSON handling`() = runTest {
        val streamContent = """
            data: {"type":"message_start","message":{"model":"claude-3-5-sonnet-20241022","usage":{"input_tokens":10}}}

            data: {"type":"content_block_delta","delta":{"text":"Test"}

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

        // Should handle malformed JSON gracefully and still complete
        assertThat(results).isNotEmpty
        val successfulResults = results.filter { it.isSuccess }
        assertThat(successfulResults).isNotEmpty
    }

    @Test
    fun `test get models returns expected models`() {
        val models = anthropicProvider.getModels()

        assertThat(models).isNotEmpty
        assertThat(models).extracting("name").contains(
            "claude-opus-4-20250514",
            "claude-sonnet-4-20250514", 
            "claude-3-7-sonnet-20250219",
            "claude-3-5-sonnet-20241022",
            "claude-3-5-sonnet-20240620",
            "claude-3-5-haiku-20241022"
        )
    }

    @Test
    fun `test get model details for valid model`() = runTest {
        val result = anthropicProvider.getModelDetails("claude-3-5-sonnet-20241022")

        assertThat(result.isSuccess).isTrue
        val details = result.getOrNull()
        assertThat(details).isNotNull
        assertThat(details!!["license"]?.toString()).contains("Anthropic")
        assertThat(details["system"]?.toString()).isNotEmpty
    }

    @Test
    fun `test get model details for invalid model`() = runTest {
        val result = anthropicProvider.getModelDetails("invalid-model")

        assertThat(result.isFailure).isTrue
        val exception = result.exceptionOrNull()
        assertThat(exception).isNotNull
    }

    @Test
    fun `test system message handling`() = runTest {
        val messages = listOf(
            Message(role = "system", content = "You are a helpful assistant"),
            Message(role = "user", content = "Hello")
        )
        val options = ChatOptions(model = "claude-3-5-sonnet-20241022")

        val result = anthropicProvider.chat(messages, options)

        assertThat(result.isSuccess).isTrue
        
        // Verify the request was made - system messages should be handled properly
        val request = mockEngine.requestHistory.first()
        assertThat(request.url.toString()).contains("api.anthropic.com")
        assertThat(request.headers["x-api-key"]).isEqualTo("sk-ant-test-key")
    }

    @Test
    fun `test chat options are properly applied`() = runTest {
        val messages = listOf(Message(role = "user", content = "Hello"))
        val options = ChatOptions(
            model = "claude-3-5-sonnet-20241022",
            temperature = 0.7f,
            topP = 0.9f,
            maxTokens = 1000
        )

        val result = anthropicProvider.chat(messages, options)

        assertThat(result.isSuccess).isTrue
        
        // Verify request was made with proper model
        val request = mockEngine.requestHistory.first()
        assertThat(request.url.toString()).contains("api.anthropic.com")
        assertThat(request.headers["anthropic-version"]).isEqualTo("2023-06-01")
    }
}