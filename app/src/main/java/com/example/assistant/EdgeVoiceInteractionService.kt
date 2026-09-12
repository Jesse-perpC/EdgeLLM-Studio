package com.example.assistant

import android.service.voice.VoiceInteractionService

class EdgeVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        // Service initialized and ready for system assistant callbacks
    }

    override fun onShutdown() {
        super.onShutdown()
    }
}
