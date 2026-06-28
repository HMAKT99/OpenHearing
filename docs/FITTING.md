# Fitting: turning an audiogram into a gain curve

This documents how OpenHearing maps a measured audiogram to prescribed
amplification, and **why** we chose the formula we did. It is intentionally
honest about the limits.

> Reminder: OpenHearing is a hearing-**assistance** tool, not a medical device or
> a certified hearing aid. The fitting here is a reasonable, conservative starting
> point — not a clinical prescription. See docs/SAFETY.md and the in-app
> disclaimers.

## The choice: start with the half-gain rule

`FractionalGainRule(fraction = 0.5)` — the **half-gain rule** — prescribes
insertion gain equal to half the hearing loss (in dB HL) at each frequency, floored
at 0 and capped at a safe per-band maximum.

We start here, deliberately, instead of a modern prescriptive target like
**NAL-NL2** or **DSL v5**:

| | Half-gain (chosen for v1) | NAL-NL2 / DSL v5 |
|---|---|---|
| Complexity | Trivial, transparent, auditable | Proprietary/complex non-linear formulae |
| Inputs needed | Audiogram thresholds only | Thresholds **+ calibrated real-ear SPL, compression, loudness models** |
| Calibration sensitivity | Degrades gracefully when uncalibrated | Assumes calibrated dB SPL to be meaningful |
| Safety to ship first | High — conservative, easy to reason about | Higher risk without calibration + WDRC |
| Good enough for v1? | Yes, as a linear baseline | The eventual goal once we have WDRC + calibration |

The decisive factor is **calibration**. NAL-NL2's benefits depend on knowing the
real output level in dB SPL at the eardrum and on pairing the prescription with
wide-dynamic-range compression (WDRC). OpenHearing does not yet have per-device /
per-earbud calibration or WDRC (both are Phase 2). Prescribing a sophisticated
non-linear target on top of an **uncalibrated** chain would be false precision —
and potentially unsafe. The half-gain rule is the honest, safe baseline.

## The seam for better fittings

`FittingStrategy` is a one-method interface. A future `NalNl2Fitting` (or a
simplified NAL-R linear fit) drops in behind it without touching the screening or
DSP code. `FractionalGainRule` already generalizes the family (e.g. one-third gain),
so swapping prescriptions is a localized change.

**Planned path:** Phase 2 adds WDRC + calibration hooks; once output can be
expressed in real dB SPL, we revisit and likely offer a NAL-style target as an
option alongside the simple linear rule.

## The calibration caveat (important)

Until the audio path is calibrated, the screening's "dB HL" values and these
gains are **relative estimates, not absolute clinical quantities**. The app must
say so wherever it shows an audiogram or applies gain. The output limiter
(SafetyConstants) bounds loudness regardless of any fitting error.
