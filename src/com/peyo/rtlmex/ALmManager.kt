package com.peyo.rtlmex

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

object ALmManager {
    private var engine: AmlogicLlmEngine? = null

    var isInitialized = false
        private set

    fun sendMessageAsync(prompt: String): Flow<String> = callbackFlow {
        engine?.generate(prompt)?.collect { state ->
            if (state.content.isNotEmpty()) trySend(state.content)
            if (state.isDone) channel.close()
        }
    }

    suspend fun initialize(context: Context) {
        if (engine != null) return // Already initialized

        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        Log.i("ALmManager", "Initializing engine with nativeLibDir=$nativeLibDir")

        try {
            val newEngine = AmlogicLlmEngine(context)
            withContext(Dispatchers.IO) {newEngine.load()}
            engine = newEngine
            isInitialized = true
            Log.i("ALmManager", "Engine successfully initialized .")
        } catch (e: Exception) {
            Log.e("ALmManager", "Failed to initialize ${e.message}", e)
        }
    }

    fun close() {
        engine?.close()
        engine = null
        isInitialized = false
    }
}
