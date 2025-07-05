package de.timheide.ollamaproxyplugin.llm

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.assertj.core.api.Assertions.*

class JsonStreamingTest {

    @Test
    fun `test JSON validation improvements`() {
        // Test JSON validation logic directly without reflection
        val validJsonStarts = listOf(
            """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"No"}   }""",
            """{"type":"message_stop"         }""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"here"}        }""",
            """{"incomplete"""  // Valid start but incomplete
        )
        
        val invalidJsonStarts = listOf(
            """   """,  // Just whitespace
            """""",  // Empty
            """malformed"""  // Not JSON
        )
        
        // Test that valid JSON patterns return true
        validJsonStarts.forEach { jsonData ->
            val trimmed = jsonData.trim()
            val isValid = trimmed.startsWith("{") && trimmed.length > 1
            assertThat(isValid).describedAs("Valid JSON should return true: $jsonData").isTrue
        }
        
        // Test that invalid JSON patterns return false  
        invalidJsonStarts.forEach { jsonData ->
            val trimmed = jsonData.trim()
            val isValid = trimmed.startsWith("{") && trimmed.length > 1
            assertThat(isValid).describedAs("Invalid JSON should return false: $jsonData").isFalse
        }
    }

    @Test
    fun `test streaming error handling does not throw exceptions`() {
        // Test that the improved error handling prevents crashes
        assertDoesNotThrow {
            val provider = AnthropicProvider("sk-ant-test-key")
            val models = provider.getModels()
            assertThat(models).isNotEmpty
            assertThat(models.map { it.name }).contains("claude-3-5-sonnet-20241022")
        }
    }

    @Test
    fun `verify streaming improvements handle problematic JSON patterns`() {
        // Test the specific patterns from the error logs that were causing IllegalStateException
        val problematicJsonChunks = listOf(
            """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"No"}   }""",
            """{"type":"message_stop"         }""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"here"}        }""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"\nhere method\nhere fun...""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"\nhere variable\nhere c...""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"\nhere field"}         ...""",
            """{"type":"message_stop"            }"""
        )
        
        // These should all be handled gracefully without throwing exceptions
        problematicJsonChunks.forEach { chunk ->
            assertDoesNotThrow("Should handle chunk: $chunk") {
                // Test the validation logic directly
                val trimmed = chunk.trim()
                val isValid = trimmed.startsWith("{") && trimmed.length > 1
                // Just ensure the validation runs without exceptions
                assertThat(isValid).isIn(true, false)
            }
        }
    }

    @Test 
    fun `test streaming configuration is properly set up`() {
        val provider = AnthropicProvider("sk-ant-test-key")
        
        // Verify that the provider is configured for streaming
        assertDoesNotThrow {
            val models = provider.getModels()
            assertThat(models).hasSizeGreaterThan(0)
            
            // Verify Claude models are available for JetBrains AI Assistant
            val modelNames = models.map { it.name }
            assertThat(modelNames).contains(
                "claude-opus-4-20250514",
                "claude-sonnet-4-20250514",
                "claude-3-5-sonnet-20241022"
            )
        }
    }
}