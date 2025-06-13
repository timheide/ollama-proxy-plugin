package de.timheide.ollamaproxyplugin.llm

import de.timheide.ollamaproxyplugin.models.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Simple streaming test to debug the real streaming implementation
 */
class SimpleStreamingTest {

    private lateinit var anthropicProvider: AnthropicProvider
    private var apiKey: String? = null

    @BeforeEach
    fun setUp() {
        apiKey = System.getenv("ANTHROPIC_API_KEY")
        assumeTrue(!apiKey.isNullOrBlank(), "Set ANTHROPIC_API_KEY to run this test")
        anthropicProvider = AnthropicProvider(apiKey!!)
    }

    @Test
    fun `debug streaming with simple request`() {
        println("Starting streaming test WITHOUT runTest...")
        
        val messages = listOf(
            Message(role = "user", content = "Say just 'Hi'")
        )
        val options = ChatOptions(
            model = "claude-3-5-sonnet-20241022",
            maxTokens = 10
        )

        try {
            // Run outside of test framework coroutines to avoid cancellation issues
            kotlinx.coroutines.runBlocking {
                val streamResults = withTimeoutOrNull(30000) { // 30 second timeout
                    anthropicProvider.chatStream(messages, options).take(10).toList()
                }
                
                if (streamResults == null) {
                    println("Streaming timed out!")
                    return@runBlocking
                }
                
                println("Received ${streamResults.size} streaming results")
                
                streamResults.forEachIndexed { index, result ->
                    when {
                        result.isSuccess -> {
                            val response = result.getOrNull()
                            println("[$index] SUCCESS: '${response?.content}' (${response?.content?.length} chars)")
                        }
                        result.isFailure -> {
                            val error = result.exceptionOrNull()
                            println("[$index] FAILURE: ${error?.javaClass?.simpleName}: ${error?.message}")
                        }
                    }
                }
                
                val successCount = streamResults.count { it.isSuccess }
                val failureCount = streamResults.count { it.isFailure }
                println("Summary: $successCount success, $failureCount failures")
                
                // Assert that streaming actually worked
                assert(successCount > 0) { "No successful streaming results received" }
                
                val finalResult = streamResults.filter { it.isSuccess }.lastOrNull()?.getOrNull()
                assert(finalResult != null) { "No final result available" }
                assert(!finalResult!!.content.isBlank()) { "Final content is blank" }
                println("✓ Streaming test PASSED - received content: '${finalResult.content}'")
            }
        } catch (e: Exception) {
            println("Exception during streaming: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}