package com.wisp.service

/**
 * Direction the finger travels during a swipe. Note the content moves opposite to
 * the finger: [UP] (finger bottom→top) scrolls content downward, revealing what is
 * below the fold.
 */
enum class SwipeDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT,
    ;

    companion object {
        fun fromString(value: String): SwipeDirection? =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
    }
}
