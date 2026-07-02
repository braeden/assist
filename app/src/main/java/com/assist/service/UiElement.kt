package com.assist.service

import kotlinx.serialization.Serializable

/**
 * One serialized accessibility node the agent can address by [id].
 *
 * [id] is a **stable per-frame integer** assigned during serialization; it maps
 * back to a live `AccessibilityNodeInfo` inside the owning [ScreenFrame] until the
 * next `serialize` call. The model clicks `id`s, not raw coordinates, whenever a
 * node exists (coordinate gestures are the fallback).
 */
@Serializable
data class UiElement(
    val id: Int,
    val role: String,
    val text: String? = null,
    val contentDesc: String? = null,
    val resourceId: String? = null,
    val bounds: Bounds,
    val clickable: Boolean = false,
    val longClickable: Boolean = false,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val checkable: Boolean = false,
    val checked: Boolean = false,
    val focused: Boolean = false,
    val password: Boolean = false,
    val enabled: Boolean = true,
)
