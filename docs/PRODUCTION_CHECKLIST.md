# Production Readiness Checklist

This checklist tracks the hardening work required before shipping a release
build of EdgeLLM-Studio. An item is only checked when it is verified in the
repository, not merely planned.

## Build & CI

- [x] Kotlin compile gate (`compileCpuDebugKotlin`) passes.
- [x] CI workflow uses the restored `cpu` / `vulkan` flavors
  (`assembleCpuDebug`, `assembleCpuRelease`).
- [x] R8 minification and resource shrinking enabled for release.
- [x] `versionCode` derived from the CI run number; `versionName` from the build.
- [x] CI verifies debug/release builds, lint, ktlint, and unit tests.
- [x] Release job builds both an APK and an AAB (`bundleRelease`).
- [x] Release job rejects APKs signed with the Android debug certificate.
- [x] CI rejects committed signing material and common hardcoded-secret patterns.
- [ ] Signed release APK/AAB produced by CI (requires signing secrets).
- [ ] Full `assembleCpuDebug` / `assembleCpuRelease` verified on a glibc/Android
  toolchain (the local sandbox uses musl).

## Secrets & Configuration

- [x] No hardcoded Supabase URL / anon key.
- [x] No hardcoded Stripe publishable key.
- [x] No hardcoded user id / email / billing allocation.
- [x] Signing material (`*.jks`, `*.keystore`, …) ignored and removed from VCS.
- [x] `BuildConfig` kept by ProGuard where reflection/serialization needs it.
- [ ] Remote config keys provisioned through build secrets for release.

## Network

- [x] Cleartext HTTP denied app-wide except loopback.
- [x] Model downloads restricted to HTTPS.
- [x] Ollama CORS disabled by default.
- [x] Model catalog placeholder checksums removed (blank = unverified).

## Agent & Shell Security

- [x] `RESTRICTED` tools are default-deny and require explicit approval.
- [x] Approval dialog shows tool name, description, and arguments.
- [x] Raw shell commands require explicit approval before execution.
- [x] Shell executor enforces a timeout and terminates hung processes.
- [x] Shell executor drains stdout/stderr concurrently (no pipe deadlock).
- [x] Destructive command patterns blocked by the sandbox.
- [ ] Per-tool argument validation / allow-listing reviewed.
- [ ] Network egress policy for agent tools documented and tested.

## Data Integrity

- [x] No simulated downloads or fake progress; model capability cards are
  labelled as catalog estimates rather than on-device measurements.
- [x] Token rate measured from real emission timing.
- [x] Device metrics read from `/proc/stat`, thermal sysfs, and BatteryManager.
- [ ] End-to-end checksum verification for all downloadable models.
- [x] `allowBackup=false` and backup/DTR extraction rules exclude app data.

## Testing

- [x] Unit/instrumentation scaffolding for the inference flow.
- [x] Coverage for the approval gate (approve / deny paths).
- [x] Coverage for the shell timeout, blocklist, and output paths.
- [ ] Physical-device coverage for GGUF, ONNX, MediaPipe, Vulkan, and AICore.
