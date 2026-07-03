package com.wisp.voice.wake

/**
 * Registry of the wake-word models we ship (openWakeWord pretrained models,
 * ONNX, at the `assets/` root — the openwakeword-android-kt library hardcodes
 * the shared preprocessing model paths as `melspectrogram.onnx` /
 * `embedding_model.onnx` relative to the assets root, so all three files live
 * there). openWakeWord's pretrained models are CC BY-NC-SA 4.0 — fine for this
 * personal sideload app, not for commercial redistribution. A custom
 * "Hey Wisp" model is a later training exercise.
 */
data class WakeWordModelInfo(
    /** Stable name persisted in settings ([com.wisp.data.SettingsStore.wakeKeyword]). */
    val name: String,
    /** Display label for the Settings picker. */
    val label: String,
    /** Wake model path relative to `assets/`. */
    val assetPath: String,
)

object WakeWords {
    /**
     * Shared openWakeWord preprocessing models (all wake models need both).
     * Paths are fixed by the library — it opens exactly these asset names.
     */
    const val MELSPECTROGRAM_ASSET = "melspectrogram.onnx"
    const val EMBEDDING_ASSET = "embedding_model.onnx"

    val ALEXA =
        WakeWordModelInfo(
            name = "alexa",
            label = "Alexa",
            assetPath = "alexa_v0.1.onnx",
        )

    /** Models available in the Settings picker. */
    fun available(): List<WakeWordModelInfo> = listOf(ALEXA)

    fun byName(name: String): WakeWordModelInfo? =
        available().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
}
