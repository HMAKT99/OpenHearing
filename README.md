# OpenHearing

**Free, open-source hearing assistance for Android.**

Apple ships a hearing screening and a hearing-aid ("Hearing Aid" / transparency)
mode on AirPods Pro 2 and Pro 3 — but locks those features to iPhone, iPad, and
Mac. There is no equivalent on Android. OpenHearing aims to change that: a fully
open hearing-assistance app that works with **any** earbuds, and does its best to
make use of AirPods Pro 2/3 where the (reverse-engineered) protocol allows.

OpenHearing does three things:

1. **Screen** — runs a pure-tone hearing screening on Android and produces a
   per-ear, per-frequency audiogram.
2. **Fit** — converts that audiogram into a real-time amplification / EQ profile.
3. **Assist** — applies that profile to live audio so quiet speech becomes
   easier to hear.

---

## ⚠️ Important: this is not a medical device

> **OpenHearing is a sound-amplification and hearing-assistance tool. It is NOT a
> medical device, NOT a certified hearing aid, and NOT a substitute for a
> professional hearing exam.** It does not diagnose or treat any condition.
>
> The built-in screening is a convenience aid, not a clinical audiogram. If you
> have any concerns about your hearing, please see an audiologist or doctor.
>
> **Hearing safety:** the app plays calibrated tones and amplifies live sound. It
> enforces a hard output-loudness ceiling, ramps tones gently, and always offers a
> master volume cap and instant mute. Even so, keep the volume at a comfortable
> level and stop immediately if you feel any discomfort.

This notice also appears in the app's onboarding and before any test begins.

---

## Project status — what's verified vs. not

OpenHearing is in **early development (Phase 0: scaffold)**. We are deliberately
explicit about what is real, what is stubbed, and what is unverified.

| Area | Status |
|---|---|
| Multi-module project skeleton, CI, docs | ✅ In place |
| Safety ceiling constants + brick-wall limiter primitive + tests | ✅ In place (full streaming limiter is a Phase 2 gate) |
| Audiogram model | ✅ In place |
| Pure-tone screening (Hughson–Westlake staircase) + audiogram→gain fitting | ✅ Phase 1 — pure-Kotlin engine, unit-tested ([fitting rationale](docs/FITTING.md)) |
| Hearing-test debug screen (runs on phone speaker / any headset) | ✅ Phase 1 — see [device testing](docs/DEVICE_TESTING.md) |
| Real-time capture→process→playback, WDRC, feedback guard | 🚧 Phase 2 |
| AirPods Pro 2/3 detection, battery, transparency routing | ❓ Phase 3 — **UNVERIFIED protocol**, see [docs/PROTOCOL.md](docs/PROTOCOL.md) |
| Onboarding, profiles, persistence, accessibility | 🚧 Phase 4 |

**On AirPods specifically:** the AirPods Bluetooth/L2CAP control protocol is
**reverse-engineered, not public.** We build on the documented work in
[LibrePods](https://github.com/kavishdevar/librepods) and CAPod. Anything we
cannot confirm is isolated behind clean interfaces, marked `UNVERIFIED`, and
paired with an on-device test. It is genuinely possible that some AirPods
features cannot be controlled from Android without firmware access — which is
exactly why **OpenHearing works fully with any earbuds first**, and treats
AirPods as a best-effort enhancement.

---

## Screenshots

_Coming soon._ <!-- TODO: add screenshots once Phase 1 UI lands -->

---

## Architecture

OpenHearing is a clean, multi-module Kotlin project. See
[ARCHITECTURE.md](ARCHITECTURE.md) for the full picture.

| Module | Responsibility |
|---|---|
| `:app` | Compose + Material 3 UI, navigation, Hilt wiring, onboarding/disclaimers |
| `:core-common` | Shared units (Hz, dB HL/SPL/FS) and the **safety constants** |
| `:core-audiogram` | Audiogram model, pure-tone staircase, audiogram→gain fitting (pure Kotlin) |
| `:core-audio` | Real-time DSP core + the **SAFETY-CRITICAL output limiter** (pure-Kotlin DSP behind an AAudio/Oboe I/O interface) |
| `:airpods-protocol` | AirPods detection/state/routing — **UNVERIFIED**, behind interfaces |
| `:data` | Persistence for audiograms, profiles, settings |

The safety-critical and DSP logic lives in pure-Kotlin/JVM modules so it is
exhaustively unit-tested with no emulator.

---

## Building

**Requirements:** JDK 17, Android SDK (API 35 / build-tools 35.0.0). Point the
build at your SDK via `local.properties` (`sdk.dir=...`) or `ANDROID_HOME`.

```bash
# Run the full check (lint + unit tests)
./gradlew ktlintCheck detekt test testDebugUnitTest

# Build the debug APK
./gradlew assembleDebug
```

CI runs the same checks on every push and pull request (see
[`.github/workflows/ci.yml`](.github/workflows/ci.yml)).

---

## Contributing

Contributions are very welcome — this project exists to help people who can't
access Apple's ecosystem. Please read [CONTRIBUTING.md](CONTRIBUTING.md) and our
[Code of Conduct](CODE_OF_CONDUCT.md). Hardware testers (AirPods Pro 2/3 + an
Android phone) are especially valuable — see [docs/PROTOCOL.md](docs/PROTOCOL.md).

For safety expectations on any audio-path change, read [docs/SAFETY.md](docs/SAFETY.md).

---

## License

OpenHearing is licensed under the **GNU General Public License v3.0** — see
[LICENSE](LICENSE). This matches the LibrePods/CAPod ecosystem we build on and
keeps the project (and its derivatives) open.

## Credits

- [LibrePods](https://github.com/kavishdevar/librepods) and CAPod for the
  reverse-engineering groundwork on the AirPods protocol.

> OpenHearing is an independent project. It is not affiliated with, endorsed by,
> or associated with Apple Inc. "AirPods" is a trademark of Apple Inc., used here
> only to describe hardware compatibility.
