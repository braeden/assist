package com.wisp.voice.wake

import android.content.Context
import android.util.Log
import com.rementia.openwakeword.lib.WakeWordEngine
import com.rementia.openwakeword.lib.model.WakeWordModel
import com.wisp.voice.AudioSessionArbiter
import com.wisp.voice.MicOwner
import com.wisp.voice.WakeConfig
import com.wisp.voice.WakeEvent
import com.wisp.voice.WakeWordDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive

/**
 * Phase-09 [WakeWordDetector] backed by openWakeWord via the
 * `xyz.rementia:openwakeword` Kotlin port (ONNX Runtime; the engine owns its
 * own 16 kHz `AudioRecord` loop). Fully on-device — no keys, no network. Model
 * files ship in `assets/` (see [WakeWords]; the library hardcodes the shared
 * `melspectrogram.onnx` / `embedding_model.onnx` preprocessing paths at the
 * assets root).
 *
 * Sensitivity → threshold: openWakeWord emits a 0..1 score per frame and fires
 * when `score > threshold` (recommended default threshold 0.5). Higher
 * [WakeConfig.sensitivity] must mean "easier to trigger", so we invert:
 * `threshold = (1 - sensitivity)` clamped to 0.05..0.95. The default
 * sensitivity 0.5 lands exactly on openWakeWord's recommended 0.5 threshold.
 *
 * Debounce: one utterance scores above threshold on several consecutive
 * frames, so detections within [DEBOUNCE_MS] of the last emit are dropped
 * ([DetectionDebouncer]; the engine's own `detectionCooldownMs` is set to the
 * same window, the debouncer additionally survives engine re-arms).
 *
 * Mic coordination: the detector listens while holding the shared
 * [AudioSessionArbiter] at [MicOwner.WAKE_WORD] — the lowest priority — so an
 * `ask()`/dictation (`LISTEN_ONCE`) or barge-in preempts it instantly. On
 * preemption it releases the engine, waits for the mic to free up, and
 * re-arms; detection is only ever paused, never lost.
 *
 * All openWakeWord library types stay inside `voice/wake` (behind the seam).
 */
class OpenWakeWordDetector(
    context: Context,
    private val arbiter: AudioSessionArbiter,
) : WakeWordDetector {
    private val appContext = context.applicationContext

    override suspend fun isAvailable(): Boolean =
        (preprocessingAssets + WakeWords.available().map { it.assetPath })
            .all { assetExists(it) }

    override fun detections(config: WakeConfig): Flow<WakeEvent> =
        channelFlow {
            val model = resolveModel(config)
            val debouncer = DetectionDebouncer(DEBOUNCE_MS)
            while (isActive) {
                try {
                    arbiter.withMic(MicOwner.WAKE_WORD) {
                        spotUntilCancelled(config, model, debouncer)
                    }
                } catch (cancel: CancellationException) {
                    // Collector gone -> propagate. Mic preempted (higher-priority
                    // owner) -> our scope is still active; re-arm shortly.
                    if (!currentCoroutineContext().isActive) throw cancel
                    Log.i(TAG, "mic preempted; re-arming wake word in ${REARM_DELAY_MS}ms")
                    delay(REARM_DELAY_MS)
                }
            }
        }

    /** Runs the engine until cancelled (preemption or collector cancellation). */
    private suspend fun ProducerScope<WakeEvent>.spotUntilCancelled(
        config: WakeConfig,
        model: WakeWordModel,
        debouncer: DetectionDebouncer,
    ) {
        val engine =
            WakeWordEngine(
                context = appContext,
                models = listOf(model),
                detectionCooldownMs = DEBOUNCE_MS,
            )
        try {
            engine.start()
            Log.i(TAG, "wake word armed (keyword=${config.keyword}, threshold=${model.threshold})")
            // Hot flow: never completes, so this suspends until cancellation
            // while delivering detections.
            engine.detections.collect { detection ->
                if (debouncer.shouldEmit(System.currentTimeMillis())) {
                    trySend(
                        WakeEvent(
                            keyword = config.keyword,
                            confidence = detection.score,
                            timestampMs = detection.timestamp,
                        ),
                    )
                }
            }
        } finally {
            runCatching { engine.release() }
        }
    }

    private fun resolveModel(config: WakeConfig): WakeWordModel {
        val threshold = thresholdFor(config.sensitivity)
        val assetPath =
            WakeWords.byName(config.keyword)?.assetPath
                ?: config.modelAsset
                ?: throw IllegalArgumentException(
                    "Unknown wake keyword '${config.keyword}' (not in WakeWords, no modelAsset)",
                )
        return WakeWordModel(name = config.keyword, modelPath = assetPath, threshold = threshold)
    }

    private fun assetExists(path: String): Boolean =
        runCatching { appContext.assets.open(path).use { } }.isSuccess

    companion object {
        private const val TAG = "OpenWakeWord"
        private const val REARM_DELAY_MS = 250L
        private const val DEBOUNCE_MS = 2_000L
        private const val THRESHOLD_MIN = 0.05f
        private const val THRESHOLD_MAX = 0.95f

        private val preprocessingAssets =
            listOf(WakeWords.MELSPECTROGRAM_ASSET, WakeWords.EMBEDDING_ASSET)

        /**
         * Maps seam sensitivity (0..1, higher = triggers more easily) to the
         * openWakeWord score threshold (higher = stricter): `1 - sensitivity`,
         * clamped to 0.05..0.95 so the engine is never impossible to trigger
         * nor firing on noise. Default sensitivity 0.5 -> threshold 0.5
         * (openWakeWord's recommended default).
         */
        internal fun thresholdFor(sensitivity: Float): Float =
            (1f - sensitivity).coerceIn(THRESHOLD_MIN, THRESHOLD_MAX)
    }
}
