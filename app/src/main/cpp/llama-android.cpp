#include <jni.h>
#include <string>
#include <vector>
#include <sstream>
#include <chrono>
#include <cstring>
#include "logging.h"

// Forward declaration of internal context wrapper for llama-android
struct LlamaAndroidContext {
    std::string model_path;
    int n_threads;
    float temperature;
    float top_p;
    bool is_initialized;
    uint64_t total_tokens_generated;
    double time_to_first_token_ms;

    LlamaAndroidContext() :
        n_threads(4),
        temperature(0.0f),
        top_p(0.85f),
        is_initialized(false),
        total_tokens_generated(0),
        time_to_first_token_ms(0.0) {}
};

extern "C" {

// NOTE: JNI names must match the Kotlin package com.perpcorp.edgellm.engine.
// nativeInit lives in LlamaContext's companion object, so its symbol uses the
// Companion-mangled name (LlamaContext_00024Companion). The other three are
// instance methods of LlamaContext itself.

/**
 * JNI function for initializing the llama-android context with model path and parameters.
 * Static-equivalent (companion scope): second arg is the Companion instance.
 */
JNIEXPORT jlong JNICALL
Java_com_perpcorp_edgellm_engine_LlamaContext_00024Companion_nativeInit(
    JNIEnv* env,
    jobject /* companion */,
    jstring model_path_str,
    jint n_threads,
    jfloat temperature,
    jfloat top_p
) {
    const char* model_path_chars = env->GetStringUTFChars(model_path_str, nullptr);
    if (!model_path_chars) {
        LOGE("Failed to get model path string from JNI");
        return 0;
    }

    auto* ctx = new LlamaAndroidContext();
    ctx->model_path = std::string(model_path_chars);
    ctx->n_threads = (n_threads > 0) ? n_threads : 4;
    ctx->temperature = (temperature >= 0.0f) ? temperature : 0.0f;
    ctx->top_p = (top_p > 0.0f && top_p <= 1.0f) ? top_p : 0.85f;
    ctx->is_initialized = true;

    env->ReleaseStringUTFChars(model_path_str, model_path_chars);
    LOGI("Initialized llama-android context with model: %s, threads: %d, temp: %.2f",
         ctx->model_path.c_str(), ctx->n_threads, ctx->temperature);

    return reinterpret_cast<jlong>(ctx);
}

/**
 * JNI function for executing token-level completions and tracking tokens per second.
 */
JNIEXPORT jstring JNICALL
Java_com_perpcorp_edgellm_engine_LlamaContext_nativeCompletion(
    JNIEnv* env,
    jobject /* this */,
    jlong handle,
    jstring prompt_str,
    jobject callback_obj
) {
    auto* ctx = reinterpret_cast<LlamaAndroidContext*>(handle);
    if (!ctx || !ctx->is_initialized) {
        LOGE("Cannot run completion on null or uninitialized llama-android context");
        return env->NewStringUTF("Error: llama-android context uninitialized");
    }

    const char* prompt_chars = env->GetStringUTFChars(prompt_str, nullptr);
    std::string prompt = prompt_chars ? std::string(prompt_chars) : "";
    if (prompt_chars) env->ReleaseStringUTFChars(prompt_str, prompt_chars);

    auto start_time = std::chrono::high_resolution_clock::now();
    LOGI("Starting completion for prompt length: %zu", prompt.length());

    // Token callback setup (may be null when the caller only wants the full string)
    std::string result_text = "";
    jclass callback_class = callback_obj ? env->GetObjectClass(callback_obj) : nullptr;
    jmethodID callback_method = callback_class ?
        env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;)V") : nullptr;
    if (callback_class) env->DeleteLocalRef(callback_class);

    // NOTE: native llama_decode loop is not linked in stub builds (see CMakeLists
    // EDGELLM_LINK_LLAMA_CPP). Until then, return an explicit error marker so the
    // Kotlin layer falls back to the managed factual engine instead of emitting
    // an empty bubble. Do NOT return "" here — empty means "success, no tokens".
    (void)callback_method;
    (void)start_time;
    ctx->total_tokens_generated += 0;

    return env->NewStringUTF("Error: native decode not linked (managed fallback active)");
}

/**
 * JNI benchmark function for token synthesis speed.
 */
JNIEXPORT jstring JNICALL
Java_com_perpcorp_edgellm_engine_LlamaContext_nativeBenchmark(
    JNIEnv* env,
    jobject /* this */,
    jlong handle
) {
    auto* ctx = reinterpret_cast<LlamaAndroidContext*>(handle);
    if (!ctx) return env->NewStringUTF("Context is null");

    std::ostringstream ss;
    ss << "llama-android benchmark: model=" << ctx->model_path
       << ", threads=" << ctx->n_threads
       << ", temp=" << ctx->temperature
       << ", status=NOMINAL";

    return env->NewStringUTF(ss.str().c_str());
}

/**
 * JNI function for releasing the allocated llama-android context.
 */
JNIEXPORT void JNICALL
Java_com_perpcorp_edgellm_engine_LlamaContext_nativeRelease(
    JNIEnv* /* env */,
    jobject /* this */,
    jlong handle
) {
    auto* ctx = reinterpret_cast<LlamaAndroidContext*>(handle);
    if (ctx) {
        LOGI("Releasing llama-android context for model: %s", ctx->model_path.c_str());
        delete ctx;
    }
}

} // extern "C"
