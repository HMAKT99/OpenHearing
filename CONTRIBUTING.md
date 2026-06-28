# Contributing to OpenHearing

Thank you for helping build open hearing assistance for Android! This project is
for people who can't access Apple's ecosystem, so accessibility, correctness, and
**safety** matter more than shipping fast.

## Ways to help

- **Code** — pick up an issue, especially ones labelled `good first issue`.
- **Hardware testing** — if you have AirPods Pro 2/3 + an Android phone, you can
  help verify the (currently UNVERIFIED) protocol. See
  [docs/PROTOCOL.md](docs/PROTOCOL.md) for capture instructions.
- **Accessibility** — this app is for people with hearing difficulty; feedback on
  large text, contrast, and TalkBack is invaluable.
- **Docs, triage, design** — all welcome.

## Development setup

**Requirements:** JDK 17, Android SDK (API 35, build-tools 35.0.0). Point the
build at your SDK via `local.properties` (`sdk.dir=...`) or `ANDROID_HOME`.

```bash
./gradlew ktlintCheck detekt            # lint + static analysis
./gradlew test testDebugUnitTest        # unit tests
./gradlew assembleDebug                 # build the app
```

Run the same checks CI runs before opening a PR; they must be green.

## Code style

- Kotlin official style, enforced by **ktlint** (`./gradlew ktlintFormat` to
  auto-fix) and **detekt**. Config lives in `.editorconfig` and `config/detekt/`.
- Keep the layering clean: `:app` depends on cores; cores never depend on `:app`.
  Keep DSP/screening logic in the pure-Kotlin modules so it stays testable.
- Match the style of surrounding code.

## Commits & PRs

- **Conventional Commits** (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`,
  `chore:`, `ci:`). Keep commits small and reviewable.
- One logical change per PR; describe what and why. Link the issue.
- Add/extend tests for any logic you change.
- **Never commit secrets** (keystores, tokens, `local.properties`). They are
  git-ignored — keep it that way.

## ⚠️ Safety-critical changes

Any change to an **audio output path** (the limiter, gain, tone generation,
real-time pipeline) is safety-critical. Such PRs must:

- keep/extend the `OutputLimiter` safety tests (see [docs/SAFETY.md](docs/SAFETY.md)),
- never allow output above `SafetyConstants` limits,
- be explicitly flagged in the PR description so they get extra review.

For **protocol** work, never present a guessed packet format as fact — mark it
`UNVERIFIED` and attach a capture or a concrete on-device test (see
[docs/PROTOCOL.md](docs/PROTOCOL.md)).

## License

By contributing, you agree your contributions are licensed under the project's
**GPLv3** license (see [LICENSE](LICENSE)).
