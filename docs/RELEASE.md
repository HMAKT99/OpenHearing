# Release & distribution

How to build a signed release and what must be cleared before putting OpenHearing
in front of real users.

## ⚠️ Release gates (do not skip)

Before any public consumer release:

1. **On-device safety validation** — run the device tests in
   [DEVICE_TESTING.md](DEVICE_TESTING.md) on real hardware. Confirm: tones/assist
   never get uncomfortably loud, the limiter holds, instant Stop/mute works, and
   the feedback guard tames howl. The limiter safety unit tests passing is
   necessary but **not sufficient** — it must be verified on a device.
2. **Comfort calibration** — confirm the comfort ceiling behaves sensibly across
   your test devices/earbuds (see [CALIBRATION.md](CALIBRATION.md)).
3. **Framing/legal review** — keep the "hearing assistance, not a medical device"
   framing everywhere. Depending on your jurisdiction, a hearing-screening/
   amplification app may face medical-device rules (US FDA, EU MDR, etc.). Get
   appropriate advice before broad distribution or any medical claims. This repo
   cannot provide legal advice.

## Build a signed release

1. Generate an upload keystore (one time, keep it safe and **never commit it**):
   ```bash
   keytool -genkey -v -keystore openhearing-release.jks \
     -keyalg RSA -keysize 2048 -validity 10000 -alias openhearing
   ```
2. Create `keystore.properties` in the repo root (gitignored):
   ```properties
   storeFile=/absolute/path/to/openhearing-release.jks
   storePassword=********
   keyAlias=openhearing
   keyPassword=********
   ```
3. Build:
   ```bash
   ./gradlew assembleRelease        # signed APK (if keystore.properties present)
   ./gradlew bundleRelease          # AAB for Play
   ```
   Without `keystore.properties`, release builds are produced **unsigned**.

Release builds use R8 (`isMinifyEnabled = true`, `isShrinkResources = true`).

## Versioning

Bump `versionCode` (integer, monotonic) and `versionName` (semver, e.g.
`0.2.0-alpha01`) in `app/build.gradle.kts` for each release. Tag releases in git
(`vX.Y.Z`) and attach the APK to a GitHub Release.

## Distribution channels

- **GitHub Releases (sideload)** — simplest; good for alpha testers now.
- **F-Droid** — best fit for this GPLv3 FOSS app. Metadata lives in
  `fastlane/metadata/android/` (store text) and a build recipe is submitted to the
  `fdroiddata` repo. No analytics/proprietary deps keeps it eligible.
- **Google Play** — widest reach; needs a developer account, a privacy policy
  (see [PRIVACY.md](PRIVACY.md)), a Data safety form (declare: no data collected),
  and a content rating. Health-adjacent apps can draw extra review — keep the
  non-medical framing and never imply FDA clearance.

## Pre-release checklist

- [ ] Device safety validation passed (gate 1)
- [ ] `./gradlew ktlintCheck detekt test testDebugUnitTest` green
- [ ] `./gradlew assembleRelease` produces a signed APK
- [ ] versionCode/versionName bumped, git tagged
- [ ] README status, screenshots, and disclaimers current
- [ ] Privacy policy published/linked
