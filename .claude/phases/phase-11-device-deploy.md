# Phase 11 — Device Deploy & Debug (Pixel 7 Pro)

**Objective:** deploy and debug on the physical Pixel 7 Pro running the latest
Android, with clear tooling and docs; keep the emulator as the fast inner loop.

**Prerequisites:** phase-01 (scripts). Runnable anytime after phase-02; most
valuable once phase-03/06 exist. Owns `scripts/` device paths + docs.

## Deliverables
1. **Device onboarding doc** in `README.md` (or `docs/device.md`):
   - Enable Developer Options + USB debugging on the Pixel; authorize the host.
   - Optional wireless debugging (`adb pair` / `adb tcpip`).
   - Manually enable the **Accessibility service** and **Display over other apps**;
     grant mic + notifications. Screenshots of the settings paths.
2. **Device-aware scripts** — `scripts/*.sh` honor `ANDROID_SERIAL` / `adb -s`
   and a `scripts/devices.sh` to list/select. `scripts/install.sh` targets the
   Pixel when selected; `scripts/logcat.sh` filters to the app on-device.
3. **Debug helpers:**
   - `scripts/dump-a11y.sh` → trigger the phase-03 screen dump on device.
   - `scripts/screenshot.sh` → pull a screenshot for inspection.
   - `scripts/bugreport.sh` → capture logs/ANRs when a run misbehaves.
   - Crash symbolication notes; how to read `AgentLoop`/tool-call logs.
4. **Real-app validation checklist** — a scripted manual pass on real apps
   (Gmail, Maps, Clock, Messages) confirming perception + a representative action
   each, plus the flagship scenario ("find my next flight and start navigation")
   as a tracked, repeatable manual eval.
5. **Release-ish build** — signed debug (or a personal release keystore,
   gitignored) and `scripts/deploy-pixel.sh` for one-command install to the phone.

## Contracts I consume
- Everything runnable; primarily exercises phases 03/06/07/08 on hardware.

## Acceptance criteria
- One command installs the current build to the Pixel and launches it.
- The Accessibility service can be enabled and the agent perceives/acts on ≥3
  real apps.
- The flagship scenario runs end-to-end on the Pixel (may need iteration);
  results/logs captured via the debug helpers.
- Device setup is documented well enough for a fresh machine to reproduce.

## Verification
```bash
scripts/devices.sh                 # select the Pixel
ANDROID_SERIAL=<pixel-serial> bash scripts/deploy-pixel.sh
ANDROID_SERIAL=<pixel-serial> bash scripts/logcat.sh
```

## Human checkpoints
- Physical device pairing/authorization and the manual permission grants are
  human steps (cannot be automated on a non-rooted Pixel).
- The flagship scenario should be validated with the user watching, given it
  touches real accounts/apps and the safety gates.
