package de.timheide.ollamaproxyplugin.llm

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class AnthropicProviderSimpleTest {

    @Test
    fun `test API key validation`() {
        // Test the core functionality that was improved
        assertDoesNotThrow {
            val provider = AnthropicProvider("sk-ant-test-key")
            val models = provider.getModels()
            assert(models.isNotEmpty())
        }
    }

    @Test
    fun `test JSON validation helper`() {
        // Test the new helper function for JSON validation
        val provider = AnthropicProvider("sk-ant-test-key")
        val validJson = """{"type":"message_start","message":{}}"""
        val invalidJson = "{"
        val emptyJson = ""
        
        // Use reflection to test the private helper method
        val method = AnthropicProvider::class.java.getDeclaredMethod("isValidJsonStart", String::class.java)
        method.isAccessible = true
        
        val validResult = method.invoke(provider, validJson) as Boolean
        val invalidResult = method.invoke(provider, invalidJson) as Boolean
        val emptyResult = method.invoke(provider, emptyJson) as Boolean
        
        assert(validResult)
        assert(!invalidResult)
        assert(!emptyResult)
    }
}