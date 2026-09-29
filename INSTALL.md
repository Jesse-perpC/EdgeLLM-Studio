# EdgeLLM Studio — Installation & Native llama.android Guide

EdgeLLM Studio is an on-device private LLM and ML runtime for Android featuring the official **llama.android** native architecture, dual-engine routing, and an embedded OpenAI-compatible HTTP daemon.

---

## 🏗️ Architecture Overview

```
                  ┌───► User asks: "How many hours in a day?" (General Factual Trivia)
                  │     └─► ROUTE TO: Google AI Studio Cloud API (Gemini-2.5-Flash)
                  │
[ User Prompt ] ──┤
                  │
                  └───► User asks: "Run offline task" / Air-Gapped Mode Toggle is ON
                        └─► ROUTE TO: Local Llama.cpp Engine (Strict System Prompt + Temp 0.0)
```

1. **Cloud Assist (`Gemini 2.5 Flash`)**:
   - Handles general trivia, complex knowledge queries, and factual questions whenever connected to the internet.
   - Temperature configured to 0.0–0.2 for deterministic precision.
2. **Local Llama.cpp Native Engine (`temperature = 0.0`)**:
   - Built on official `llama.android` architecture (`llama-android.cpp`, `LlamaContext.kt`).
   - Runs offline tasks, JSON schema outputs, GBNF grammar decoding, local tools, and document RAG.
   - Strictly prevents model drift and hallucinations by enforcing deterministic greedy argmax sampling.

---

## 📁 Native llama.android JNI Files

- **`app/src/main/cpp/logging.h`**: Android NDK logger macros (`LOGI`, `LOGW`, `LOGE`, `LOGD`).
- **`app/src/main/cpp/llama-android.cpp`**: Official JNI bridge implementing `nativeInit`, `nativeCompletion`, `nativeBenchmark`, and `nativeRelease`.
- **`app/src/main/cpp/CMakeLists.txt`**: Standard CMake build definition linking `libandroid` and `liblog`.
- **`app/src/main/java/com/example/engine/LlamaContext.kt`**: High-level Kotlin wrapper with graceful native loading and managed fallback.

---

## 🚀 Building the Project

### Local Development (Android Studio / Gradle)
```bash
# 1. Clone the repository
git clone https://github.com/your-username/edgellm-studio.git
cd edgellm-studio

# 2. Setup llama.cpp native submodule (optional for native C++ build)
./scripts/setup-llama-cpp.sh

# 3. Build Debug APK
./gradlew assembleDebug

# 4. Run unit and Robolectric tests
./gradlew testDebugUnitTest
```

### GitHub Actions (Automated CI/CD)
The repository includes `.github/workflows/build-apk.yml` which automatically:
- Installs Android SDK 36, NDK 26.1, and CMake 3.22.1.
- Builds both Debug and Release APKs.
- Publishes installable release APK artifacts on every commit and tag.
