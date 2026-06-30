# Device testing

Some parts of OpenHearing can only be validated on a real phone (audio output,
and later BLE/AirPods). This is the maintainer's hands-on checklist. Automated
unit tests cover the pure logic; this covers what they can't.

## Validation status

### ✅ Emulator smoke test — assist mode (2026-06-30, signed release / R8 build)

Ran the full flow on a headless Android 15 (API 35) emulator with the **signed
release (R8-minified) APK**:

- Install → onboarding → screening completed (profile saved) → assist screen →
  **Start assist** → ran ~8 s → **Stop**.
- **No crashes** (process stayed alive through start and stop); UI reached the
  running state ("Stop assist" shown).
- **Foreground microphone service started cleanly** (`Background started FGS:
  Allowed … AssistService … targetSdkVersion:35`).
- Audio path engaged: `AudioFlinger … thread ready to run` + active mixing for the
  duration. **No `AudioRecord`/`AudioTrack` errors** — mic capture
  (`VOICE_COMMUNICATION`, mono, PCM-float) and playback both initialized.

**What this proves:** the real-time pipeline (AudioRecord → DSP chain → AudioTrack,
inside the foreground service) **initializes and runs on Android without crashing**,
including in the minified release build.

**What it does NOT prove (not yet verified on a physical device):**
- Audio *quality* — the emulator mic feeds silence, so this confirms the pipeline
  runs/plays, not that real speech is made clearer.
- *Loudness safety* on real ears and real earbuds.
- *Latency* — the emulator **denied the low-latency "fast" output path**
  (`createTrack_l(): mismatch between requested flags (00000004) and output flags
  (00000002)` → fell back to the normal output). Expected on an emulator; **confirm
  real-device latency** with a fast-mixer-capable device.
- Feedback/howl behavior with real acoustic mic↔speaker coupling; AirPods.

## Prerequisites

- Android phone (API 26+), USB debugging on, `adb` available.
- A wired or Bluetooth headset (AirPods **not** required for Phase 1).
- Build the debug APK: `./gradlew assembleDebug`.

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Phase 1 — hearing screening debug screen

Goal: exercise the pure-tone screening engine end to end through the phone
speaker or any headset, and sanity-check the audiogram + half-gain output.

> ⚠️ Use a headset in a quiet room and keep the volume comfortable. Start with the
> in-app **Volume cap** slider low and raise it gradually. **Stop / mute** is
> always on screen — use it if anything is too loud.

### Steps

1. Launch **OpenHearing**. Confirm the home screen shows the **"Not a medical
   device"** disclaimer card.
2. Tap **Run hearing screening (debug)**. Confirm the uncalibrated-estimate notice
   is shown before the test.
3. Tap **Start screening**. A tone should play in the **Right** ear first at
   1000 Hz. Verify:
   - the tone **ramps** in/out (no click/pop at start or end);
   - it is a clean single pitch;
   - **Play tone again** replays it;
   - lowering the **Volume cap** makes replays quieter; raising it makes them louder
     (but never jarringly loud).
4. Respond honestly with **Yes, I heard it** / **No, I didn't**. The level should
   drop after a "yes" and rise after a "no" (the staircase). Progress advances
   through both ears across 6 frequencies (12 points).
5. Mid-test, press **Stop / mute now** — audio must cut **immediately**.
6. Complete the screening. Verify the results screen shows:
   - an **Audiogram** table (estimated dB HL) per ear, per frequency;
   - a **Prescribed gain (half-gain rule)** table per ear (≈ half the dB HL,
     capped, ≥ 0);
   - the uncalibrated/​see-an-audiologist caveat.
7. **Run again** should restart cleanly.

### What to report back

- Any tone that is clicky, distorted, or surprisingly loud (**safety** — report
  via SECURITY.md, not a public issue).
- Whether the staircase feels sensible (levels track your responses).
- Stereo routing: did the "Right"/"Left" labels match the ear you heard?
  (Phone speakers can't pan per-ear; use a headset to check.)
- Latency between tapping and the next tone.

> Calibration is not done yet, so absolute dB HL numbers are estimates. We're
> verifying *behaviour and safety* here, not clinical accuracy.

## Capture a real demo while you're here

The README screenshots are currently from an emulator. While testing on a real
device, grab authentic assets:

```bash
# a screenshot
adb exec-out screencap -p > docs/images/results.png
# a ~20-30s screen recording, then convert to GIF (needs ffmpeg)
adb shell screenrecord --time-limit 30 /sdcard/demo.mp4 && adb pull /sdcard/demo.mp4
```

Storyboard for the demo GIF: quiet speech nearby → run/recall the screening →
turn on assist → the same speech is clearly louder. This single asset will do more
for adoption than anything else (see the launch notes).

## Later phases

- Phase 2: real-time amplification latency + limiter behaviour under load.
- Phase 3: AirPods BLE/L2CAP capture — see [PROTOCOL.md](PROTOCOL.md).
