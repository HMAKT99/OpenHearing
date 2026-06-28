# Hearing safety policy

OpenHearing plays calibrated tones (the screening) and amplifies live sound (the
assist mode) **directly into people's ears**. Hearing safety is non-negotiable.
Any code path that could exceed these limits is treated as a **critical bug**.

This document is the policy; `app.openhearing.common.SafetyConstants` in
`:core-common` is the machine-enforced source of truth. Code must reference those
constants, never redefine its own limits.

## Hard rules

1. **Hard output ceiling.** No audio the app produces may exceed
   `SafetyConstants.MAX_OUTPUT_SPL_DB`. The final stage of every processing chain
   MUST be an `OutputLimiter` (`:core-audio`) enforcing this. There is no path to
   the audio device that bypasses the limiter.
2. **Quieter ceiling for test tones.** Screening tones are capped lower
   (`MAX_TONE_SPL_DB`) — they never need to be as loud as assist mode, and many
   users have reduced loudness tolerance.
3. **Gentle ramps.** Every tone ramps on and off over at least
   `MIN_TONE_RAMP_MS`. No instantaneous, clicky, or startling onsets. No sudden
   loud tones, ever.
4. **Master cap + instant mute, always available.** The UI must always expose a
   master volume cap (bounded by `MAX_MASTER_GAIN_CAP_DB`) and a one-tap instant
   mute, reachable on every screen where audio can play.
5. **Feedback / howl guard.** Assist mode must detect and suppress acoustic
   feedback before it becomes loud (Phase 2).
6. **Fail safe, not loud.** On any error, glitch, or uncertainty (including
   uncalibrated output), attenuate or mute — never pass audio through at full gain.

## Calibration caveat

The mapping from digital level (dBFS) to real-world loudness (dB SPL) depends on
the phone, the DAC, and the specific earbuds. Until a device/earbud is calibrated,
the app **must assume the worst case and stay conservative** (err quiet). True
dB SPL claims require calibration; uncalibrated, treat the limits as digital
headroom and keep generous margin.

## Testing policy (release gate)

- The `OutputLimiter` ships with an explicit safety test suite. Phase 0 pins the
  core invariant (*nothing exceeds the ceiling*, including extreme/garbage input).
- **Phase 2 gate:** before any real-time amplification reaches users, the limiter
  must have tests covering: steady overload, sudden transients, sustained
  full-scale input, ramp on/off, and a simulated feedback howl — all proving the
  output stays at or below the ceiling.
- Any change touching an audio output path requires its safety tests to pass in CI
  and should be called out in review (see CONTRIBUTING.md).

## Disclaimers (must appear in-app and in docs)

OpenHearing is **not a medical device**, not a certified hearing aid, and not a
substitute for a professional hearing exam. It does not diagnose or treat anything.
This disclaimer appears in the README, in onboarding, and before any test starts.
