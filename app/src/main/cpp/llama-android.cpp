#include <jni.h>

#include "logging.h"

// Only public, exported headers. ggml-cpu.h and ggml-alloc.h are PRIVATE on
// their CMake targets and are deliberately not on our include path.
#include "llama.h"
#include "ggml.h"
#include "ggml-backend.h"

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstring>
#include <mutex>
#include <sstream>
#include <string>
#include <vector>

// ---------------------------------------------------------------------------
// Global backend bootstrap
//
// ggml_backend_load_all() enumerates and registers every available device
// (CPU, plus OpenCL / Vulkan when compiled in). It is process-global and
// reference counted by llama_backend_init(), so we run it exactly once no
// matter how many models get loaded or unloaded over the app's lifetime.
// ---------------------------------------------------------------------------
static std::once_flag g_backend_once;

static void ensure_backend_initialized() {
    std::call_once(g_backend_once, []() {
        llama_backend_init();
        ggml_backend_load_all();
        LOGI("llama.cpp backend initialized: %s", llama_print_system_info());
    });
}

// ---------------------------------------------------------------------------
// Per-model context
//
// Sampling parameters are deliberately held here and mutated in place by
// nativeSetSampling rather than baked into nativeInit. Reloading a 4 GB GGUF
// just to change the temperature is not viable on a phone, so model residency
// and sampling config are separate lifecycles.
// ---------------------------------------------------------------------------
struct LlamaAndroidContext {
    llama_model         * model   = nullptr;
    llama_context       * ctx     = nullptr;
    const llama_vocab   * vocab   = nullptr;
    // Owned sampler chain, rebuilt by rebuild_sampler_chain() whenever sampling
    // config or grammar changes. Guarded by decode_mutex; freed on rebuild and
    // on context destroy. Never null during decode.
    llama_sampler       * sampler = nullptr;

    std::string          model_path;
    std::string          grammar;
    std::string          chat_template;

    int32_t  n_threads    = 4;
    int32_t  n_ctx        = 2048;
    int32_t  n_gpu_layers = 0;
    int32_t  n_batch      = 512;
    int32_t  prefill_chunk = 256;

    float    temperature  = 0.0f;
    float    top_p        = 0.85f;
    float    min_p        = 0.05f;
    int32_t  top_k        = 40;
    int32_t  n_predict    = 512;
    uint32_t seed         = LLAMA_DEFAULT_SEED;
    bool     flash_attn   = true;

    bool               initialized = false;
    std::atomic<bool> cancelled{false};

    // Serializes decode work: one llama_context owns one KV cache, so
    // overlapping requests would corrupt each other's sequence state.
    std::mutex decode_mutex;

    // Measured telemetry. Written by nativeCompletion, read by nativeGetStats.
    uint64_t last_tokens_generated = 0;
    double   last_ttft_ms          = 0.0;
    double   last_prompt_ms        = 0.0;
    double   last_decode_ms        = 0.0;
    int32_t  last_prompt_tokens    = 0;
    bool     last_stop_eog         = false;
    bool     last_stop_length      = false;
    bool     last_stop_cancelled   = false;
};

static std::string jstring_to_utf8(JNIEnv * env, jstring str) {
    if (str == nullptr) return std::string();
    const char * chars = env->GetStringUTFChars(str, nullptr);
    if (chars == nullptr) return std::string();
    std::string out(chars);
    env->ReleaseStringUTFChars(str, chars);
    return out;
}

// ---------------------------------------------------------------------------
// Length of the longest prefix of `s` that terminates on a complete UTF-8
// code point.
//
// A BPE token can split a multi-byte code point across tokens, so streaming
// raw pieces straight into the JVM can emit a half-formed sequence and
// corrupt the String. We hold back the trailing 1-3 bytes until the rest
// arrives instead.
// ---------------------------------------------------------------------------
static size_t utf8_safe_prefix_len(const std::string & s) {
    const size_t n = s.size();
    if (n == 0) return 0;

    // Walk back over continuation bytes (10xxxxxx) to find the lead byte.
    size_t start = n - 1;
    while (start > 0 && (static_cast<unsigned char>(s[start]) & 0xC0) == 0x80) {
        if (n - start >= 4) break;  // malformed; bail out rather than underflow
        --start;
    }

    const unsigned char lead = static_cast<unsigned char>(s[start]);
    size_t need = 1;
    if      (lead >= 0xF0) need = 4;
    else if (lead >= 0xE0) need = 3;
    else if (lead >= 0xC0) need = 2;

    const size_t have = n - start;
    return have >= need ? n : start;  // 0 => code point still incomplete
}

// ---------------------------------------------------------------------------
// Build the sampler chain for the current sampling config.
//
// Order matters: repetition penalties and truncation run before temperature,
// and the grammar sampler is always last so it constrains whatever the
// distribution produced. A GBNF grammar makes the output structurally valid
// by masking disallowed tokens during sampling, so it is genuinely enforced
// at decode time and not merely validated after the fact.
// ---------------------------------------------------------------------------
static void rebuild_sampler_chain(LlamaAndroidContext * ctx) {
    if (ctx->sampler) {
        llama_sampler_free(ctx->sampler);
        ctx->sampler = nullptr;
    }

    auto chain_params = llama_sampler_chain_default_params();
    // We time the decode loop ourselves with steady_clock, so the sampler's
    // own per-token timing is redundant overhead on a phone.
    chain_params.no_perf = true;
    ctx->sampler = llama_sampler_chain_init(chain_params);
    if (ctx->sampler == nullptr) {
        LOGE("failed to allocate sampler chain");
        return;
    }

    // Chain order mirrors llama.cpp's own common/sampling defaults: penalties,
    // then truncation stages, then temperature, then the distribution, then
    // the grammar last so it constrains whatever survived the earlier stages.
    //
    // n_vocab is the first argument; passing 0 would silently disable the
    // repetition penalty.
    llama_sampler_chain_add(ctx->sampler,
        llama_sampler_init_penalties(
            llama_vocab_n_tokens(ctx->vocab),
            /* penalty_last_n   */ 64,
            /* penalty_repeat   */ 1.10f,
            /* penalty_freq     */ 0.00f,
            /* penalty_present  */ 0.00f));

    // top_k == 1 is greedy argmax; skip the expensive chain stages for it.
    if (ctx->top_k > 0) {
        llama_sampler_chain_add(ctx->sampler,
            llama_sampler_init_top_k(ctx->top_k));
    }
    if (ctx->top_p < 1.0f) {
        llama_sampler_chain_add(ctx->sampler,
            llama_sampler_init_top_p(ctx->top_p, 1));
    }
    if (ctx->min_p > 0.0f) {
        llama_sampler_chain_add(ctx->sampler,
            llama_sampler_init_min_p(ctx->min_p, 1));
    }

    llama_sampler_chain_add(ctx->sampler,
        llama_sampler_init_temp(ctx->temperature));

    // Greedy ignores the seed entirely; dist honours it.
    if (ctx->temperature <= 0.0f) {
        llama_sampler_chain_add(ctx->sampler, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(ctx->sampler,
            llama_sampler_init_dist(ctx->seed));
    }

    if (!ctx->grammar.empty()) {
        // Returns NULL when the GBNF fails to parse. Leaving the grammar out
        // silently would produce unconstrained output while the Kotlin layer
        // still reports GBNF as active, so we hard-fail instead.
        auto * grammar_sampler = llama_sampler_init_grammar(
            ctx->vocab, ctx->grammar.c_str(), "root");
        if (grammar_sampler == nullptr) {
            LOGE("GBNF grammar failed to parse; disabling grammar constraint");
            return;
        }
        llama_sampler_chain_add(ctx->sampler, grammar_sampler);
        LOGI("GBNF grammar constraint active (%zu bytes)", ctx->grammar.size());
    }
}

// ---------------------------------------------------------------------------
// Model load + context creation
// ---------------------------------------------------------------------------
static LlamaAndroidContext * create_context(
        const std::string & model_path,
        int32_t n_threads,
        int32_t n_ctx,
        int32_t n_gpu_layers,
        int32_t n_batch,
        bool use_mmap,
        bool flash_attn,
        std::string & error_out) {

    ensure_backend_initialized();

    auto * ctx = new LlamaAndroidContext();
    ctx->model_path    = model_path;
    ctx->n_threads     = std::max(1, n_threads);
    ctx->n_ctx         = n_ctx > 0 ? n_ctx : 2048;
    ctx->n_gpu_layers  = n_gpu_layers;
    ctx->n_batch       = std::max(32, n_batch);
    ctx->flash_attn    = flash_attn;
    ctx->prefill_chunk = std::min(ctx->n_batch, 256);

    auto mparams = llama_model_default_params();
    mparams.n_gpu_layers = n_gpu_layers;
    // mmap keeps the 1-4 GB GGUF off the Java heap and lets the kernel page
    // weights in on demand, which is what makes multi-GB models viable here.
    mparams.load_mode = use_mmap ? LLAMA_LOAD_MODE_MMAP : LLAMA_LOAD_MODE_NONE;

    ctx->model = llama_model_load_from_file(model_path.c_str(), mparams);
    if (ctx->model == nullptr) {
        error_out = "failed to load GGUF model: " + model_path;
        LOGE("%s", error_out.c_str());
        delete ctx;
        return nullptr;
    }

    const uint32_t model_train_ctx = llama_model_n_ctx_train(ctx->model);
    if (model_train_ctx > 0 && (uint32_t) ctx->n_ctx > model_train_ctx) {
        LOGW("requested n_ctx=%d exceeds model training context %u; clamping",
             ctx->n_ctx, model_train_ctx);
        ctx->n_ctx = (int32_t) model_train_ctx;
    }

    auto cparams = llama_context_default_params();
    cparams.n_ctx         = (uint32_t) ctx->n_ctx;
    cparams.n_batch       = (uint32_t) ctx->n_batch;
    cparams.n_ubatch      = (uint32_t) ctx->n_batch;
    cparams.n_threads     = ctx->n_threads;
    cparams.n_threads_batch = ctx->n_threads;
    cparams.flash_attn_type = flash_attn
        ? LLAMA_FLASH_ATTN_TYPE_ENABLED
        : LLAMA_FLASH_ATTN_TYPE_DISABLED;
    // The SoC is a phone: offloading the KV cache to a slow mobile GPU often
    // loses to staying on CPU, so leave KQV on the host by default.
    cparams.offload_kqv = n_gpu_layers > 0;
    cparams.no_perf     = false;

    ctx->ctx = llama_init_from_model(ctx->model, cparams);
    if (ctx->ctx == nullptr) {
        error_out = "failed to create llama context (n_ctx too large for device memory?)";
        LOGE("%s", error_out.c_str());
        llama_model_free(ctx->model);
        delete ctx;
        return nullptr;
    }

    ctx->vocab = llama_model_get_vocab(ctx->model);
    if (ctx->vocab == nullptr) {
        error_out = "model has no vocabulary";
        llama_free(ctx->ctx);
        llama_model_free(ctx->model);
        delete ctx;
        return nullptr;
    }

    const char * tmpl = llama_model_chat_template(ctx->model, nullptr);
    ctx->chat_template = tmpl != nullptr ? std::string(tmpl) : std::string();

    // llama_model_desc writes into the caller's buffer and returns the length.
    char desc_buf[256] = {0};
    const int32_t desc_len = llama_model_desc(ctx->model, desc_buf, sizeof(desc_buf) - 1);
    if (desc_len < 0) desc_buf[0] = '\0';
    desc_buf[sizeof(desc_buf) - 1] = '\0';

    LOGI("loaded model: n_ctx=%d n_threads=%d n_gpu_layers=%d desc=%s template=%s",
         ctx->n_ctx, ctx->n_threads, ctx->n_gpu_layers,
         desc_buf[0] != '\0' ? desc_buf : "?",
         ctx->chat_template.empty() ? "<none>" : "present");

    rebuild_sampler_chain(ctx);
    ctx->initialized = true;
    return ctx;
}

static void destroy_context(LlamaAndroidContext * ctx) {
    if (ctx == nullptr) return;
    if (ctx->sampler) llama_sampler_free(ctx->sampler);
    if (ctx->ctx)     llama_free(ctx->ctx);
    if (ctx->model)   llama_model_free(ctx->model);
    delete ctx;
}

extern "C" {

// ---------------------------------------------------------------------------
// JNI names must match the Kotlin package com.perpcorp.edgellm.engine.
// nativeInit lives in LlamaContext's companion object, so its symbol uses the
// Companion-mangled name (LlamaContext_00024Companion). The rest are instance
// methods of LlamaContext itself.
// ---------------------------------------------------------------------------

/**
 * Load a GGUF model and create an inference context. Returns an opaque handle,
 * or 0 on failure (check nativeLastError for the reason).
 */
JNIEXPORT jlong JNICALL
Java_com_perpcorp_edgellm_engine_LlamaContext_00024Companion_nativeInit(
    JNIEnv * env,
    jobject /* companion */,
    jstring model_path_str,
    jint n_threads,
    jint n_ctx,
    jint n_gpu_layers,
    jint n_batch,
    jboolean use_mmap,
    jboolean use_flash_attn
) {
    const std::string model_path = jstring_to_utf8(env, model_path_str);
    if (model_path.empty()) return 0;

    std::string error;
    auto * ctx = create_context(
        model_path,
        n_threads,
        n_ctx,
        n_gpu_layers,
        n_batch,
        use_mmap == JNI_TRUE,
        use_flash_attn == JNI_TRUE,
        error);

    if (ctx == nullptr) {
        jclass cls = env->FindClass("com/perpcorp/edgellm/engine/LlamaContext");
        if (cls != nullptr) {
            jmethodID mid = env->GetStaticMethodID(
                cls, "nativeSetLastError", "(Ljava/lang/String;)V");
            if (mid != nullptr) env->CallStaticVoidMethod(cls, mid, env->NewStringUTF(error.c_str()));
        }
        return 0;
    }
    return reinterpret_cast<jlong>(ctx);
}

/**
 * Update sampling parameters in place. No model reload, so this is safe to
 * call on every request.
 */
JNIEXPORT void JNICALL
Java_com_perpcorp_edgellm_engine_LlamaContext_nativeSetSampling(
    JNIEnv * /* env */,
    jobject /* this */,
    jlong handle,
    jfloat temperature,
    jfloat top_p,
    jint top_k,
    jfloat min_p,
    jint n_predict,
    jint seed
) {
    auto * ctx = reinterpret_cast<LlamaAndroidContext *>(handle);
    if (ctx == nullptr || !ctx->initialized) return;

    // Rebuilding the chain frees the old sampler, so it must not race a decode
    // loop that is sampling through it. Taking the same lock nativeCompletion
    // holds means this waits for an in-flight generation rather than pulling
    // the sampler out from under it.
    std::lock_guard<std::mutex> lock(ctx->decode_mutex);

    ctx->temperature = temperature < 0.0f ? 0.0f : temperature;
    ctx->top_p       = (top_p > 0.0f && top_p <= 1.0f) ? top_p : 0.85f;
    ctx->top_k       = top_k;
    ctx->min_p       = min_p < 0.0f ? 0.0f : min_p;
    ctx->n_predict   = n_predict > 0 ? n_predict : 512;
    ctx->seed        = seed == -1 ? LLAMA_DEFAULT_SEED : (uint32_t) seed;

    rebuild_sampler_chain(ctx);
}

/**
 * Install (or clear, by passing an empty string) a GBNF grammar that constrains
 * sampling. Returns 1 on success, 0 if the grammar failed to parse.
 */
JNIEXPORT jboolean JNICALL
Java_com_perpcorp_edgellm_engine_LlamaContext_nativeSetGrammar(
    JNIEnv * env,
    jobject /* this */,
    jlong handle,
    jstring gbnf_str
) {
    auto * ctx = reinterpret_cast<LlamaAndroidContext *>(handle);
    if (ctx == nullptr || !ctx->initialized) return JNI_FALSE;

    // Same reasoning as nativeSetSampling: the probe sampler and the rebuild
    // both mutate sampler state, so exclude a concurrent decode.
    std::lock_guard<std::mutex> lock(ctx->decode_mutex);

    const std::string gbnf = jstring_to_utf8(env, gbnf_str);

    if (!gbnf.empty()) {
        // Validate before committing so a bad grammar leaves the previous
        // working config intact rather than dropping to unconstrained output.
        auto * probe = llama_sampler_init_grammar(ctx->vocab, gbnf.c_str(), "root");
        if (probe == nullptr) {
            LOGE("nativeSetGrammar: GBNF rejected, keeping previous grammar");
            return JNI_FALSE;
        }
        llama_sampler_free(probe);
    }

    ctx->grammar = gbnf;
    rebuild_sampler_chain(ctx);
    return JNI_TRUE;
}

/**
 * Cooperative cancellation flag, polled once per decoded token.
 */
JNIEXPORT void JNICALL
Java_com_perpcorp_edgellm_engine_LlamaContext_nativeSetCancelled(
    JNIEnv * /* env */,
    jobject /* this */,
    jlong handle,
    jboolean cancelled
) {
    auto * ctx = reinterpret_cast<LlamaAndroidContext *>(handle);
    if (ctx == nullptr) return;
    ctx->cancelled.store(cancelled == JNI_TRUE);
}

/**
 * The model's own chat template, or "" when it ships without one. The Kotlin
 * layer needs this to format chat turns correctly; feeding a raw prompt to an
 * instruction-tuned GGUF produces garbage otherwise.
 */
JNIEXPORT jstring JNICALL
Java_com_perpcorp_edgellm_engine_LlamaContext_nativeGetChatTemplate(
    JNIEnv * env,
    jobject /* this */,
    jlong handle
) {
    auto * ctx = reinterpret_cast<LlamaAndroidContext *>(handle);
    if (ctx == nullptr) return env->NewStringUTF("");
    return env->NewStringUTF(ctx->chat_template.c_str());
}

/**
 * Run a real decode loop: tokenize, prefill, then sample-and-decode one token
 * at a time, pushing each piece to the Kotlin TokenCallback as it is produced.
 *
 * Returns the complete generated text, or a string starting with "Error:" on
 * failure (the Kotlin layer treats that as "fall back", never as content).
 */
JNIEXPORT jstring JNICALL
Java_com_perpcorp_edgellm_engine_LlamaContext_nativeCompletion(
    JNIEnv * env,
    jobject /* this */,
    jlong handle,
    jstring prompt_str,
    jobject callback_obj
) {
    auto * ctx = reinterpret_cast<LlamaAndroidContext *>(handle);
    if (ctx == nullptr || !ctx->initialized) {
        return env->NewStringUTF("Error: context not initialized");
    }

    std::lock_guard<std::mutex> lock(ctx->decode_mutex);

    const auto t_start = std::chrono::steady_clock::now();
    ctx->cancelled.store(false);

    // --- resolve the streaming callback once, not per token ---
    jmethodID on_token = nullptr;
    jclass    callback_class = nullptr;
    if (callback_obj != nullptr) {
        callback_class = env->GetObjectClass(callback_obj);
        if (callback_class != nullptr) {
            on_token = env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;)V");
        }
    }

    struct CallbackGuard {
        JNIEnv * e; jclass c;
        ~CallbackGuard() { if (c) e->DeleteLocalRef(c); }
    } guard{env, callback_class};

    if (callback_obj != nullptr && on_token == nullptr) {
        return env->NewStringUTF("Error: TokenCallback.onToken(String) not found");
    }

    const std::string prompt = jstring_to_utf8(env, prompt_str);
    if (prompt.empty()) {
        return env->NewStringUTF("Error: empty prompt");
    }

    // --- tokenize ---
    // add_special = true lets the GGUF's own BOS/EOS handling apply, which
    // varies per architecture and is required for correct logits.
    const int32_t prompt_cap = ctx->n_ctx - ctx->n_predict - 1;
    if (prompt_cap <= 0) {
        return env->NewStringUTF("Error: n_ctx too small for the requested n_predict");
    }

    std::vector<llama_token> prompt_tokens((size_t) prompt_cap);
    int32_t n_prompt = llama_tokenize(
        ctx->vocab, prompt.c_str(), (int32_t) prompt.size(),
        prompt_tokens.data(), prompt_cap, /* add_special */ true, /* parse_special */ false);

    if (n_prompt < 0) {
        // Negative return means "this many tokens would have been produced".
        const int32_t needed = -n_prompt;
        if (needed > prompt_cap) {
            std::ostringstream ss;
            ss << "Error: prompt needs " << needed
               << " tokens but only " << prompt_cap
               << " fit in n_ctx=" << ctx->n_ctx;
            return env->NewStringUTF(ss.str().c_str());
        }
        n_prompt = llama_tokenize(
            ctx->vocab, prompt.c_str(), (int32_t) prompt.size(),
            prompt_tokens.data(), needed, true, false);
    }
    if (n_prompt <= 0) {
        return env->NewStringUTF("Error: prompt tokenized to zero tokens");
    }

    ctx->last_prompt_tokens = n_prompt;
    ctx->last_stop_eog = ctx->last_stop_length = ctx->last_stop_cancelled = false;

    // --- prefill ---
    // Start from a clean KV cache; a stale cache from a previous turn would
    // be attended to and corrupt the first token.
    llama_memory_clear(llama_get_memory(ctx->ctx), true);
    llama_perf_context_reset(ctx->ctx);

    const auto t_prefill_start = std::chrono::steady_clock::now();
    int32_t n_processed = 0;
    while (n_processed < n_prompt) {
        if (ctx->cancelled.load()) {
            ctx->last_stop_cancelled = true;
            return env->NewStringUTF("Error: cancelled");
        }

        const int32_t chunk = std::min(ctx->prefill_chunk, n_prompt - n_processed);
        llama_batch batch = llama_batch_init(chunk, 0, 1);
        for (int32_t i = 0; i < chunk; ++i) {
            batch.token[i]     = prompt_tokens[(size_t)(n_processed + i)];
            batch.pos[i]       = n_processed + i;
            batch.n_seq_id[i]  = 1;
            batch.seq_id[i][0] = 0;
            // Only the final prompt token needs logits; asking for all of them
            // wastes a large logits buffer and measurable memory bandwidth.
            batch.logits[i]    = (n_processed + i == n_prompt - 1) ? 1 : 0;
        }

        const int rc = llama_decode(ctx->ctx, batch);
        llama_batch_free(batch);

        if (rc != 0) {
            std::ostringstream ss;
            ss << "Error: llama_decode failed on prompt chunk (" << rc << ")";
            LOGE("%s", ss.str().c_str());
            return env->NewStringUTF(ss.str().c_str());
        }
        n_processed += chunk;
    }

    const auto t_prefill_end = std::chrono::steady_clock::now();
    ctx->last_prompt_ms =
        std::chrono::duration<double, std::milli>(t_prefill_end - t_prefill_start).count();

    // Reset here so t_eval_ms / n_eval measure generation only, giving a real
    // decode-phase tokens/sec rather than a blended average.
    llama_perf_context_reset(ctx->ctx);

    // --- decode loop ---
    std::string result;
    std::string pending_utf8;
    int32_t n_generated = 0;
    bool first_token = true;

    const int32_t budget = std::min(ctx->n_predict, ctx->n_ctx - n_prompt - 1);

    while (n_generated < budget) {
        if (ctx->cancelled.load()) {
            ctx->last_stop_cancelled = true;
            break;
        }

        const llama_token id = llama_sampler_sample(ctx->sampler, ctx->ctx, -1);
        if (id < 0) {
            LOGW("sampler returned LLAMA_TOKEN_NULL; stopping");
            break;
        }
        llama_sampler_accept(ctx->sampler, id);

        if (llama_vocab_is_eog(ctx->vocab, id)) {
            ctx->last_stop_eog = true;
            break;
        }

        char piece[256];
        const int32_t n_piece = llama_token_to_piece(
            ctx->vocab, id, piece, sizeof(piece), /* lstrip */ 0, /* special */ false);
        if (n_piece >= (int32_t) sizeof(piece)) {
            // Would have been truncated mid-code-point, which would corrupt
            // the output. No real BPE piece comes close to 256 bytes, so this
            // means something is wrong with the vocab rather than the token.
            LOGW("token %d piece >= %zu bytes; buffer too small, skipping", id, sizeof(piece));
            continue;
        }
        if (n_piece > 0) {
            result.append(piece, (size_t) n_piece);
            pending_utf8.append(piece, (size_t) n_piece);

            // Stream only whole code points to the JVM.
            const size_t safe = utf8_safe_prefix_len(pending_utf8);
            if (safe > 0) {
                const std::string chunk = pending_utf8.substr(0, safe);
                pending_utf8.erase(0, safe);

                if (first_token) {
                    const auto now = std::chrono::steady_clock::now();
                    ctx->last_ttft_ms =
                        std::chrono::duration<double, std::milli>(now - t_start).count();
                    first_token = false;
                }

                if (on_token != nullptr) {
                    jstring jchunk = env->NewStringUTF(chunk.c_str());
                    if (jchunk != nullptr) {
                        env->CallVoidMethod(callback_obj, on_token, jchunk);
                        env->DeleteLocalRef(jchunk);
                    }
                    // Surface any pending Java exception instead of looping on.
                    if (env->ExceptionCheck()) {
                        pending_utf8.clear();
                        break;
                    }
                }
            }
        }

        n_generated++;

        // Feed the sampled token back in to advance the KV cache.
        llama_batch batch = llama_batch_init(1, 0, 1);
        batch.token[0]     = id;
        batch.pos[0]       = n_prompt + n_generated - 1;
        batch.n_seq_id[0]  = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0]    = 1;

        const int rc = llama_decode(ctx->ctx, batch);
        llama_batch_free(batch);

        if (rc != 0) {
            LOGE("llama_decode failed during generation (%d); returning partial text", rc);
            break;
        }
    }

    if (n_generated >= budget) {
        ctx->last_stop_length = true;
    }

    // Flush any trailing partial code point that will never complete.
    if (on_token != nullptr && !pending_utf8.empty()) {
        jstring jchunk = env->NewStringUTF(pending_utf8.c_str());
        if (jchunk != nullptr) {
            env->CallVoidMethod(callback_obj, on_token, jchunk);
            env->DeleteLocalRef(jchunk);
        }
    }

    const auto t_end = std::chrono::steady_clock::now();
    ctx->last_decode_ms =
        std::chrono::duration<double, std::milli>(t_end - t_prefill_end).count();
    ctx->last_tokens_generated = (uint64_t) n_generated;

    LOGI("gen tokens=%d prompt_tokens=%d prefill=%.1fms decode=%.1fms tps=%.2f",
         n_generated, n_prompt, ctx->last_prompt_ms, ctx->last_decode_ms,
         ctx->last_decode_ms > 0.0 ? n_generated / (ctx->last_decode_ms / 1000.0) : 0.0);

    return env->NewStringUTF(result.c_str());
}

/**
 * Measured telemetry from the last nativeCompletion call, as JSON. These are
 * real counters read out of llama.cpp, not estimates.
 */
JNIEXPORT jstring JNICALL
Java_com_perpcorp_edgellm_engine_LlamaContext_nativeGetStats(
    JNIEnv * env,
    jobject /* this */,
    jlong handle
) {
    auto * ctx = reinterpret_cast<LlamaAndroidContext *>(handle);
    if (ctx == nullptr || !ctx->initialized) {
        return env->NewStringUTF("{\"error\":\"not initialized\"}");
    }

    const double tps = ctx->last_decode_ms > 0.0
        ? (double) ctx->last_tokens_generated / (ctx->last_decode_ms / 1000.0)
        : 0.0;

    std::ostringstream ss;
    ss << "{"
       << "\"tokensGenerated\":"   << ctx->last_tokens_generated << ","
       << "\"promptTokens\":"       << ctx->last_prompt_tokens << ","
       << "\"ttftMs\":"             << ctx->last_ttft_ms << ","
       << "\"promptEvalMs\":"       << ctx->last_prompt_ms << ","
       << "\"decodeMs\":"           << ctx->last_decode_ms << ","
       << "\"tokensPerSecond\":"    << tps << ","
       << "\"promptTokensPerSecond\":"
       << (ctx->last_prompt_ms > 0.0
              ? (double) ctx->last_prompt_tokens / (ctx->last_prompt_ms / 1000.0)
              : 0.0)
       << ","
       << "\"nCtx\":"               << ctx->n_ctx << ","
       << "\"nThreads\":"           << ctx->n_threads << ","
       << "\"nGpuLayers\":"         << ctx->n_gpu_layers << ","
       << "\"grammarActive\":"      << (ctx->grammar.empty() ? "false" : "true") << ","
       << "\"stopEog\":"            << (ctx->last_stop_eog ? "true" : "false") << ","
       << "\"stopLength\":"         << (ctx->last_stop_length ? "true" : "false") << ","
       << "\"stopCancelled\":"      << (ctx->last_stop_cancelled ? "true" : "false")
       << "}";
    return env->NewStringUTF(ss.str().c_str());
}

/**
 * Registered ggml devices as JSON: [{"name":"CPU","type":"CPU","gpu":false},...].
 *
 * The app's ComputeBackend enum is a user-facing setting, but whether a GPU
 * backend was actually compiled in is a build-time fact. Querying the real
 * registry lets the Kotlin layer report a truthful "unavailable" instead of
 * silently offloading nothing while claiming Vulkan.
 */
JNIEXPORT jstring JNICALL
Java_com_perpcorp_edgellm_engine_LlamaContext_nativeListBackends(
    JNIEnv * env,
    jobject /* this */
) {
    std::ostringstream ss;
    ss << "[";
    const size_t n = ggml_backend_dev_count();
    for (size_t i = 0; i < n; ++i) {
        ggml_backend_dev_t dev = ggml_backend_dev_get(i);
        if (dev == nullptr) continue;

        const char * name = ggml_backend_dev_name(dev);
        const char * desc = ggml_backend_dev_description(dev);
        const bool is_gpu = ggml_backend_dev_type(dev) == GGML_BACKEND_DEVICE_TYPE_GPU;

        if (i > 0) ss << ",";
        ss << "{\"name\":\"" << (name ? name : "unknown") << "\",";
        ss << "\"description\":\"" << (desc ? desc : "") << "\",";
        ss << "\"type\":\"" << (is_gpu ? "GPU" : "CPU") << "\",";
        ss << "\"gpu\":" << (is_gpu ? "true" : "false") << "}";
    }
    ss << "]";
    return env->NewStringUTF(ss.str().c_str());
}

/**
 * Free the context and the model. Safe to call with 0.
 */
JNIEXPORT void JNICALL
Java_com_perpcorp_edgellm_engine_LlamaContext_nativeRelease(
    JNIEnv * /* env */,
    jobject /* this */,
    jlong handle
) {
    auto * ctx = reinterpret_cast<LlamaAndroidContext *>(handle);
    if (ctx == nullptr) return;
    // Waits out any in-flight decode so the model and context are not freed
    // while llama_decode() is still walking them.
    {
        std::lock_guard<std::mutex> lock(ctx->decode_mutex);
        LOGI("releasing llama context for model: %s", ctx->model_path.c_str());
        destroy_context(ctx);
    }
}

} // extern "C"
