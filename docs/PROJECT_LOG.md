# Project log

A chronological record of how OpenHearing was built, the decisions made, and what
is verified vs. not — so the full context survives across sessions. High-level
orientation is in [CLAUDE.md](../CLAUDE.md).

## Origin & mission

Apple ships a hearing screening + hearing-aid/transparency mode on AirPods Pro 2/3,
locked to its own platforms. OpenHearing brings equivalent **hearing assistance**
to Android, working with any earbuds, with best-effort AirPods support. It is a
sound-amplification tool, **not a medical device** (no "diagnose/treat/medical").

## Decisions (confirmed with the maintainer)

- **License** GPLv3 (matches the LibrePods/CAPod ecosystem; F-Droid friendly).
- **Stack** Kotlin · Compose/Material 3 · MVVM + Hilt · coroutines/Flow ·
  Gradle KTS + version catalog · JUnit5/Turbine/Robolectric · ktlint + detekt.
  minSdk 26, compile/target SDK 35, JDK 17.
- **DSP = pure Kotlin** behind I/O interfaces, so safety/signal logic is JVM-tested.
- **Namespace** `app.openhearing`.
- **Earbud-agnostic first**; AirPods is an `UNVERIFIED` enhancement, never required.
- **Fitting = half-gain rule** for v1 (NAL-NL2 deferred until calibration + WDRC);
  see [FITTING.md](FITTING.md).
- **Assist is mono** in v1.

## Toolchain (local only; not committed)

Empty machine at start: installed Temurin JDK 17, Android cmdline-tools (+ platform
35, build-tools 35.0.0) at `/usr/local/share/android-commandlinetools`, and the
Gradle wrapper pinned to **8.11.1** (system Gradle 9.6.1 is too new for AGP 8.7.3).
Hilt later bumped 2.52 → **2.56.2** (2.52 couldn't read DataStore Kotlin metadata).
Maintainer's machine disk is near-full; freed regenerable `~/.gradle/caches`.

## Phase-by-phase

### Phase 0 — Scaffold ✅
Six-module Gradle project (`:app`, `:core-common`, `:core-audiogram`, `:core-audio`,
`:airpods-protocol`, `:data`), version catalog, wrapper, ktlint/detekt, GitHub
Actions CI (build + test + lint), full docs set, GPLv3 LICENSE, issue/PR templates.
Safety seeded day one: `SafetyConstants` + tested `HardCeilingLimiter`. Disclaimer
UI. Pushed; repo configured (description + topics).

### Phase 1 — Audiogram engine ✅
`HughsonWestlakeStaircase` (down-10/up-5, 2-of-3 ascending criterion),
`PureToneScreening` (both ears × 6 frequencies), `Audiogram` model, half-gain
`FittingStrategy`/`GainCurve`. `ToneGenerator` (raised-cosine ramps, amplitude
clamp), `ToneLevel` (uncalibrated mapping), `TonePlayer` (AudioTrack + limiter).
Debug screen (`HearingTestViewModel`/`HearingTestScreen`) runs on phone speaker /
any headset. Simulated-listener tests prove staircase convergence. docs/FITTING.md
+ docs/DEVICE_TESTING.md.

### Phase 2 — Real-time assist ✅
Pure-Kotlin `dsp/`: `Biquad` peaking EQ → `GainEqualizer`, `Wdrc` broadband
compression, `FeedbackGuard` (autocorrelation-based howl detect + duck),
`LookaheadLimiter` (smooth limiting + hard brick-wall backstop), composed in
`HearingAssistChain`. **Limiter safety suite** (steady overload, transients,
sustained full-scale, runaway ramp, garbage, NaN/∞) = the release gate.
`AndroidAudioEngine` (AudioRecord→process→AudioTrack, urgent-audio thread),
`AssistController` + foreground-microphone `AssistService`, RECORD_AUDIO/foreground
permissions. **Not yet run on a device.**

### Phase 4 — Usable app ✅
DataStore `SettingsRepository` (consent, high-contrast, comfort ceiling) +
`ProfileRepository` (single active profile); `AudiogramCodec` (compact, tested).
Consent/onboarding gate; the screening saves its result as the active profile.
`AssistViewModel`/`AssistScreen` (mic-permission flow, amplification slider,
instant stop) builds a mono gain curve from the saved profile. Settings + high-
contrast theme + accessibility (large targets, semantics, scalable type).

### Calibration ✅ (proxy)
"Comfort calibration": preview a 1 kHz tone, set the maximum comfortable loudness;
that value becomes the assist limiter ceiling. Subjective but safe; true dB SPL
needs a sound-level meter. docs/CALIBRATION.md.

### Phase 5 — Release readiness ✅
R8 release build (minify + resource shrink); signing from a gitignored
`keystore.properties` (unsigned if absent). docs/PRIVACY.md (no data collected,
on-device only), docs/RELEASE.md (signed-build steps, distribution, release gates),
F-Droid fastlane metadata. Verified `assembleRelease` produces a shrunk APK.

### Phase 3 — AirPods ❌ NOT STARTED
The reverse-engineered (LibrePods/CAPod) protocol is `UNVERIFIED`. `:airpods-protocol`
ships interfaces/models only. Needs real BLE/HCI capture on hardware and the
item-by-item verification checklist in [PROTOCOL.md](PROTOCOL.md). It may turn out
some AirPods features can't be driven from Android without firmware access — which
is exactly why the app is earbud-agnostic first.

## Verified vs. NOT verified

- **Verified (local):** all pure logic — staircase, fitting, codec, DSP, and the
  limiter invariant — via 69 JVM unit tests; debug + release APKs compile; lint clean.
- **NOT verified:** anything requiring hardware — actual audio playback/capture,
  assist latency, feedback behaviour with real earbuds, stereo routing, true loudness
  in dB SPL, and the entire AirPods protocol.

## Distribution posture

Alpha: sideload for testers. Public path is F-Droid first (GPLv3 FOSS fit), then
maybe Play with careful non-medical framing — only after the two release gates
(on-device validation; calibration + legal) are cleared. See RELEASE.md.

## Commit history (this work)

`be5e7c0` issue-template links → `5a46572` Phase 0 scaffold → `76eb3c9` Phase 1 →
`96e3da8` Phase 2 → `79f1e39` Phase 4 + calibration → `6aa69b8` Phase 5.
All authored solely by the maintainer (no co-author), Conventional Commits.
