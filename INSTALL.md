# EdgeLLM Studio — Installation & Native llama.android Guide

EdgeLLM Studio is an on-device private LLM and ML runtime for Android featuring a real **llama.cpp** native JNI bridge, multi-engine routing (GGUF / MediaPipe / ONNX / TFLite / AICore), and an embedded OpenAI-compatible HTTP daemon.

---

## Architecture Overview

```
                  ┌──► Air-Gapped Mode ON (or no network)
                  │    └─► ROUTE TO: Local GGUF/MediaPipe/ONNX/TFLite/AICore engine
                  │        (strict prompt jail + temp 0.0 + unified sanitizer)
[ User Prompt ] ──┤
                  │
                  └──► Cloud Assist enabled + online
                       └─► ROUTE TO: Gemini Cloud API (optional helper only)
```

1. **Local Engines (default, offline-first)**:
   - Every format (GGUF via llama.cpp, MediaPipe/LiteRT, ONNX, TFLite, AICore when available) answers through the shared grounded knowledge pipeline with answer-only chat text.
   - `temperature = 0.0` greedy decoding + GBNF grammar (GGUF) or ChatML prompt jail (others) + unified post-decoder sanitizer prevent drift and hallucinations.
2. **Cloud Assist (opt-in)**:
   - Handles overflow queries only when the user enables it and the device is online.
   - Temperature 0.0–0.2 for deterministic precision.

---

## Native llama.android JNI Files

- **`app/src/main/cpp/logging.h`**: Android NDK logger macros (`LOGI`, `LOGW`, `LOGE`, `LOGD`).
- **`app/src/main/cpp/llama-android.cpp`**: Real JNI bridge — `nativeInit`, `nativeCompletion` (token-by-token `llama_decode` loop with GBNF grammar sampling), `nativeSetSampling` / `nativeSetGrammar` / `nativeSetCancelled`, `nativeGetChatTemplate`, `nativeGetStats`, `nativeListBackends`, `nativeRelease`. Symbols follow the `com.perpcorp.edgellm` package.
- **`app/src/main/cpp/CMakeLists.txt`**: Builds `libllama-android.so` against the pinned `llama.cpp` submodule. **Hard build error if the submodule is missing** — there is no stub fallback. CPU (ARM NEON) is always enabled; Vulkan / OpenCL / Hexagon are opt-in CMake flags (see below).
- **`app/src/main/java/com/perpcorp/edgellm/engine/LlamaContext.kt`**: Kotlin wrapper owning the native `llama_context`, with measured per-completion stats and streaming via `completionStream`.
- **`app/src/main/java/com/perpcorp/edgellm/engine/OnnxLlmEngine.kt`**: Real ONNX Runtime decoder. Needs the `.onnx` file plus its `tokenizer.json` sidecar.
- **`app/src/main/java/com/perpcorp/edgellm/engine/TfliteClassifierEngine.kt`**: Real TFLite BERT classifier (needs `vocab.txt`, optional `labels.txt`).

---

## Compute Backends

The shipped APK is a **single variant**. There are no Gradle product flavors.

| Backend | How it is enabled | When it is used |
|---|---|---|
| **ARM NEON (CPU)** | Always on (default) | Every ARM64 / x86_64 device. GGUF decodes on CPU. |
| **NNAPI** | Automatic where the device provides delegates | ONNX / TFLite / MediaPipe paths when the OEM exposes NNAPI. |
| **Vulkan (ggml)** | Opt-in CMake flag `EDGELLM_GPU_VULKAN=ON` at native build time | Requires Vulkan SDK (`glslc`) + SPIRV-Headers on the *build host*. Falls back to CPU at runtime if the device has no usable Vulkan driver. |
| **OpenCL** | Opt-in CMake flag `EDGELLM_GPU_OPENCL=ON` | Requires an OpenCL ICD for the target GPU (not in the NDK). |
| **Hexagon HTP** | Opt-in CMake flag `EDGELLM_NPU_HEXAGON=ON` | Requires a licensed Hexagon SDK — **not shipped**. |

**Not shipped by default (and why):**
- **Hexagon HTP** — licensed Hexagon SDK + signing certificate.
- **MNN-LLM** — no Maven artifact; see `scripts/setup-mnn.md` for a manual path.
- **AICore / Gemini Nano** — wired via the Google Generative AI client. Works only on Android 14+ devices that have the AI Core system service and a downloaded model (Pixel 8+, some flagships); otherwise reports `Unavailable`.

Package ID of the single shipped APK: `com.aistudio.edgellm.qvmxrp`.

---

## Building the Project

### Local Development (Android Studio / Gradle)

```bash
# 1. Clone the repository
git clone https://github.com/Jesse-perpC/EdgeLLM-Studio.git
cd EdgeLLM-Studio

# 2. Setup llama.cpp native submodule (REQUIRED for the GGUF build)
git submodule update --init --depth 1 -- llama.cpp
# (or: ./scripts/setup-llama-cpp.sh)

# 3. Build Debug APK (single variant — ARM NEON by default)
./gradlew assembleDebug

# 4. Build Release APK (requires a real keystore, or CI opt-in)
#    KEYSTORE_PATH / STORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD
#    or ALLOW_DEBUG_SIGNED_RELEASE=true for CI-only debug-signed artifacts
./gradlew assembleRelease

# 5. Unit tests + static analysis
./gradlew testDebugUnitTest lintDebug ktlintCheck

# 6. Confirm the GGUF JNI bridge is packaged
unzip -l app/build/outputs/apk/debug/*.apk | grep libllama-android.so
```

Optional — enable the Vulkan ggml backend at CMake time (host must have Vulkan SDK + SPIRV-Headers):

```bash
# Pass -DEDGELLM_GPU_VULKAN=ON through Gradle externalNativeBuild arguments,
# then rebuild. The same APK falls back to CPU when the device has no Vulkan driver.
```

### GitHub Actions (Automated CI/CD)

`.github/workflows/build-apk.yml` runs on every push/PR to `main` and on tags `v*`:

**Verify job** (all pushes/PRs):
- Ubuntu 24.04, JDK 21, Android SDK 36, NDK 27.3.13750724, CMake 3.22.1
- Recursive submodule checkout
- `./gradlew assembleDebug assembleRelease lintDebug ktlintCheck testDebugUnitTest`
- Rejects committed signing material and common hardcoded-secret patterns
- Verifies the debug APK contains `libllama-android.so` for `arm64-v8a` and `x86_64`
- Uploads the debug APK and lint reports as artifacts

**Release job** (tags `v*` only):
- Requires repository secrets: `RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
- Builds signed `assembleRelease` and `bundleRelease` artifacts
- Verifies with `apksigner`, rejects debug certificates, checks the AAB native libraries, and publishes a GitHub Release with APK + AAB + SHA256SUMS

There is no dual-flavor matrix in CI. One APK variant is produced per build.
