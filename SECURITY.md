# Security Policy

## Supported versions

EdgeLLM Studio is currently in **beta**. Only the latest `main` commit and
the latest `v*` tag receive security fixes. There is no LTS yet.

## Reporting a vulnerability

- Open a **private** report via GitHub Security Advisories for this repo, or
  contact the maintainer directly. Do not open public issues for exploits.
- Include: affected commit/tag, flavor (`cpu`/`vulkan`), device + Android
  version, steps to reproduce, and impact (RCE, key extraction, sandbox
  escape, auth bypass on the local HTTP server, etc.).
- Target response: acknowledgement within 72 hours; fix or mitigation plan
  within 14 days for critical issues.

## Release signing

- `v*` tag builds **require** a real release key
  (`RELEASE_KEYSTORE_BASE64` + `STORE_PASSWORD` + `KEY_ALIAS` +
  `KEY_PASSWORD`). CI refuses to publish debug-signed tag releases.
- Branch builds may use `ALLOW_DEBUG_SIGNED_RELEASE=true` for diagnostics.
  Debug-signed APKs use the publicly known Android debug key, are rejected by
  Play, and must never be distributed as releases.

## Scope notes

- The on-device HTTP server (`8080`/`11434`) defaults to loopback
  (`127.0.0.1`). LAN exposure is an explicit opt-in via the in-app LAN
  switch. Either way, use bearer-token auth, keep the token in app-private
  storage (excluded from cloud backup), rotate it regularly, and review the
  in-app audit log.
- Secrets (`.env`, `*.jks`, `debug.keystore`) are git-ignored and must never
  be committed. `.env.example` ships blank placeholders only.
