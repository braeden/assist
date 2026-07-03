package com.wisp.voice.wake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Framework-free tests for the pure pieces of the openWakeWord detector. */
class OpenWakeWordDetectorTest {
    // --- sensitivity -> threshold mapping ---

    @Test
    fun `default sensitivity maps to the recommended threshold`() {
        assertEquals(0.5f, OpenWakeWordDetector.thresholdFor(0.5f), 0.0001f)
    }

    @Test
    fun `higher sensitivity lowers the threshold`() {
        assertEquals(0.2f, OpenWakeWordDetector.thresholdFor(0.8f), 0.0001f)
        assertTrue(
            OpenWakeWordDetector.thresholdFor(0.9f) < OpenWakeWordDetector.thresholdFor(0.1f),
        )
    }

    @Test
    fun `threshold is clamped away from the extremes`() {
        assertEquals(0.05f, OpenWakeWordDetector.thresholdFor(1f), 0.0001f)
        assertEquals(0.95f, OpenWakeWordDetector.thresholdFor(0f), 0.0001f)
        assertEquals(0.95f, OpenWakeWordDetector.thresholdFor(-1f), 0.0001f)
        assertEquals(0.05f, OpenWakeWordDetector.thresholdFor(2f), 0.0001f)
    }

    // --- WakeWords registry lookup ---

    @Test
    fun `byName resolves the alexa model`() {
        assertEquals(WakeWords.ALEXA, WakeWords.byName("alexa"))
    }

    @Test
    fun `byName is case-insensitive and trims`() {
        assertEquals(WakeWords.ALEXA, WakeWords.byName("Alexa"))
        assertEquals(WakeWords.ALEXA, WakeWords.byName("ALEXA"))
        assertEquals(WakeWords.ALEXA, WakeWords.byName("  alexa "))
    }

    @Test
    fun `byName returns null for unknown keywords`() {
        assertNull(WakeWords.byName("hey wisp"))
        assertNull(WakeWords.byName(""))
    }

    @Test
    fun `registered assets are root-relative onnx files`() {
        // The library hardcodes the preprocessing models at the assets root;
        // keep every registered path root-relative alongside them.
        val shipped =
            WakeWords.available().map { it.assetPath } +
                listOf(WakeWords.MELSPECTROGRAM_ASSET, WakeWords.EMBEDDING_ASSET)
        shipped
            .forEach { path ->
                assertFalse("$path should be root-relative", path.contains('/'))
                assertTrue("$path should be .onnx", path.endsWith(".onnx"))
            }
    }

    // --- detection debounce ---

    @Test
    fun `first detection always emits`() {
        val debouncer = DetectionDebouncer(windowMs = 2_000L)
        assertTrue(debouncer.shouldEmit(nowMs = 10_000L))
    }

    @Test
    fun `detections inside the window are dropped`() {
        val debouncer = DetectionDebouncer(windowMs = 2_000L)
        assertTrue(debouncer.shouldEmit(nowMs = 10_000L))
        assertFalse(debouncer.shouldEmit(nowMs = 10_500L))
        assertFalse(debouncer.shouldEmit(nowMs = 11_999L))
    }

    @Test
    fun `detection after the window emits and re-arms the window`() {
        val debouncer = DetectionDebouncer(windowMs = 2_000L)
        assertTrue(debouncer.shouldEmit(nowMs = 10_000L))
        assertTrue(debouncer.shouldEmit(nowMs = 12_000L))
        // Window restarts from the last accepted emit, not the first.
        assertFalse(debouncer.shouldEmit(nowMs = 13_000L))
        assertTrue(debouncer.shouldEmit(nowMs = 14_000L))
    }

    @Test
    fun `dropped detections do not extend the window`() {
        val debouncer = DetectionDebouncer(windowMs = 2_000L)
        assertTrue(debouncer.shouldEmit(nowMs = 10_000L))
        assertFalse(debouncer.shouldEmit(nowMs = 11_900L))
        assertTrue(debouncer.shouldEmit(nowMs = 12_000L))
    }
}
