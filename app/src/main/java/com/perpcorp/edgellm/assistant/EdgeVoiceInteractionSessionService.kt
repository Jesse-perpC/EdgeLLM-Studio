package com.perpcorp.edgellm.assistant

import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class EdgeVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: android.os.Bundle?): VoiceInteractionSession {
        return EdgeVoiceInteractionSession(this)
    }
}
