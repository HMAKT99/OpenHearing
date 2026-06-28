# Architecture

OpenHearing is a multi-module Kotlin/Android app following MVVM + clean layering.
The guiding principle: **the safety-critical and signal-processing logic lives in
pure-Kotlin/JVM modules, decoupled from Android audio/BLE I/O, so it can be
exhaustively unit-tested without a device or emulator.**

## Modules and dependency direction

```
                 ┌─────────────┐
                 │     :app     │  Compose UI · Hilt · navigation · onboarding
                 └──────┬──────┘
        ┌───────┬───────┼────────┬─────────────┐
        ▼       ▼       ▼        ▼             ▼
 :core-audiogram :core-audio :airpods-protocol :data
        │       │       │        │             │
        └───────┴───────┴────────┴─────────────┘
                        ▼
                  :core-common   units · SAFETY constants
```

- `:app` depends on the cores; **no core depends back on `:app`.**
- Everything depends on `:core-common`; `:core-common` depends on nothing app-specific.
- `:core-common` and `:core-audiogram` are plain Kotlin/JVM modules (fast JUnit5 tests).
- `:core-audio`, `:airpods-protocol`, `:data` are Android library modules (they
  touch Android audio/BLE/persistence APIs) but keep their core logic pure where possible.

### `:core-common`
Strongly-typed units (`Hertz`, `DecibelsHl`, `DecibelsSpl`, `DecibelsFs`, `Ear`)
and **`SafetyConstants`** — the single source of truth for every output-loudness
limit. Anything that produces sound must respect these.

### `:core-audiogram`
The audiogram domain: the `Audiogram`/`Threshold` model, the pure-tone
threshold-seeking staircase (Phase 1), and audiogram→gain-curve fitting (Phase 1).
Pure Kotlin — no Android dependency.

### `:core-audio`
The real-time DSP core: multiband gain, wide dynamic range compression (WDRC),
feedback/howl guard, and the **SAFETY-CRITICAL output limiter**. The DSP math is
pure Kotlin behind the `AudioEngine`/`AudioProcessor` interfaces; the concrete
AAudio/Oboe engine (Phase 2) is just the I/O shell. This is what makes the limiter
unit-testable.

### `:airpods-protocol`
AirPods Pro 2/3 detection, battery/state, and transparency routing over BLE /
L2CAP CoC. The protocol is **reverse-engineered and UNVERIFIED** (see
[docs/PROTOCOL.md](docs/PROTOCOL.md)); everything protocol-specific is behind
interfaces. Non-root path first.

### `:data`
Persistence for audiograms, profiles, and settings (DataStore/Room, Phase 4).

## Data flow

```
 Pure-tone screening ─► Audiogram ─► Gain curve / fitting ─► DSP chain ─► AudioEngine (AAudio/Oboe) ─► earbuds
 (:core-audiogram)      (:core-     (:core-audiogram)        (:core-      (:core-audio)
                         audiogram)                           audio)            │
                                                                                ▼
                                                          (optional, best-effort) :airpods-protocol
                                                          tunes transparency/route — never required
```

The DSP chain's **final stage is always an `OutputLimiter`**, so nothing can
exceed the safety ceiling on the way to the device, regardless of upstream gain.

## Testing strategy

- **Pure-Kotlin modules** (`:core-common`, `:core-audiogram`): JUnit5 unit tests,
  no Android. This is where the screening, fitting, and safety-math tests live.
- **Android library modules**: JUnit5 unit tests (via the `android-junit5` plugin)
  for pure logic; Robolectric/instrumented tests for Android-touching code.
- **`:airpods-protocol`**: can only be partially unit-tested; the protocol itself
  is validated on real hardware using the scripts in `docs/PROTOCOL.md`.

## Tech stack

Kotlin · Jetpack Compose + Material 3 · MVVM + clean layering · Hilt · coroutines/Flow ·
AAudio/Oboe behind an interface · Gradle Kotlin DSL + version catalog · JUnit5 +
Turbine + Robolectric · detekt + ktlint. minSdk 26, compile/target SDK 35.
