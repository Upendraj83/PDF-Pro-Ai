package com.example.pdf

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class PdfTtsManager(context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _speechRate = MutableStateFlow(1.0f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private var currentText: String = ""
    private var onFinishedCallback: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate(_speechRate.value)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isPlaying.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isPlaying.value = false
                        onFinishedCallback?.invoke()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isPlaying.value = false
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _isPlaying.value = false
                    }
                })
            }
        }
    }

    fun speak(text: String, onFinished: (() -> Unit)? = null) {
        if (text.isBlank()) return
        currentText = text
        onFinishedCallback = onFinished

        if (isInitialized) {
            tts?.setSpeechRate(_speechRate.value)
            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "pdf_page_tts")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "pdf_page_tts")
            _isPlaying.value = true
        }
    }

    fun pause() {
        tts?.stop()
        _isPlaying.value = false
    }

    fun stop() {
        tts?.stop()
        _isPlaying.value = false
        currentText = ""
        onFinishedCallback = null
    }

    fun resume() {
        if (currentText.isNotBlank()) {
            speak(currentText, onFinishedCallback)
        }
    }

    fun setSpeed(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 2.5f)
        _speechRate.value = clamped
        tts?.setSpeechRate(clamped)
        if (_isPlaying.value && currentText.isNotBlank()) {
            speak(currentText, onFinishedCallback)
        }
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
