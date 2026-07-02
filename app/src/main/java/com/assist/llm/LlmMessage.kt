package com.assist.llm

/** Conversation role for a turn sent to / received from the model. */
enum class Role { USER, ASSISTANT }

/**
 * One conversation turn. `tool_result` blocks live inside a [Role.USER] message's
 * [content] (Anthropic convention); assistant `tool_use` blocks live inside a
 * [Role.ASSISTANT] message. Phase-05 reconstructs `List<LlmMessage>` from the DB.
 */
data class LlmMessage(
    val role: Role,
    val content: List<ContentBlock>,
)
