package com.assist.service

/**
 * A serialized [ScreenState] plus the live `id -> NodeView` mapping that backs it.
 *
 * The controller keeps exactly one current frame; [node] resolves an element id to
 * its live node so gestures can act on it. When a newer frame is produced, the old
 * one must be [recycle]d to release native `AccessibilityNodeInfo` handles (leaking
 * them causes ANRs).
 */
class ScreenFrame(
    val state: ScreenState,
    private val nodes: Map<Int, NodeView>,
) {
    /** Live node for [id], or null if the id is stale/unknown. */
    fun node(id: Int): NodeView? = nodes[id]

    /** Recycle every retained node. Call once, when this frame is superseded. */
    fun recycle() {
        for (n in nodes.values) {
            runCatching { n.recycle() }
        }
    }

    companion object {
        val EMPTY = ScreenFrame(ScreenState.EMPTY, emptyMap())
    }
}
