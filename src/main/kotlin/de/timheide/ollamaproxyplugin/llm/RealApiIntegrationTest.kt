package de.timheide.ollamaproxyplugin.llm

import de.timheide.ollamaproxyplugin.models.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.assertj.core.api.Assertions.*

/**
 * Integration tests that use the real Anthropic API.
 * 
 * To run these tests, set the environment variable:
 * ANTHROPIC_API_KEY=your-api-key-here
 * 
 * Tests will be skipped if no API key is provided.
 * These tests make actual API calls and may incur costs.
 */
class RealApiIntegrationTest {

    private lateinit var anthropicProvider: AnthropicProvider
    private var apiKey: String? = null

    @BeforeEach
    fun setUp() {
        // Get API key from environment variable
        apiKey = System.getenv("ANTHROPIC_API_KEY")
        
        // Skip tests if no API key is provided
        assumeTrue(
            !apiKey.isNullOrBlank(),
            "Skipping real API tests - set ANTHROPIC_API_KEY environment variable to run these tests"
        )
        
        anthropicProvider = AnthropicProvider(apiKey!!)
    }

    @Test
    fun `test real streaming response from Anthropic API`() = runTest {
        val messages = listOf(
            Message(role = "user", content = "Say 'Hello World' and nothing else")
        )
        val options = ChatOptions(
            model = "claude-3-5-sonnet-20241022", // Use streaming-compatible model
            maxTokens = 20  // Limit tokens to minimize cost
        )

        val streamResults = anthropicProvider.chatStream(messages, options).toList()

        // Verify streaming actually works with real API
        println("Total streaming responses received: ${streamResults.size}")
        
        // Check for any failures and print them
        val failedResults = streamResults.filter { it.isFailure }
        if (failedResults.isNotEmpty()) {
            println("Failed results: ${failedResults.size}")
            failedResults.forEach { 
                println("Failure: ${it.exceptionOrNull()?.message}")
                it.exceptionOrNull()?.printStackTrace()
            }
        }
        
        // Verify all results are successful (no JSON parsing errors)
        val successfulResults = streamResults.filter { it.isSuccess }
        println("Successful streaming responses: ${successfulResults.size}")
        
        // At minimum, we should get some successful results
        assertThat(successfulResults).describedAs("Should have at least one successful streaming result").isNotEmpty
        
        // Verify content was accumulated correctly
        val finalResult = successfulResults.last().getOrNull()
        assertThat(finalResult).isNotNull
        assertThat(finalResult!!.content).isNotBlank
        assertThat(finalResult.content.lowercase()).contains("hello")
        
        println("Final streaming content: '${finalResult.content}'")
        println("Prompt tokens: ${finalResult.promptTokens}")
        println("Completion tokens: ${finalResult.completionTokens}")
        println("Model: ${finalResult.model}")
        
        // Verify tokens were counted
        assertThat(finalResult.promptTokens).isGreaterThan(0)
        assertThat(finalResult.completionTokens).isGreaterThan(0)
    }

    @Test
    fun `test real non-streaming response from Anthropic API`() = runTest {
        val messages = listOf(
            Message(role = "user", content = "Say 'Test' and nothing else")
        )
        val options = ChatOptions(
            model = "claude-3-5-haiku-20241022", // Use fastest/cheapest model for non-streaming
            maxTokens = 10  // Limit tokens to minimize cost
        )

        val result = anthropicProvider.chat(messages, options)

        assertThat(result.isSuccess).isTrue
        val response = result.getOrNull()
        assertThat(response).isNotNull
        assertThat(response!!.content).isNotBlank
        assertThat(response.content.lowercase()).contains("test")
        
        println("Non-streaming response: '${response.content}'")
        println("Prompt tokens: ${response.promptTokens}")
        println("Completion tokens: ${response.completionTokens}")
        
        // Verify token counting works
        assertThat(response.promptTokens).isGreaterThan(0)
        assertThat(response.completionTokens).isGreaterThan(0)
    }

    @Test
    fun `test streaming handles system messages correctly`() = runTest {
        val messages = listOf(
            Message(role = "system", content = "You are a helpful assistant. Always respond with exactly 'Hello from Claude'"),
            Message(role = "user", content = "Please respond")
        )
        val options = ChatOptions(
            model = "claude-3-5-sonnet-20241022",
            maxTokens = 15
        )

        val streamResults = anthropicProvider.chatStream(messages, options).toList()

        assertThat(streamResults).isNotEmpty
        val successfulResults = streamResults.filter { it.isSuccess }
        assertThat(successfulResults).isNotEmpty
        
        val finalResult = successfulResults.last().getOrNull()
        assertThat(finalResult).isNotNull
        assertThat(finalResult!!.content).containsIgnoringCase("hello")
        
        println("System message test result: '${finalResult.content}'")
    }

    @Test
    fun `test API error handling with invalid model`() = runTest {
        val messages = listOf(
            Message(role = "user", content = "Test")
        )
        val options = ChatOptions(
            model = "invalid-model-name",
            maxTokens = 10
        )

        val result = anthropicProvider.chat(messages, options)

        // Should handle API error gracefully
        assertThat(result.isFailure).isTrue
        val exception = result.exceptionOrNull()
        assertThat(exception).isInstanceOf(LLMError.ApiError::class.java)
        
        println("Expected API error handled: ${exception?.message}")
    }

    @Test
    fun `test streaming with longer content for robustness`() = runTest {
        val messages = listOf(
            Message(role = "user", content = "Count from 1 to 5, each number on a new line")
        )
        val options = ChatOptions(
            model = "claude-3-5-sonnet-20241022",
            maxTokens = 50
        )

        val streamResults = anthropicProvider.chatStream(messages, options).toList()

        assertThat(streamResults).isNotEmpty
        val successfulResults = streamResults.filter { it.isSuccess }
        assertThat(successfulResults).isNotEmpty
        
        // Verify content builds up correctly over multiple chunks
        var previousLength = 0
        successfulResults.forEach { result ->
            val response = result.getOrNull()
            if (response != null) {
                assertThat(response.content.length).isGreaterThanOrEqualTo(previousLength)
                previousLength = response.content.length
            }
        }
        
        val finalResult = successfulResults.last().getOrNull()
        assertThat(finalResult).isNotNull
        assertThat(finalResult!!.content).contains("1")
        assertThat(finalResult.content).contains("5")
        
        println("Multi-chunk streaming result length: ${finalResult.content.length}")
        println("Content preview: ${finalResult.content.take(100)}...")
    }

    @Test
    fun `test real model list retrieval`() {
        // This doesn't make an API call, but tests the actual model configuration
        val models = anthropicProvider.getModels()
        
        assertThat(models).isNotEmpty
        assertThat(models).hasSizeGreaterThan(5)
        
        val modelNames = models.map { it.name }
        assertThat(modelNames).contains("claude-3-5-haiku-20241022")
        
        println("Available models: ${modelNames.joinToString(", ")}")
        
        // Test model details for a real model
        runTest {
            val detailsResult = anthropicProvider.getModelDetails("claude-3-5-sonnet-20241022")
            assertThat(detailsResult.isSuccess).isTrue
            
            val details = detailsResult.getOrNull()
            assertThat(details).isNotNull
            assertThat(details!!["system"]?.toString()).isNotBlank
            
            println("Model details retrieved successfully")
        }
    }
}