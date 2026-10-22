# Security Policy

## Reporting a Vulnerability

Do not open a public GitHub issue for security vulnerabilities. Report them
privately to the maintainers through GitHub's private vulnerability reporting
(Security tab → "Report a vulnerability") or by email if a contact is listed
on the repository profile.

Please include:

- A description of the issue and its impact.
- Steps to reproduce, or a minimal proof of concept.
- Affected version / commit hash and Android OS version.
- Any suggested mitigation.

You can expect an acknowledgement within 72 hours and a remediation timeline
after triage.

## Scope

EdgeLLM-Studio runs local inference engines and agent tooling on-device.
Areas of particular interest:

- Agent tool sandbox escape (shell, Linux workspace, SSH, Telegram bridges).
- Prompt injection that causes RESTRICTED tools to execute without approval.
- Model download integrity (checksum bypass, non-HTTPS sources).
- Secrets embedded in the app, logs, or build artifacts.
- Export/share paths that leak model data or user content.

## Security Properties

- **Default-deny tools**: every `AgentSecurityLevel.RESTRICTED` tool and every
  raw shell command requires explicit user approval before execution.
- **Cleartext traffic denied** app-wide, except loopback (`localhost`,
  `127.0.0.1`, `::1`), which is used for local model servers.
- **No bundled secrets**: Supabase/Stripe/AI credentials are read from
  configuration at runtime; the app ships no publishable/test keys.
- **HTTPS-only model downloads**; catalog checksums are only enforced when a
  real value is present (blank means "unverified", never a placeholder hash).
- **No fake telemetry**: token rates, CPU, thermal, and battery metrics are
  measured from the platform, never synthesized.

## Supported Versions

Only the latest `main` build receives security fixes.
