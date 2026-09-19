package com.example.assistant

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * System-wide RecognitionService implementation for EdgeLLM Voice Assistant.
 * Declared in AndroidManifest with BIND_RECOGNITION_SERVICE and registered
 * in res/xml/voice_interaction_service.xml.
 *
 * It provides standard Android Speech-to-Text capabilities to the Android OS,
 * delegating speech audio capture and dispatching recognized text results
 * back to the invoking client or voice session.
 */
class EdgeRecognitionService : RecognitionService() {

    companion object {
        private const val TAG = "EdgeRecognitionService"
    }

    private var delegateRecognizer: SpeechRecognizer? = null
    private var currentCallback: Callback? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "EdgeRecognitionService created")
    }

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        Log.d(TAG, "onStartListening called with intent: $recognizerIntent")
        currentCallback = listener

        try {
            listener?.readyForSpeech(Bundle())

            // Clean up previous recognizer if active
            delegateRecognizer?.cancel()
            delegateRecognizer?.destroy()

            delegateRecognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        try {
                            listener?.readyForSpeech(params ?: Bundle())
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in readyForSpeech callback", e)
                        }
                    }

                    override fun onBeginningOfSpeech() {
                        try {
                            listener?.beginningOfSpeech()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in beginningOfSpeech callback", e)
                        }
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        try {
                            listener?.rmsChanged(rmsdB)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in rmsChanged callback", e)
                        }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {
                        try {
                            if (buffer != null) {
                                listener?.bufferReceived(buffer)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in bufferReceived callback", e)
                        }
                    }

                    override fun onEndOfSpeech() {
                        try {
                            listener?.endOfSpeech()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in endOfSpeech callback", e)
                        }
                    }

                    override fun onError(error: Int) {
                        Log.w(TAG, "SpeechRecognizer error: $error")
                        try {
                            listener?.error(error)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in error callback", e)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        Log.d(TAG, "SpeechRecognizer onResults received")
                        try {
                            listener?.results(results ?: Bundle())
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in results callback", e)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        try {
                            listener?.partialResults(partialResults ?: Bundle())
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in partialResults callback", e)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {
                        // Reserved for custom recognizer events
                    }
                })
            }

            val targetIntent = recognizerIntent ?: Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }

            delegateRecognizer?.startListening(targetIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed starting recognition session", e)
            try {
                listener?.error(SpeechRecognizer.ERROR_CLIENT)
            } catch (ce: Exception) {
                Log.e(TAG, "Failed calling listener.error", ce)
            }
        }
    }

    override fun onStopListening(listener: Callback?) {
        Log.d(TAG, "onStopListening")
        try {
            delegateRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping listening", e)
        }
    }

    override fun onCancel(listener: Callback?) {
        Log.d(TAG, "onCancel")
        try {
            delegateRecognizer?.cancel()
            delegateRecognizer?.destroy()
            delegateRecognizer = null
            currentCallback = null
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling listening", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            delegateRecognizer?.cancel()
            delegateRecognizer?.destroy()
            delegateRecognizer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
    }
}
