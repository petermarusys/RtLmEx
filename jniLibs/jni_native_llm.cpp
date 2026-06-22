#include <jni.h>
#include <string>
#include <android/log.h>
#include "llm.h"

#define LOG_TAG "LLM_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static LLM* getHandle(jlong handle) {
    return reinterpret_cast<LLM*>(handle);
}

extern "C" {

// 1. nativeCreate
JNIEXPORT jlong JNICALL
Java_com_peyo_rtlmex_LLMWrapper_nativeCreate(JNIEnv* env, jobject) {
    return reinterpret_cast<jlong>(new LLM());
}

// 2. nativeInit
JNIEXPORT jboolean JNICALL
Java_com_peyo_rtlmex_LLMWrapper_nativeInit(
        JNIEnv* env, jobject,
        jlong handle,
        jstring model_path,
        jstring tokenizer_path,
        jstring model_type) {

    const char* mp = env->GetStringUTFChars(model_path,     nullptr);
    const char* tp = env->GetStringUTFChars(tokenizer_path, nullptr);
    const char* mt = env->GetStringUTFChars(model_type,     nullptr);

    bool result = getHandle(handle)->init(mp, tp, mt);

    env->ReleaseStringUTFChars(model_path,     mp);
    env->ReleaseStringUTFChars(tokenizer_path, tp);
    env->ReleaseStringUTFChars(model_type,     mt);

    return result ? JNI_TRUE : JNI_FALSE;
}

// 3. nativeRun
JNIEXPORT void JNICALL
Java_com_peyo_rtlmex_LLMWrapper_nativeRun(
        JNIEnv* env, jobject thiz,
        jlong handle,
        jstring prompt) {

    const char* p = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_str(p);
    env->ReleaseStringUTFChars(prompt, p);

    JavaVM* jvm;
    env->GetJavaVM(&jvm);
    jobject global_thiz = env->NewGlobalRef(thiz);
    jclass clazz = env->GetObjectClass(thiz);
    jmethodID on_token = env->GetMethodID(clazz, "onToken", "(Ljava/lang/String;)V");
    jmethodID on_done  = env->GetMethodID(clazz, "onDone",  "()V");
    jmethodID on_error = env->GetMethodID(clazz, "onError", "()V");

    getHandle(handle)->run(prompt_str, [jvm, global_thiz, on_token, on_done, on_error]
            (const std::string& text, bool is_done, bool is_error) {

        JNIEnv* jni_env;
        jvm->AttachCurrentThread(&jni_env, nullptr);

        if (is_done) {
            jni_env->CallVoidMethod(global_thiz, on_done);
            jni_env->DeleteGlobalRef(global_thiz);
        } else if (is_error) {
            jni_env->CallVoidMethod(global_thiz, on_error);
            jni_env->DeleteGlobalRef(global_thiz);
        } else {
            jstring jtext = jni_env->NewStringUTF(text.c_str());
            jni_env->CallVoidMethod(global_thiz, on_token, jtext);
            jni_env->DeleteLocalRef(jtext);
        }
    });
}

// 4. nativeReset
JNIEXPORT void JNICALL
Java_com_peyo_rtlmex_LLMWrapper_nativeReset(JNIEnv*, jobject, jlong handle) {
    getHandle(handle)->reset();
}

// 5. nativeStop
JNIEXPORT void JNICALL
Java_com_peyo_rtlmex_LLMWrapper_nativeStop(JNIEnv*, jobject, jlong handle) {
    getHandle(handle)->stop();
}

// 6. nativeDestroy
JNIEXPORT void JNICALL
Java_com_peyo_rtlmex_LLMWrapper_nativeDestroy(JNIEnv*, jobject, jlong handle) {
    delete getHandle(handle);
}

} // extern "C"
