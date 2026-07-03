package com.wisp.voice.android

import com.wisp.voice.SttEngine
import com.wisp.voice.TtsEngine
import com.wisp.voice.VoiceProvider
import com.wisp.voice.VoiceProviderKind
import com.wisp.voice.WakeWordDetector

/**
 * The v1 `android` [VoiceProvider]: `SpeechRecognizer` STT + `TextToSpeech` TTS,
 * a `PIPELINE` backend. Free, offline, ships first. Wake word is deferred to
 * phase-09 (a standalone [WakeWordDetector]), so [wakeWord] returns `null`.
 */
class AndroidVoiceProvider(
    private val stt: SttEngine,
    private val tts: TtsEngine,
) : VoiceProvider {
    override val id: String = "android"
    override val kind: VoiceProviderKind = VoiceProviderKind.PIPELINE

    override fun stt(): SttEngine = stt

    override fun tts(): TtsEngine = tts

    override fun wakeWord(): WakeWordDetector? = null
}
