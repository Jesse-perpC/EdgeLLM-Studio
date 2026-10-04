# EdgeLLM Studio — Installation & Native llama.android Guide

EdgeLLM Studio is an on-device private LLM and ML runtime for Android featuring the official **llama.android** native architecture, dual-engine routing, and an embedded OpenAI-compatible HTTP daemon.

---

## 🏗️ Architecture Overview

```
                  ┌──► Air-Gapped Mode ON (or no network)
                  │    └─► ROUTE TO: Local GGUF/MediaPipe/MNN/AICore engine
                  │        (strict prompt jail + temp 0.0 + unified sanitizer)
[ User Prompt ] ──┤
                  │
                  └──► Cloud Assist enabled + online
                       └─► ROUTE TO: Gemini Cloud API (optional helper only)
```

1. **Local Engines (default, offline-first)**:
   - Every format (GGUF via llama.cpp/llama.android, MediaPipe/LiteRT, ONNX, MNN, AICore) answers through the shared grounded knowledge pipeline with answer-only chat text.
   - `temperature = 0.0` greedy decoding + GBNF grammar (GGUF) or ChatML prompt jail (others) + unified post-decoder sanitizer prevent drift and hallucinations.
2. **Cloud Assist (opt-in)**:
   - Handles overflow queries only when the user enables it and the device is online.
   - Temperature 0.0–0.2 for deterministic precision.

---

## 📁 Native llama.android JNI Files

- **`app/src/main/cpp/logging.h`**: Android NDK logger macros (`LOGI`, `LOGW`, `LOGE`, `LOGD`).
- **`app/src/main/cpp/llama-android.cpp`**: Real JNI bridge — `nativeInit`, `nativeCompletion` (token-by-token `llama_decode` loop with GBNF grammar sampling), `nativeSetSampling`/`nativeSetGrammar`/`nativeSetCancelled`, `nativeGetChatTemplate`, `nativeGetStats`, `nativeListBackends`, `nativeRelease`. Symbols follow the `com.perpcorp.edgellm` package (`..._LlamaContext_00024Companion_nativeInit` for the companion factory).
- **`app/src/main/cpp/CMakeLists.txt`**: Builds `libllama-android.so` against the pinned `llama.cpp` submodule (hard build error if missing — no stub fallback). CPU (ARM NEON) always; Vulkan/OpenCL/Hexagon are opt-in CMake flags, see flavors below.
- **`app/src/main/java/com/perpcorp/edgellm/engine/LlamaContext.kt`**: Kotlin wrapper owning the native `llama_context`, with measured per-completion stats and streaming via `completionStream`.
- **`app/src/main/java/com/perpcorp/edgellm/engine/OnnxLlmEngine.kt`**: Real ONNX Runtime decoder (graph-inspected KV-cache vs full-prefix, encoder-safe). Needs the `.onnx` file plus its `tokenizer.json` sidecar.
- **`app/src/main/java/com/perpcorp/edgellm/engine/TfliteClassifierEngine.kt`**: Real TFLite BERT classifier (needs `vocab.txt`, optional `labels.txt` next to the `.tflite`).

## 🎛️ Compute Backends & APK Flavors

| Flavor | Native backend | Installs as | When to use |
|---|---|---|---|
| `cpu` (default) | ARM NEON | `com.aistudio.edgellm.qvmxrp` | Every ARM64 device. GGUF on CPU; ONNX/TFLite/MediaPipe use NNAPI delegates where present. |
| `vulkan` | ggml Vulkan + CPU fallback | `...qvmxrp.vulkan` (side-by-side) | Adreno/Mali devices with a Vulkan driver for GPU-offloaded GGUF. Falls back to CPU otherwise. |

Not shipped (and why): **Hexagon HTP** needs a licensed Hexagon SDK; **MNN-LLM** has no Maven artifact (manual steps in `scripts/setup-mnn.md`); **AICore/Gemini Nano** has no public third-party SDK — both report `Unavailable` instead of fake output.

---

## 🚀 Building the Project

### Local Development (Android Studio / Gradle)
```bash
# 1. Clone the repository
git clone https://github.com/Jesse-perpC/EdgeLLM-Studio.git
cd EdgeLLM-Studio

# 2. Setup llama.cpp native submodule (REQUIRED for the GGUF build)
git submodule update --init --depth 1 -- llama.cpp
# (or: ./scripts/setup-llama-cpp.sh)

# 3. Build Debug APKs (cpu default + vulkan GPU flavor)
./gradlew assembleCpuDebug assembleVulkanDebug

# 4. Run unit and Robolectric tests (per flavor)
./gradlew testCpuDebugUnitTest
```

### GitHub Actions (Automated CI/CD)
The repository includes `.github/workflows/build-apk.yml` which automatically:
- Installs Android SDK 36, NDK 27.3.13750724, and CMake 3.22.1.
- Builds both Debug and Release APKs (including the `llama-android` JNI stub).
- Publishes installable release APK artifacts on every commit and tag.
