package com.peyo.rtlmex

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import kotlinx.coroutines.flow.Flow

object LmManager {
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    var isInitialized = false
        private set
    var activeBackend = "None"
        private set

    fun sendMessageAsync(prompt: String): Flow<Message>? = conversation?.sendMessageAsync(prompt)

    fun initialize(context: Context) {
        if (engine != null) return // Already initialized

        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        Log.i("LmManager", "Initializing engine with nativeLibDir=$nativeLibDir")

        try {
            val config = EngineConfig(
                modelPath = "/data/local/tmp/gemma-4-E2B-it.litertlm",
                backend = Backend.CPU(),
                cacheDir = context.getExternalFilesDir(null)?.absolutePath
            )
            val newEngine = Engine(config)
            newEngine.initialize()
            engine = newEngine
            conversation = newEngine.createConversation()
            activeBackend = "CPU"
            isInitialized = true
            Log.i("LmManager", "Engine successfully initialized with CPU backend.")
        } catch (e: Exception) {
            Log.e("LmManager", "Failed to initialize CPU backend: ${e.message}", e)
        }
    }

    fun close() {
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
        isInitialized = false
        activeBackend = "None"
    }
}
