## Summary

What does this PR do and why? Link any related issue (`Closes #…`).

## Changes

-

## Checklist

- [ ] Follows Conventional Commits and is scoped to one logical change
- [ ] `./gradlew ktlintCheck detekt` passes
- [ ] `./gradlew test testDebugUnitTest` passes
- [ ] Added/updated tests for changed logic
- [ ] No secrets committed (keystores, tokens, `local.properties`)

## ⚠️ Safety / protocol

- [ ] This PR touches an **audio output path** (limiter, gain, tone generation,
      real-time pipeline). If checked: safety tests updated and output stays within
      `SafetyConstants` limits (see docs/SAFETY.md).
- [ ] This PR touches **AirPods protocol** code. If checked: any unverified
      behaviour is marked `UNVERIFIED` and backed by a capture or on-device test
      (see docs/PROTOCOL.md).
