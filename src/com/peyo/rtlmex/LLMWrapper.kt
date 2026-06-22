package com.peyo.rtlmex

import android.util.Log

class LLMWrapper {
    private var handle: Long = 0

    companion object {
        init {
            System.loadLibrary("adla")
            System.loadLibrary("teec")
            System.loadLibrary("tokenizers_cpp")
            System.loadLibrary("llmsdk")
            System.loadLibrary("llm")
        }
    }

    // 수정된 인터페이스: void 제거, fun 사용
    interface LLMListener {
        fun onToken(token: String) // void 대신 fun
        fun onDone()              // void 대신 fun
        fun onError()             // void 대신 fun
    }

    private var listener: LLMListener? = null

    fun setListener(listener: LLMListener?) {
        this.listener = listener
    }

    fun create() {
        handle = nativeCreate()
    }

    fun init(modelPath: String, tokenizerPath: String, modelType: String): Boolean {
        return nativeInit(handle, modelPath, tokenizerPath, modelType)
    }

    fun run(prompt: String) {
        nativeRun(handle, prompt)
    }

    fun reset() = nativeReset(handle)
    fun stop() = nativeStop(handle)

    fun destroy() {
        nativeDestroy(handle)
        handle = 0
    }

    // JNI에서 호출할 때 리스너로 전달하는 브릿지 함수들
    fun onToken(text: String) {
        listener?.onToken(text)
    }

    fun onDone() {
        listener?.onDone()
    }

    fun onError() {
        listener?.onError()
    }

    private external fun nativeCreate(): Long
    private external fun nativeInit(handle: Long, modelPath: String, tokenizerPath: String, modelType: String): Boolean
    private external fun nativeRun(handle: Long, prompt: String)
    private external fun nativeReset(handle: Long)
    private external fun nativeStop(handle: Long)
    private external fun nativeDestroy(handle: Long)
}