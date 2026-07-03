package com.wisp.voice.wake

/**
 * Pure, framework-free debounce guard for wake detections: a single spoken
 * utterance scores above threshold on several consecutive audio frames, so
 * everything within [windowMs] of the last accepted detection is dropped.
 * Not thread-safe — used from a single collector coroutine.
 */
internal class DetectionDebouncer(
    private val windowMs: Long,
) {
    private var lastEmitMs: Long? = null

    /** True (and records the emit) when [nowMs] is outside the debounce window. */
    fun shouldEmit(nowMs: Long): Boolean {
        val last = lastEmitMs
        if (last != null && nowMs - last < windowMs) return false
        lastEmitMs = nowMs
        return true
    }
}
