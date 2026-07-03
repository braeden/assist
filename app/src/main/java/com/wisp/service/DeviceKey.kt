package com.wisp.service

/**
 * Global navigation keys the agent can press. Most map to
 * `AccessibilityService.performGlobalAction`; [ENTER] targets the focused editable
 * node via `ACTION_IME_ENTER`.
 */
enum class DeviceKey {
    BACK,
    HOME,
    RECENTS,
    NOTIFICATIONS,
    QUICK_SETTINGS,
    ENTER,
    ;

    companion object {
        fun fromString(value: String): DeviceKey? =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
    }
}
