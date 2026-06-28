# Security Policy

## Reporting a vulnerability

If you discover a security or **safety** issue (including any bug that could cause
the app to produce dangerously loud audio), please report it privately:

- Use **GitHub Security Advisories** ("Report a vulnerability") on this repository, or
- Open a minimal issue asking a maintainer to make private contact — **do not post
  exploit details or reproduction steps publicly** until a fix is available.

Please include the affected version/commit, the device and Android version, and
steps to reproduce.

## Scope — hearing safety is a security concern

We treat any defect that can exceed the output-loudness limits in
`SafetyConstants` (see [docs/SAFETY.md](docs/SAFETY.md)) as a **critical** issue,
on par with a traditional security vulnerability. These get priority handling.

## Supported versions

OpenHearing is pre-release (Phase 0). Until a stable release, only the latest
`main` is supported.

## No secrets in the repo

Keystores, tokens, and `local.properties` are git-ignored and must never be
committed. If you find a committed secret, report it as above.
