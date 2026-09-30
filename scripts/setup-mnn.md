# MNN-LLM integration (manual — not available via Gradle)

`ModelFormat.MNN_LLM` currently reports **Unavailable**. This file records why
and exactly what is needed to turn it on.

## Why it is not wired up

Alibaba publishes **no Maven artifact** for MNN. Verified absent from:

| Repository | Coordinate | Result |
|---|---|---|
| Maven Central | `com.alibaba.mnn:mnn-android` | 404 |
| JitPack | `com.github.alibaba.MNN:mnn-android` | 404 |
| Aliyun public | `com.alibaba.mnn:mnn-android` | 404 |
| Google Maven | `com.google.ai.edge.litert:*` | n/a |

`https://github.com/alibaba/MNN/releases` ships only `.zip` bundles such as
`mnn_<ver>_android_armv7_armv8_cpu_opencl_vulkan.zip`. MNN's own reference
Android app (`apps/Android/MnnLlmChat`) resolves its native libraries through a
Gradle *download* plugin rather than a normal dependency, which confirms there is
nothing for `build.gradle.kts` to resolve.

Two further problems make the stock zip insufficient for LLM work:

1. The LLM runtime is a **separate build target**. `MNN::llm::Llm`
   (`transformers/llm/engine/include/llm/llm.hpp` on `master`) is compiled only
   when `MNN_LLM=ON`; the default Android release is the CV runtime.
2. MNN's `Llm` is C++-only. There is no Kotlin/Java binding, so a JNI layer has
   to be written and compiled against the NDK.

## What is already verified

The API a future JNI layer should target, read from `master`:

```cpp
namespace MNN { namespace llm {
class Llm {
    static Llm* createLLM(const std::string& config_path);
    static void  destroy(Llm* llm);
    virtual bool load();
    virtual void response(const std::string& user_content,
                          std::ostream* os, const char* end_with,
                          int max_new_tokens);
    virtual void generate_init(std::ostream* os, const char* end_with);
    void generate(int max_token);
    bool stoped();
    void reset();
    bool set_config(const std::string& content);
};
}}
```

`LlmContext` (same header) exposes `prefill_us`, `decode_us`, `sample_us`,
`ttfa_us`, `history_tokens`, `output_tokens`, `generate_str`, and a `LlmStatus`
(`NOT_LOADED`, `RUNNING`, `NORMAL_FINISHED`, `MAX_TOKENS_FINISHED`,
`USER_CANCEL`, `INTERNAL_ERROR`, `TIMEOUT`) — everything needed to replace the
fabricated timing that used to be emitted for this branch.

Streaming works by passing a custom `std::ostream` to `response()`, so a
`std::streambuf` subclass is the JNI delta-sink.

## To enable it

1. Build MNN with the LLM runtime for Android, or obtain a prebuilt bundle that
   includes it:

   ```bash
   git clone --recursive https://github.com/alibaba/MNN
   cd MNN && ./project/android/Android.mk MNN_LLM=ON
   # or, in this project's CMakeLists.txt:
   #   set(MNN_LLM ON CACHE BOOL "" FORCE)
   #   add_subdirectory(third_party/MNN)
   ```

2. Drop the resulting `libMNN.so` (and `libMNN_ext.so` if present) into
   `app/src/main/jniLibs/<abi>/`.

3. Copy `transformers/llm/engine/include/` plus `include/` into
   `app/src/main/cpp/mnn/include/`.

4. Write `app/src/main/cpp/mnn-jni.cpp` against the signatures above, exposing
   `nativeCreate / nativeLoad / nativeGenerate / nativeCancel / nativeDestroy`.

5. Replace the `MNN_LLM` branch in `LocalInferenceEngine.kt` with a call to that
   binding, and delete `AlibabaMnnEngine.kt`'s `delay()`-driven stand-in.

Until then the branch emits an explicit unavailable result. It deliberately does
**not** fall back to synthesized text.
