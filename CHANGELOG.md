# Changelog

All notable changes to EdgeLLM Studio. Format follows Keep a Changelog;
versioning is `1.0.<run_number>` from CI (`versionCode` = run number).

## [Unreleased]

### Fixed (keystone production pass)
- `BillingRepository`: removed hardcoded Supabase project URL + anon key,
  Stripe test publishable key, owner email/user-id, and fabricated usage
  counter. Fresh installs are now unconfigured/local-only (`isConfigured`
  gates all cloud sync); billing backends are opt-in via `.env`/in-app sheet.
- `InferenceServerService`: HTTP server now defaults to loopback
  (`127.0.0.1`). LAN bind is an explicit opt-in via the in-app LAN switch.
  Bearer-token provisioning still happens before any connection is accepted.
- `backup_rules.xml` / `data_extraction_rules.xml`: cloud backup now excludes
  the server bearer-token prefs, billing prefs, theme prefs, and the Room
  vault database.
- CI (`build-apk.yml`): rewritten from scratch (single robust job, YAML-validated).

### Fixed (production hardening)
- `metadata.json`: honest offline-first description; removed Stripe/WordPress
  claims and the server-side Gemini capability.
- `.env.example`: blank placeholders only; removed committed test Stripe key.
- `.gitignore`: ignore release keys (`*.jks`, `*.keystore`), `/dist-apks/`,
  `/dist-windows/`, `*.zip`.
- `gradle.properties`: disabled configuration cache to match CI
  (`--no-configuration-cache`); documented why.
- CI (`build-apk.yml`): added test gate (`testCpuDebugUnitTest`), tag-only
  GitHub Releases (no release-per-commit), tag builds require a real release
  key, fixed Vulkan header check path (`/usr/local`), added concurrency
  cancel-in-progress.
- Docs: README beta status + limitations pointer; INSTALL prerequisites and
  CI behavior aligned with reality (JDK 21, SDK 36, NDK 27.3.13750724).
- Added `LICENSE` (Apache-2.0), `SECURITY.md`, and this changelog.

### Known limitations (not yet fixed)
- No independent device-matrix benchmarks; comparison scores unverified.
- Hexagon HTP / MNN-LLM / AICore runtimes not shipped (see INSTALL.md).
- Image generation path is stub/simulated; needs real diffusion or relabeling.
- No Play release, crash reporting, or update-channel story yet.
