package com.wisp.llm

/**
 * A parsed tool invocation from an assistant turn. [argumentsJson] is the raw
 * JSON arguments object; the agent (phase-06) decodes it per tool schema and
 * executes it — the LLM layer never auto-executes.
 */
data class ToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)
