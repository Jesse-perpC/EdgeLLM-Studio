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

---

## Step-by-step build guide

### Prerequisites

- Android NDK r27b or later (download from [Android developer site](https://developer.android.com/ndk/downloads))
- CMake 3.22.1+
- Python 3 (for some MNN build scripts)
- About 15 GB of disk space for the build

### Step 1: Clone MNN

```bash
git clone --recursive https://github.com/alibaba/MNN.git ~/MNN
cd ~/MNN
git checkout master  # or a specific release tag
git submodule update --init --recursive
```

### Step 2: Build with LLM runtime enabled

The key is `MNN_LLM=ON`. Without it, you get the computer-vision runtime, not the LLM engine:

```bash
ANDROID_NDK=/path/to/android-ndk-r27b
ANDROID_SDK=/path/to/android-sdk

cmake -B build-android \
  -DCMAKE_SYSTEM_NAME=Android \
  -DCMAKE_SYSTEM_VERSION=24 \
  -DCMAKE_ANDROID_NDK=$ANDROID_NDK \
  -DCMAKE_ANDROID_ARCH_ABI=arm64-v8a \
  -DCMAKE_ANDROID_STL_TYPE=c++_static \
  -DCMAKE_BUILD_TYPE=Release \
  -DMNN_LLM=ON \
  -DMNN_OPENCL=ON \
  -DMNN_VULKAN=ON \
  -DMNN_BUILD_CONVERTER=OFF \
  -DMNN_BUILD_DEMO=OFF \
  -DMNN_BUILD_TRAIN=OFF \
  -DMNN_BUILD_TOOLS=OFF \
  -DMNN_BUILD_QUANTOOLS=OFF \
  -DMNN_USE_LOG_HERMES=OFF

cmake --build build-android --parallel $(nproc)
```

For `armeabi-v7a`, `x86`, and `x86_64`, repeat with the appropriate `CMAKE_ANDROID_ARCH_ABI`. Or use the multi-ABI approach:

```bash
cmake -B build-android-multi \
  -DCMAKE_SYSTEM_NAME=Android \
  -DCMAKE_SYSTEM_VERSION=24 \
  -DCMAKE_ANDROID_NDK=$ANDROID_NDK \
  -DCMAKE_ANDROID_ARCH_ABIS="arm64-v8a;armeabi-v7a;x86;x86_64" \
  -DCMAKE_ANDROID_STL_TYPE=c++_static \
  -DCMAKE_BUILD_TYPE=Release \
  -DMNN_LLM=ON \
  -DMNN_OPENCL=ON \
  -DMNN_VULKAN=ON \
  -DMNN_BUILD_CONVERTER=OFF \
  -DMNN_BUILD_DEMO=OFF \
  -DMNN_BUILD_TRAIN=OFF \
  -DMNN_BUILD_TOOLS=OFF \
  -DMNN_BUILD_QUANTOOLS=OFF

cmake --build build-android-multi --parallel $(nproc)
```

### Step 3: Collect the output libraries

After a successful build, you'll find:

| Library | Purpose |
|---|---|
| `libMNN.so` | Core MNN runtime |
| `libMNN_Express.so` | Express layer (shape inference, etc.) |
| `libMNN_LLM.so` | **LLM runtime — required** |
| `libMNN_Vulkan.so` | Vulkan backend (if enabled) |
| `libMNN_OpenCL.so` | OpenCL backend (if enabled) |

Only `libMNN.so` and `libMNN_LLM.so` are strictly required for basic LLM inference. The GPU backends are optional but recommended for performance.

### Step 4: Bundle into EdgeLLM-Studio

Copy the libraries into the project's `jniLibs` directory:

```bash
PROJECT_ROOT=/path/to/EdgeLLM-Studio
DST="$PROJECT_ROOT/app/src/main/jniLibs"

# arm64-v8a
mkdir -p "$DST/arm64-v8a"
cp ~/MNN/build-android-multi/libs/arm64-v8a/libMNN.so "$DST/arm64-v8a/"
cp ~/MNN/build-android-multi/libs/arm64-v8a/libMNN_LLM.so "$DST/arm64-v8a/"
cp ~/MNN/build-android-multi/libs/arm64-v8a/libMNN_Vulkan.so "$DST/arm64-v8a/" 2>/dev/null || true
cp ~/MNN/build-android-multi/libs/arm64-v8a/libMNN_OpenCL.so "$DST/arm64-v8a/" 2>/dev/null || true
cp ~/MNN/build-android-multi/libs/arm64-v8a/libMNN_Express.so "$DST/arm64-v8a/" 2>/dev/null || true
```

Repeat for other ABIs as needed. The app's `CMakeLists.txt` should already reference these via `jniLibs.srcDirs`.

### Step 5: Write the JNI bridge

The Kotlin code in `AlibabaMnnEngine.kt` declares these native methods:

```kotlin
private external fun nativeCreateSession(modelPath: String, threads: Int, useVulkan: Boolean): Long
private external fun nativeFeedPrompt(handle: Long, prompt: String)
private external fun nativeFetchNextToken(handle: Long): String?  // null == EOS
private external fun nativeReset(handle: Long)
private external fun nativeDestroy(handle: Long)
```

Create `app/src/main/cpp/mnn/mnn_llm_jni.cpp` implementing these. The C++ side should:

1. Call `MNN::llm::Llm::createLLM(config_path)` to load the model
2. Call `.load()` to initialize
3. Feed prompts via the `response()` method with a custom `std::ostream` sink
4. Return tokens one at a time through `nativeFetchNextToken`
5. Clean up with `destroy()` in `nativeDestroy`

See the `LlmContext` API in `transformers/llm/engine/include/llm/llm.hpp` for the full signature reference.

### Step 6: Update LocalInferenceEngine.kt

Replace the current `MNN_LLM` branch (which returns Unavailable) with a call to `MnnEngine.streamMnnResponse(...)`. Then delete the `delay()`-based stub in `AlibabaMnnEngine.kt`.

### Step 7: Model files

MNN-LLM models are stored as a directory containing:
- `data` — the quantized weight file
- `config.json` — model configuration

Download compatible models from the [MNN model hub](https://modelscope.cn/models) or convert GGUF models using MNN's converter tool (`tools/converter`). The converter is built when `MNN_BUILD_TOOLS=ON`.

---

## Troubleshooting

- **`UnsatisfiedLinkError` on startup**: The `.so` files aren't in `jniLibs` for the device's ABI. Check `app/src/main/jniLibs/` and verify the ABI matches your device (`adb shell getprop ro.product.cpu.abi`).
- **Model fails to load**: Ensure the `config.json` is present and the `data` file matches the config. Use the MNN converter to verify: `python tools/converter/cli.py --modelFile=model.mnn --format=MNN --extractType=0`.
- **Vulkan errors**: Not all Android devices support the Vulkan LLM backend. Set `useVulkan=false` in `AlibabaMnnEngine.kt` to fall back to CPU.
