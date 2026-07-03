package com.wisp.llm

/**
 * A system-prompt block. [cacheable] marks the stable prefix so the impl can put
 * `cache_control` on it (prompt caching for cheap repeated turns).
 */
data class SystemBlock(
    val text: String,
    val cacheable: Boolean = false,
)
