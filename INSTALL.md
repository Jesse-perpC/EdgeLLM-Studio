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
- **`app/src/main/cpp/llama-android.cpp`**: JNI bridge implementing `nativeInit`, `nativeCompletion`, `nativeBenchmark`, and `nativeRelease`. Symbols follow the `com.perpcorp.edgellm` package (`..._LlamaContext_00024Companion_nativeInit` for the companion factory). Until the real `llama_decode` loop is linked (`EDGELLM_LINK_LLAMA_CPP=ON`), completion returns an explicit error marker so Kotlin falls back to the managed engine — never an empty bubble.
- **`app/src/main/cpp/CMakeLists.txt`**: Builds the `llama-android` stub (links `libandroid`/`liblog`); real llama.cpp linkage is opt-in via `EDGELLM_LINK_LLAMA_CPP`.
- **`app/src/main/java/com/perpcorp/edgellm/engine/LlamaContext.kt`**: High-level Kotlin wrapper with graceful native loading and managed fallback.

---

## 🚀 Building the Project

### Local Development (Android Studio / Gradle)
```bash
# 1. Clone the repository
git clone https://github.com/Jesse-perpC/EdgeLLM-Studio.git
cd EdgeLLM-Studio

# 2. Setup llama.cpp native submodule (optional for native C++ build)
./scripts/setup-llama-cpp.sh

# 3. Build Debug APK
./gradlew assembleDebug

# 4. Run unit and Robolectric tests
./gradlew testDebugUnitTest
```

### GitHub Actions (Automated CI/CD)
The repository includes `.github/workflows/build-apk.yml` which automatically:
- Installs Android SDK 36, NDK 27.3.13750724, and CMake 3.22.1.
- Builds both Debug and Release APKs (including the `llama-android` JNI stub).
- Publishes installable release APK artifacts on every commit and tag.
