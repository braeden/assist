package com.assist.llm

/**
 * A tool advertised to the model. [inputSchemaJson] is a JSON Schema object as a
 * string, kept opaque here so this layer stays serialization-agnostic. The agent
 * (phase-06) owns the concrete tool catalog; the impl marks the last tool def
 * cacheable alongside the system prompt.
 */
data class ToolDef(
    val name: String,
    val description: String,
    val inputSchemaJson: String,
)
