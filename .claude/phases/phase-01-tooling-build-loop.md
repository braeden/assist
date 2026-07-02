# Phase 01 — Tooling & Build/Debug Self-Loop

**Objective:** make it possible to build, install, run, and read logs of the app
autonomously from the shell, with no IDE in the loop.

**Prerequisites:** macOS with Homebrew; Android Studio installed; Android SDK at
`~/Library/Android/sdk` with `platform-tools` and `cmdline-tools/latest`
(already present as of phase-00 probe).

**Do it yourself / no handoff** except the two human checkpoints below.

## Deliverables
1. **JDK 17** available for Gradle. Prefer the Android Studio JBR:
   `/Applications/Android Studio.app/Contents/jbr/Contents/Home`. Do not rely on
   the Homebrew JDK 20. Record the chosen path in `README.md` and set it via
   `org.gradle.java.home` in `gradle.properties` (or `JAVA_HOME` in the scripts).
2. **SDK components installed** via `sdkmanager` (in `cmdline-tools/latest/bin`):
   - `"platforms;android-35"`, `"build-tools;35.0.0"`, `"platform-tools"`
   - `"system-images;android-35;google_apis_playstore;arm64-v8a"` (emulator)
   - Accept licenses: `sdkmanager --licenses`.
3. **Emulator AVD** named `pixel7pro_api35` (Pixel 7 Pro profile, API 35, Play image).
4. **Helper scripts** in `scripts/` (make them the build-loop surface):
   - `scripts/build.sh` → `./gradlew :app:assembleDebug`
   - `scripts/install.sh` → build + `adb install -r` the debug APK to the
     selected device (`ANDROID_SERIAL` if set).
   - `scripts/run.sh` → install + `adb shell am start` the launcher activity.
   - `scripts/logcat.sh` → `adb logcat` filtered to the app PID + `Assist:*` tag.
   - `scripts/enable-service.sh` → prints/opens the Accessibility settings intent
     and (best-effort) sets the secure setting on an emulator/rooted device.
   - `scripts/emulator.sh` → boot `pixel7pro_api35` headless-friendly.
   - Each script resolves `ANDROID_HOME`, `JAVA_HOME`, picks a device, and fails
     loudly with a helpful message.
5. **Root docs:** create `CLAUDE.md` (progress log, starts here) and update
   `README.md` with prerequisites + the script commands. `.gitignore` for
   Android/Gradle/Kotlin (`/build`, `.gradle`, `local.properties`, `*.keystore`,
   `.env`).
6. **`local.properties`** with `sdk.dir` (gitignored).

## Steps
1. Install SDK components and licenses via `sdkmanager`; create the AVD via
   `avdmanager`. Verify `adb devices` sees the emulator after `scripts/emulator.sh`.
2. Write the scripts; make executable. They must work before the app exists by
   failing gracefully (no APK yet) — they become fully functional after phase-02.
3. Commit in logical steps: (a) docs + gitignore, (b) scripts, (c) any tooling notes.

## Human checkpoints
- If an ADB/Android **MCP** is desired instead of raw scripts, stop and ask the
  user to approve/install it; otherwise proceed with scripts (the default).
- Emulator vs device: scripts must honor `ANDROID_SERIAL`/`adb -s` so the same
  loop targets the Pixel later (phase-11).

## Acceptance criteria
- `sdkmanager --list_installed` shows android-35 platform + build-tools 35 + the
  system image.
- `scripts/emulator.sh` boots the AVD and `adb devices` lists it.
- Scripts are executable and print clear errors when the APK/device is missing.
- `CLAUDE.md` and `README.md` exist and describe the loop.

## Verification
```bash
sdkmanager --list_installed
avdmanager list avd
bash scripts/emulator.sh & sleep 40 && adb devices
```

## Handoff notes
After this phase the self-loop is: edit → `scripts/install.sh` → `scripts/logcat.sh`.
Phase-02 makes the scripts produce a real, launchable APK.
