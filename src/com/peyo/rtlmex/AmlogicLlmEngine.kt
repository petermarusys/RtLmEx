package com.peyo.rtlmex

import android.content.Context
import android.util.Log
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "AmlogicLlmEngine"
class AmlogicLlmEngine(context: Context) {

    data class GenerationState(
        val content: String = "",
        val isDone: Boolean = false,
    )

    // 🔥 LiteRtLmEngine처럼 인스턴스를 가변적으로 관리
    private var wrapper: LLMWrapper? = null

    var isReady: Boolean = false
        private set

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun load() {
        isReady = false

        val oldWrapper = wrapper
        wrapper = null

        System.gc()
        delay(500)

        val modelPath = "/data/local/tmp/gemma-3-1b-it-f16_quant_i8.adla"
        val tokenizerPath = "/data/local/tmp/tokenizer.json"
        val modelType = "AmlGemma3E1B"

        Log.i(TAG, "Amlogic Native 엔진 로드 시도: $modelPath")

        val newWrapper = LLMWrapper().apply { create() }

        val success = withContext(Dispatchers.IO) {
            newWrapper.init(modelPath, tokenizerPath, modelType)
        }

        if (success) {
            wrapper = newWrapper
            isReady = true
            Log.i(TAG, "Amlogic Native 엔진 로드 성공")

            // 3. 🔥 구형 엔진 자원 백그라운드 정리 (LiteRtLmEngine 방식)
            if (oldWrapper != null) {
                Log.d(TAG, "구형 엔진 자원 백그라운드 정리 시작")
                GlobalScope.launch(Dispatchers.Default) {
                    try {
                        val startCleanup = System.currentTimeMillis()
                        oldWrapper.stop()
                        oldWrapper.destroy()
                        Log.i(TAG, "구형 엔진 정리 완료 (${System.currentTimeMillis() - startCleanup}ms)")
                    } catch (e: Exception) {
                        Log.w(TAG, "구형 엔진 정리 중 오류: ${e.message}")
                    }
                }
            }
        } else {
            newWrapper.destroy()
            Log.e(TAG, "Amlogic Native 엔진 초기화 실패")
            throw IllegalStateException("Amlogic SDK 초기화 실패 (메모리 부족 가능성)")
        }
    }

     fun generate(
        userMessage: String,
    ): Flow<GenerationState> = callbackFlow {
        val currentWrapper = wrapper ?: run {
            close(IllegalStateException("엔진이 로드되지 않았습니다."))
            return@callbackFlow
        }

        currentWrapper.setListener(object :
            LLMWrapper.LLMListener {
            override fun onToken(text: String) {
                trySend(GenerationState(content = text, isDone = false))
            }

            override fun onDone() {
                Log.i(
                    TAG,
                    "DONE"
                )
                trySend(
                    GenerationState(
                        content = "",
                        isDone = true,
                    )
                )
                channel.close()
            }

            override fun onError() {
                Log.e(TAG, "ERROR 발생")  // 🔥 추가
                channel.close(RuntimeException("Amlogic JNI 추론 에러"))
            }
        })

        val inferenceJob = launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "NPU 추론 실행 시작")
                currentWrapper.run(userMessage)
            } catch (e: Exception) {
                Log.e(TAG, "NPU 실행 오류: ${e.message}")
                close(e)
            }
        }

        awaitClose {
            Log.d(TAG, "추론 중단 요청")
            inferenceJob.cancel()
            currentWrapper.stop()
        }
    }

    fun reset(systemInstruction: String?) {
        wrapper?.reset()
    }

    fun close() {
        wrapper?.let {
            it.stop()
            it.destroy()
        }
        wrapper = null
        isReady = false
    }
}
