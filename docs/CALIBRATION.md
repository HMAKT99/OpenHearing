# Calibration

OpenHearing produces and amplifies sound, but Android gives no portable way to
know the true sound pressure level (dB SPL) reaching a given user's ear — it
depends on the phone, its DAC, the headset/earbuds, and fit. This document is
honest about what that means and how we stay safe anyway.

## What we do NOT claim

- We do **not** claim the screening's dB HL values are clinically accurate.
- We do **not** claim a specific dB SPL output.
- This is not a medical device or a calibrated audiometer (see docs/SAFETY.md).

## Two layers of protection regardless of calibration

1. **Digital ceiling + limiter.** The hearing-assist chain always ends in the
   look-ahead limiter, which guarantees no output sample exceeds a configured
   linear ceiling (`HearingAssistChain.DEFAULT_CEILING_LINEAR`, lowered by the
   user's comfort setting). This guarantee is unit-tested and does not depend on
   any calibration being correct.
2. **Conservative defaults.** Until the user calibrates, the comfort ceiling
   defaults low (`DataStoreSettingsRepository` default `0.5`), so the app errs
   quiet.

## Comfort calibration (what the app offers today)

In **Settings → Comfort calibration**, the user previews a 1 kHz tone at the
candidate maximum and adjusts a slider until that maximum is comfortably loud —
never painful. That value becomes the assist-mode output ceiling
(`SettingsRepository.observeComfortCeiling`).

This is a **subjective, relative** calibration: it pins the loudest the app will
ever get to a level the user has personally confirmed is comfortable. It is not a
dB SPL measurement, but it is a meaningful, safe bound and requires no equipment.

## True dB SPL calibration (future / advanced)

A precise calibration would require a reference: e.g. a sound-level meter (or a
coupler/ear simulator) measuring a known test tone, to derive the dB SPL-per-dBFS
offset for a specific phone + earbud combination. The codebase leaves room for a
per-device `CalibrationProfile` offset to slot in front of the limiter ceiling
once such a measurement exists. Community-contributed calibration data for common
phone/earbud pairs (including AirPods Pro 2/3) is a good future contribution.

Until then: **comfort calibration + the hard limiter** are the safety model, and
the app states clearly that results are estimates.
