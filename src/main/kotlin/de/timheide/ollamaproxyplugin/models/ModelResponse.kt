package de.timheide.ollamaproxyplugin.models

data class ModelDetails(
    val parent_model: String = "",
    val format: String = "gguf",
    val family: String = "claude",
    val families: List<String> = listOf("claude"),
    val parameter_size: String = "175B",
    val quantization_level: String = "Q4_K_M"
)

data class Model(
    val name: String,
    val model: String? = null,
    val modified_at: String? = null,
    val size: Long? = null,
    val digest: String? = null,
    val details: ModelDetails? = null
)