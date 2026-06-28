# AirPods protocol — what we know vs. what we assume

> **Status: UNVERIFIED.** The AirPods Bluetooth control protocol is
> reverse-engineered, **not** published by Apple. Nothing in this document should
> be treated as fact until it is confirmed on real hardware and checked off below.
> We will **never** invent a packet format and present it as known.

## Sources we build on

- **LibrePods** — https://github.com/kavishdevar/librepods
- **CAPod** (Companion for AirPods) — community reverse-engineering of the
  battery/status beacons and control behaviour.

When we record a "known" fact below, it must cite where it came from (a LibrePods
source file/commit, a CAPod observation, or our own capture) — not "the model
believes." If you can only say "LibrePods appears to do X," it is **assumed**, not
known, until we reproduce it.

## What is reasonably established (still verify on YOUR units)

- AirPods broadcast a manufacturer-specific BLE advertisement (Apple, company ID
  `0x004C`) that encodes model and battery/charging/in-ear status. CAPod and
  LibrePods both decode this; this is how third-party Android apps already show
  AirPods battery. **Assume the byte layout is firmware-version-dependent.**
- A richer control channel (used for things like ANC/Transparency mode switching)
  is reached over an **L2CAP channel**, not standard GATT. LibrePods documents
  this path. The exact PSM, handshake, and opcodes are the uncertain part.

## What is assumed / unknown (the risky part)

- ❓ The exact L2CAP PSM and connection handshake for the control channel.
- ❓ Opcode/packet format for reading detailed state and for setting
  listening mode (ANC / Transparency / Adaptive).
- ❓ Whether a **hearing-aid / amplification** behaviour can be triggered or
  configured at all from a non-Apple host, or whether it is gated by firmware /
  account / a capability Android simply cannot present.
- ❓ Whether any of this requires the phone to be the active audio host, a
  specific bonding state, or elevated (root) privileges.
- ❓ How much differs between **AirPods Pro 2** and **Pro 3**, and across firmware.

**Working assumption for the app:** we may not be able to drive Apple's on-device
hearing-aid DSP from Android at all. OpenHearing therefore does its own
amplification (`:core-audio`) and treats AirPods control as a best-effort bonus.

## How to capture BLE / HCI logs (for the maintainer with hardware)

We need real captures to turn ❓ into ✅ or ❌.

### A. Android Bluetooth HCI snoop log (primary)

1. On the Android phone: **Settings → About phone → tap Build number 7×** to
   enable Developer options.
2. **Settings → System → Developer options → enable "Enable Bluetooth HCI snoop
   log"** (wording varies by OEM). Some devices require a Bluetooth off/on or
   reboot to start logging.
3. Reproduce the scenario (e.g. connect AirPods, toggle Transparency from an
   iPhone nearby while the Android phone observes; or pair AirPods to the Android
   phone and toggle modes from whatever UI exists).
4. Pull the log:
   ```bash
   adb bugreport bugreport.zip          # snoop log is inside (FS/data/misc/bluetooth/logs/)
   # or, on devices that expose it directly:
   adb pull /data/misc/bluetooth/logs/btsnoop_hci.log
   ```
5. Open `btsnoop_hci.log` in **Wireshark** and filter for the AirPods MAC / Apple
   company ID `0x004C` / the L2CAP channel.

### B. Decode the BLE advertisement (battery/state)

- Use **nRF Connect** (Nordic) on the Android phone to scan and dump the raw
  manufacturer data from the AirPods advertisement, then compare the bytes to
  CAPod's/LibrePods' documented layout for the same action (in-ear, charging,
  battery level changing).

### C. (Optional) host-side L2CAP probing — only behind a clearly-flagged debug build

- Any code that opens an L2CAP channel to the AirPods must live behind the
  `UNVERIFIED` interfaces in `:airpods-protocol`, be opt-in, and log every byte
  sent/received. We do **not** ship speculative writes to users.

> Capture safety: never share raw logs publicly without scrubbing — they can
> contain device identifiers and other personal data.

## Verification checklist

Move an item to ✅ only with a citation or an attached capture; use ❌ if a
capture shows it does **not** work from Android.

- [ ] AirPods Pro 2 detected from its BLE advertisement on Android (model id confirmed)
- [ ] AirPods Pro 3 detected from its BLE advertisement on Android (model id confirmed)
- [ ] Battery (L/R/case) decoded correctly vs. ground truth
- [ ] In-ear / charging status decoded correctly
- [ ] L2CAP control channel PSM + handshake reproduced from a capture
- [ ] Read listening mode (ANC / Transparency / Adaptive) over the control channel
- [ ] Set listening mode over the control channel (round-trips, survives)
- [ ] Determined whether any hearing-aid/amplification config is reachable from Android (✅/❌)
- [ ] Confirmed whether root/Xposed is required for any of the above
- [ ] Documented Pro 2 vs Pro 3 and firmware differences observed

## Per-claim log

| Claim | Source | Status | Capture/notes |
|---|---|---|---|
| Apple BLE adv encodes battery/state | LibrePods, CAPod | assumed | needs our own capture (B) |
| L2CAP control channel exists | LibrePods | assumed | needs capture (A) |
| Listening mode settable from Android | — | unknown | — |
| Hearing-aid DSP controllable from Android | — | unknown | likely ❌ without firmware access |
