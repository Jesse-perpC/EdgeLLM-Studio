# EdgeLLM Studio

EdgeLLM Studio is an **Android on-device AI runtime** for GGUF, MediaPipe/LiteRT,
ONNX, and TFLite models. It is currently **beta software**: builds and core local
inference paths work, but it is not a finished consumer product.

Local inference runs on the device. Cloud features (Gemini assist, model
downloads, Stripe checkout, Telegram/SSH bridges) are opt-in and are not part of
the offline path.

## Status

| Area | Status |
|:--|:--|
| GGUF inference via llama.cpp | Implemented; requires the `llama.cpp` submodule and a model file |
| MediaPipe / LiteRT `.task` models | Implemented through the MediaPipe Tasks GenAI AAR |
| ONNX Runtime | Implemented; model + tokenizer sidecar required |
| TFLite classifiers | Implemented; needs `vocab.txt` / optional `labels.txt` |
| Gemini Nano (AICore) | Available only when Android 14+ AI Core is present and a probe generation succeeds |
| Alibaba MNN | Not bundled; manual build required |
| Stable Diffusion / image generation | Not available on-device in this build; visible image generation uses the opt-in Gemini API |
| Local shell workspace | App-private shell directory, not a PRoot/chroot distro |
| OpenAI/Ollama-compatible local server | Implemented with bearer-token authentication |

## Build

Requirements: JDK 21, Android SDK 36, NDK `27.3.13750724`, CMake `3.22.1`.

```bash
git clone https://github.com/Jesse-perpC/EdgeLLM-Studio.git
cd EdgeLLM-Studio
git submodule update --init --depth 1 -- llama.cpp
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug ktlintCheck
unzip -l app/build/outputs/apk/debug/*.apk | grep libllama-android.so
```

The application ID is `com.aistudio.edgellm.qvmxrp`; the source namespace is
`com.perpcorp.edgellm`. The shipped APK is a single variant (no `cpu`/`vulkan`
product flavors); GGUF uses the ARM NEON backend by default. Vulkan is an opt-in
CMake flag (`EDGELLM_GPU_VULKAN`), not a separate APK. The APK must contain
`libllama-android.so`; otherwise GGUF inference reports an
`UnsatisfiedLinkError` and produces no output. See `INSTALL.md` for details.

## Release signing

Release builds are **unsigned by default**. A real upload keystore is required:

```bash
export KEYSTORE_PATH=/secure/path/release-upload-key.jks
export STORE_PASSWORD=...
export KEY_ALIAS=...
export KEY_PASSWORD=...
./gradlew assembleRelease bundleRelease
```

`ALLOW_DEBUG_SIGNED_RELEASE=true` exists only for CI dry runs and must never be
used for a published artifact. The release workflow verifies the APK signer and
refuses debug certificates. See `docs/PRODUCTION_CHECKLIST.md` for open items.

## Security model

- RESTRICTED agent tools and raw shell commands require explicit approval.
- Shell commands have a timeout, process kill, output caps, and a destructive
  command blocklist. The blocklist is defense-in-depth, not a sandbox.
- Cleartext HTTP is denied except loopback (`localhost`, `127.0.0.1`, `::1`).
- `allowBackup` is disabled; app data is not included in cloud backup or DTR.
- Release builds enable R8 shrinking and are not debuggable.
- Do not commit keystores, `.env`, or API keys.

Report vulnerabilities per `SECURITY.md`, not in a public issue.

## Known limitations

- The public `v1.0.99` release predates the current signing hardening and is
  debug-signed. Build a signed release from current `main` before distributing.
- On-device Stable Diffusion, MNN-LLM, voice cloning, and some UI benchmark
  cards are partial or unavailable. Anything labelled "estimate" comes from
  catalog metadata, not a measurement on your device.
- Model accuracy and throughput depend on the device, quantization, and model;
  no universal performance claim is made.
- Automated coverage is focused on unit-level paths. Device/GPU/NPU behavior
  still needs physical-device testing before a production rollout.

## Documentation

- `INSTALL.md` — native JNI and engine details.
- `docs/PRODUCTION_CHECKLIST.md` — release blockers and verification status.
- `SECURITY.md` — vulnerability reporting and security properties.

## License

Apache License 2.0. See `LICENSE`.
