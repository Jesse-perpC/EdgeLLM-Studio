package com.perpcorp.edgellm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    companion object {
        init {
            try {
                System.loadLibrary("edgellm")
            } catch (e: UnsatisfiedLinkError) {
                // Ignore if running without native lib
            }
        }
    }

    external fun stringFromJNI(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val engineStatus = try {
            stringFromJNI()
        } catch (e: Throwable) {
            "EdgeLLM Studio Native Engine"
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = engineStatus)
                    }
                }
            }
        }
    }
}
