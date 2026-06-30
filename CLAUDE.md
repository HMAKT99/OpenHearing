# CLAUDE.md — OpenHearing project context

> This file is auto-loaded each session. It is the end-to-end orientation for
> anyone (human or AI) picking up this repo. Full chronological history is in
> [docs/PROJECT_LOG.md](docs/PROJECT_LOG.md).

## What this is

**OpenHearing** — a free, open-source Android app bringing AirPods Pro 2/3-style
hearing assistance to Android: (1) a pure-tone hearing **screening** → audiogram,
(2) turn it into an amplification/EQ **profile**, (3) **assist** mode amplifies
quiet sound (speech) in real time on any earbuds.

**Framing is load-bearing:** this is a *hearing-assistance / sound-amplification
tool, NOT a medical device*. Never use "diagnose / treat / medical" in user-facing
copy. Every health surface carries the disclaimer (README, onboarding, before any
test). Hearing safety is non-negotiable — see [docs/SAFETY.md](docs/SAFETY.md).

- **Repo:** https://github.com/HMAKT99/OpenHearing (owner `HMAKT99`, branch `main`)
- **License:** GPLv3 · **Author:** Arun Kumar Thiagarajan <arunkt.bm14@gmail.com>

## Current status (2026-06-29)

Phases 0,1,2,4,5 are **built, unit-tested, pushed**. The app is **alpha — ready
for hardware testing, NOT ready to ship to consumers.**

| Phase | State |
|---|---|
| 0 Scaffold (modules, CI, docs, license) | ✅ done |
| 1 Audiogram engine (staircase, fitting) + debug screen | ✅ done |
| 2 Real-time assist DSP + Android engine + service | ✅ done |
| 3 AirPods protocol | ❌ **not started** — UNVERIFIED, [docs/PROTOCOL.md](docs/PROTOCOL.md) |
| 4 Persistence, onboarding, assist UI, accessibility | ✅ done |
| 5 Release build, signing, privacy, F-Droid metadata | ✅ done |
| Calibration | ✅ comfort-ceiling proxy; true dB SPL needs a meter |

**69 unit tests green; debug + release (R8) APKs build.**

### TWO GATES before any consumer release (cannot be closed in code)
1. **On-device safety validation** — no audio has ever run on a real device. Assist
   mode (live mic, feedback guard, real earbuds, latency) and the limiter must be
   verified on hardware per [docs/DEVICE_TESTING.md](docs/DEVICE_TESTING.md).
2. **Real calibration + legal/regulatory** — comfort calibration is a safe proxy,
   not dB-SPL truth; medical-device framing/jurisdiction needs the maintainer's
   judgement (no legal advice from here).

## Key decisions (don't re-litigate without reason)

- **Kotlin, Compose/Material 3, MVVM + Hilt, coroutines/Flow.** minSdk 26,
  compile/target SDK 35, JDK 17.
- **DSP is pure Kotlin** behind I/O interfaces (AAudio/AudioTrack/AudioRecord is a
  thin shell) so the safety-critical and signal logic is JVM-unit-tested.
- **Namespace / applicationId:** `app.openhearing`.
- **Fitting = half-gain rule** for v1 (uncalibrated + no per-band yet); NAL-NL2
  slots behind `FittingStrategy` later — see [docs/FITTING.md](docs/FITTING.md).
- **Earbud-agnostic first.** Works fully on any headset; AirPods is a best-effort,
  clearly-`UNVERIFIED` enhancement, never a hard dependency.
- **Assist is mono in v1** (mic is mono); per-ear stereo is future work.

## Module map (see [ARCHITECTURE.md](ARCHITECTURE.md))

- `:core-common` — units (`Hertz`, `DecibelsHl/Spl/Fs`, `Ear`) + **`SafetyConstants`**
  (single source of truth for output limits).
- `:core-audiogram` (pure JVM) — `Audiogram`, `HughsonWestlakeStaircase`,
  `PureToneScreening`, `GainCurve`/`FittingStrategy`/`FractionalGainRule`, `AudiogramCodec`.
- `:core-audio` (Android lib) — `dsp/`: `Biquad`, `GainEqualizer`, `Wdrc`,
  `FeedbackGuard`, `LookaheadLimiter`, `HearingAssistChain`; `ToneGenerator`,
  `TonePlayer`, `AndroidAudioEngine`, `OutputLimiter`.
- `:airpods-protocol` (Android lib) — `AirPodsController` interface, all `UNVERIFIED`.
- `:data` (Android lib) — `SettingsRepository` + `ProfileRepository` (DataStore).
- `:app` — Compose UI (`OpenHearingApp` nav, onboarding gate, hearingtest/, assist/,
  settings), Hilt modules (`di/`), `assist/AssistController` + `AssistService`.

Data flow: screening → `Audiogram` → `FittingStrategy` → `GainCurve` →
`HearingAssistChain` (EQ→WDRC→feedback guard→master gain→**limiter**) →
`AndroidAudioEngine`. The limiter is ALWAYS the final stage.

## Safety invariants (treat violations as critical bugs)

- Nothing reaches the device without passing an `OutputLimiter`
  (`LookaheadLimiter` = smooth limiting + hard brick-wall backstop). Its safety
  test suite (`LookaheadLimiterSafetyTest`) is the **Phase 2 release gate**.
- All limits live in `SafetyConstants` (`:core-common`); never redefine them.
- Tones ramp (raised-cosine, ≥ `MIN_TONE_RAMP_MS`); amplitude is clamped; master
  gain is capped; instant mute/stop is always on screen; fail **quiet**, never loud.

## Build / verify (toolchain is NOT committed — local only)

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
# Android SDK at /usr/local/share/android-commandlinetools (platform 35, build-tools 35.0.0)
./gradlew test testDebugUnitTest ktlintCheck detekt assembleDebug   # full check
./gradlew assembleRelease                                           # R8; signs if keystore.properties present
```
- Use the **wrapper** (Gradle 8.11.1). System `gradle` is 9.6.1 — too new for AGP 8.7.3.
- **Hilt is 2.56.2** (2.52 couldn't read DataStore's Kotlin metadata).
- ktlint auto-fix: `./gradlew ktlintFormat`. Detekt config: `config/detekt/detekt.yml`.
- Machine disk runs near-full; safe to clear `~/.gradle/caches/build-cache-1` etc.

## Working agreement (the maintainer's rules — follow these)

- **Never add Claude / Claude Code as a commit co-author.** Sole author is the maintainer.
- **Local changes first. Run tests before every push, and ASK permission before pushing**
  (and before `git init`/first commits). Conventional Commits, small + reviewable.
- Build in **phases**; after each, stop, summarize, run tests, wait.
- Be honest about uncertainty — especially the AirPods protocol and anything
  needing real hardware/calibration/legal review. Don't invent packet formats.

## What's next

- **Run the Phase 1/2 device tests** ([docs/DEVICE_TESTING.md](docs/DEVICE_TESTING.md))
  on a real phone; fix whatever they surface.
- **Phase 3 — AirPods**: capture BLE/HCI logs, verify the LibrePods-documented
  protocol item by item ([docs/PROTOCOL.md](docs/PROTOCOL.md) checklist).
- Per-ear stereo assist; multi-band WDRC; true calibration; consumer-release gates.

## Doc index

[ARCHITECTURE](ARCHITECTURE.md) · [SAFETY](docs/SAFETY.md) · [FITTING](docs/FITTING.md) ·
[CALIBRATION](docs/CALIBRATION.md) · [PROTOCOL](docs/PROTOCOL.md) ·
[DEVICE_TESTING](docs/DEVICE_TESTING.md) · [RELEASE](docs/RELEASE.md) ·
[PRIVACY](docs/PRIVACY.md) · [PROJECT_LOG](docs/PROJECT_LOG.md) ·
[CONTRIBUTING](CONTRIBUTING.md)
