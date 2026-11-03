# EdgeLLM Studio — Installation & Native llama.cpp Guide

EdgeLLM Studio is an Android on-device LLM/ML runtime. GGUF inference uses a
real `llama.cpp` JNI bridge packaged in the APK as `libllama-android.so`.

## Native files

- `app/src/main/cpp/llama-android.cpp`: JNI bridge (`nativeInit`,
  `nativeCompletion`, sampling/grammar controls, chat template, stats,
  backends, release). Symbols use the `com.perpcorp.edgellm` package.
- `app/src/main/cpp/CMakeLists.txt`: builds `libllama-android.so` from the
  pinned `llama.cpp` submodule. A missing submodule is a hard CMake error.
- `app/src/main/java/com/perpcorp/edgellm/engine/LlamaContext.kt`: Kotlin
  wrapper that loads `libllama-android.so` and owns the native context.
- `app/build.gradle.kts`: wires CMake via `externalNativeBuild`.

The build is a single application (no `cpu`/`vulkan` flavors). The CPU backend
(ARM NEON) is the default. OpenCL, Vulkan, and Hexagon are opt-in CMake flags
and require their respective SDKs/toolchains; they are not part of the stock
NDK build.

## Requirements

- JDK 21
- Android SDK 36
- NDK `27.3.13750724`
- CMake `3.22.1`
- The `llama.cpp` submodule

## Local build

```bash
git clone https://github.com/Jesse-perpC/EdgeLLM-Studio.git
cd EdgeLLM-Studio
git submodule update --init --recursive
# or: bash scripts/setup-llama-cpp.sh

./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug ktlintCheck
```

Always verify the GGUF bridge is actually packaged before installing:

```bash
unzip -l app/build/outputs/apk/debug/*.apk | grep libllama-android.so
```

Expected entries include `lib/arm64-v8a/libllama-android.so` and, when built,
`lib/x86_64/libllama-android.so`. If the library is absent, GGUF inference
fails with `native decode produced no output` and logcat reports an
`UnsatisfiedLinkError`.

## Release build

Release APKs/AABs are unsigned unless real signing secrets are provided:

```bash
export KEYSTORE_PATH=/secure/path/release-upload-key.jks
export STORE_PASSWORD=...
export KEY_ALIAS=...
export KEY_PASSWORD=...
./gradlew assembleRelease bundleRelease
```

`ALLOW_DEBUG_SIGNED_RELEASE=true` is only for CI dry runs and must never be
used for a published artifact.

## CI

`.github/workflows/build-apk.yml` installs the SDK/NDK/CMake, builds debug and
release variants, runs lint, ktlint, and unit tests, and verifies that both the
debug APK and release AAB contain `libllama-android.so`. Tagged builds publish
signed release artifacts.
