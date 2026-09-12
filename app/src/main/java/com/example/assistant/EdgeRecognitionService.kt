package com.example.assistant

import android.content.Intent
import android.speech.RecognitionService

class EdgeRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        // Recognition callback stub
    }

    override fun onCancel(listener: Callback?) {
        // Cancel listening
    }

    override fun onStopListening(listener: Callback?) {
        // Stop listening
    }
}
