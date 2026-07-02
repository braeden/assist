package com.assist.llm

/**
 * Token usage for one turn. Cache counts let phase-05 verify prompt caching is
 * working (a repeated identical prefix should report non-zero [cacheReadTokens])
 * and price turns accurately.
 */
data class Usage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cacheReadTokens: Int = 0,
    val cacheWriteTokens: Int = 0,
)
