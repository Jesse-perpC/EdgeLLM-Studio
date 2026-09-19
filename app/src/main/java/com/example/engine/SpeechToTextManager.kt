package com.example.engine

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Robust Speech-to-Text Manager wrapping Android's SpeechRecognizer API.
 * Provides real-time listening states, audio volume/RMS dB feedback,
 * partial hypothesis transcripts, and final transcribed results.
 */
class SpeechToTextManager(private val context: Context) {

    companion object {
        private const val TAG = "SpeechToTextManager"
    }

    sealed class SpeechState {
        object Idle : SpeechState()
        object Initializing : SpeechState()
        object Listening : SpeechState()
        data class Error(val errorCode: Int, val message: String) : SpeechState()
    }

    private var speechRecognizer: SpeechRecognizer? = null

    private val _speechState = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val speechState: StateFlow<SpeechState> = _speechState.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private var onFinalResultCallback: ((String) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening(
        prompt: String? = null,
        language: String = Locale.getDefault().toLanguageTag(),
        onError: ((String) -> Unit)? = null,
        onResult: (String) -> Unit
    ) {
        stopListening()

        this.onFinalResultCallback = onResult
        this.onErrorCallback = onError
        _partialText.value = ""
        _speechState.value = SpeechState.Initializing

        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                    setRecognitionListener(createRecognitionListener())
                }
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                if (!prompt.isNullOrBlank()) {
                    putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
                }
            }

            speechRecognizer?.startListening(intent)
            _isListening.value = true
            _speechState.value = SpeechState.Listening
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition: ${e.message}", e)
            _isListening.value = false
            val errorMsg = "Speech recognition unavailable: ${e.message}"
            _speechState.value = SpeechState.Error(-1, errorMsg)
            onErrorCallback?.invoke(errorMsg)
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognition", e)
        } finally {
            _isListening.value = false
            if (_speechState.value is SpeechState.Listening) {
                _speechState.value = SpeechState.Idle
            }
        }
    }

    fun cancel() {
        try {
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling speech recognition", e)
        } finally {
            _isListening.value = false
            _speechState.value = SpeechState.Idle
            _partialText.value = ""
        }
    }

    fun destroy() {
        cancel()
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying speech recognizer", e)
        }
        speechRecognizer = null
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _speechState.value = SpeechState.Listening
            _isListening.value = true
        }

        override fun onBeginningOfSpeech() {
            _isListening.value = true
        }

        override fun onRmsChanged(rmsdB: Float) {
            _rmsDb.value = rmsdB
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _isListening.value = false
        }

        override fun onError(error: Int) {
            _isListening.value = false
            val errorDesc = getErrorDescription(error)
            Log.w(TAG, "SpeechRecognizer error: $error ($errorDesc)")
            _speechState.value = SpeechState.Error(error, errorDesc)
            onErrorCallback?.invoke(errorDesc)
        }

        override fun onResults(results: Bundle?) {
            _isListening.value = false
            _speechState.value = SpeechState.Idle

            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val topResult = matches?.firstOrNull()?.trim() ?: ""

            if (topResult.isNotBlank()) {
                _partialText.value = topResult
                onFinalResultCallback?.invoke(topResult)
            } else {
                val errorMsg = "No speech detected"
                _speechState.value = SpeechState.Error(SpeechRecognizer.ERROR_NO_MATCH, errorMsg)
                onErrorCallback?.invoke(errorMsg)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull()?.trim() ?: ""
            if (partial.isNotBlank()) {
                _partialText.value = partial
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun getErrorDescription(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Audio record permission not granted"
            SpeechRecognizer.ERROR_NETWORK -> "Network communication error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout while recognizing speech"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech matches recognized. Please try speaking again."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy"
            SpeechRecognizer.ERROR_SERVER -> "Speech server returned an error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input detected within timeout"
            else -> "Speech recognition error ($errorCode)"
        }
    }
}
