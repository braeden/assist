# CLAUDE.md — Assist progress log

Running log of what has landed, for humans and models picking up the work.
Plan and per-phase specs: [`.claude/`](.claude/README.md).

## Project snapshot
- **Goal:** on-device AI phone agent (Kotlin/Android) that drives the phone via
  Accessibility APIs from Claude tool calls, by voice. Personal sideload target
  (Pixel 7 Pro). See `.claude/phases/phase-00-decisions.md`.
- **Locked decisions:** sideload-only; hybrid perception (a11y tree + on-demand
  screenshot); `gradlew`+`adb`+`logcat` build loop; model-agnostic `LlmClient`
  with a concrete Claude impl (default `claude-opus-4-8`).

## Conventions (see `.claude/CONVENTIONS.md`)
- App id `com.assist`; single `:app` module; minSdk 30 / target/compile 35; JDK 17.
- Kotlin, Compose, Hilt, Room, Coroutines, kotlinx.serialization.

## Environment (provisioned)
- JDK 17.0.19 (Temurin) at `~/Library/Java/JavaVirtualMachines/temurin-17.jdk`
  (resolved via `/usr/libexec/java_home -v 17`).
- Android SDK `~/Library/Android/sdk`: platform-tools **37.0.0**, build-tools
  35.0.0, platform android-35, emulator 36.6.11, system image
  `system-images;android-35;google_apis_playstore;arm64-v8a`.
- Emulator AVD: **`pixel7pro_api35`** (Pixel 7 Pro, Android 15, Play image).

## Progress

### Phase 00 — Decisions ✅
Feasibility confirmed; decisions locked (`.claude/phases/phase-00-decisions.md`).

### Phase 01 — Tooling & build/debug self-loop ✅
- Installed JDK 17 (Temurin), upgraded adb/platform-tools to 37.0.0, installed
  API-35 platform + build-tools 35 + emulator + Pixel Play system image.
- Created AVD `pixel7pro_api35`.
- Added `scripts/` build loop: `env.sh` (shared resolver), `build.sh`,
  `install.sh`, `run.sh`, `logcat.sh`, `emulator.sh`, `enable-service.sh`,
  `devices.sh`.
- Added `.gitignore`, `local.properties` (gitignored), root `README.md`, this log.

### Phase 02 — App skeleton ✅
- **Gradle project** (Kotlin DSL): `settings.gradle.kts`, root + `:app`
  `build.gradle.kts`, `gradle.properties`, version catalog
  `gradle/libs.versions.toml` (AGP 8.7.3, Gradle 8.9, Kotlin 2.0.21, Compose BOM
  2024.10.01, Hilt 2.52, Room 2.6.1, coroutines/serialization/security-crypto).
  Wrapper committed (`./gradlew`, pinned 8.9).
- **App** under `com.assist`: `AssistApplication` (`@HiltAndroidApp`),
  `ui.MainActivity` (Compose) onboarding — live permission status + deep-links
  (accessibility, overlay, mic, notifications), masked Anthropic-key field, gated
  "Start session". `data.SecretStore` + `EncryptedSecretStore`
  (EncryptedSharedPreferences), `di.AppModule`, `ui.Permissions` helper,
  Material3 theme. Package skeleton stubs for the parallel phases
  (`service/ llm/ llm/anthropic/ agent/ voice/ overlay/ prompt/`).
- **Manifest**: permissions + commented placeholder registrations for the
  phase-03 AccessibilityService and phase-07 overlay FGS.
- **Verified**: `:app:assembleDebug` + `:app:testDebugUnitTest` green; installed
  and launched on `pixel7pro_api35` (onboarding screen renders, no crash).
- **Build automation**: inner loop is now **Gradle tasks** (`gradle/device.gradle.kts`:
  `runApp`/`launchApp`/`stopApp`/`enableAccessibility`/`listDevices`, plus AGP
  `installDebug`); `build.sh`/`install.sh`/`run.sh` thinned to wrappers.
  `emulator.sh`/`logcat.sh`/`enable-service.sh` stay scripts (streaming/branching).
- **Portability**: no machine-specific bakes committed; `env.sh` is OS-aware
  (macOS/Linux SDK + JDK discovery) and auto-generates gitignored
  `local.properties` from the resolved SDK on first build.

### Phase 03 — Accessibility service + device tools ✅
- `service.AssistAccessibilityService` (singleton `instance`, `@AndroidEntryPoint`)
  registered in the manifest + `res/xml/accessibility_service_config.xml` (retrieve
  content, gestures, screenshot). Emits `ScreenChangeSignals.events` on window
  state/content changes so the agent loop can await UI settle.
- `ScreenSerializer` walks the a11y tree — framework-free via a `NodeView`
  abstraction — into `ScreenState`/`UiElement` with a stable per-frame `id`→node
  map (`ScreenFrame`). Caps 150 elements / 200-char text; recycles nodes (skipped
  immediately, retained on `frame.recycle()`) to avoid ANRs.
- `DeviceController` (interface + `DefaultDeviceController`): `getScreenState`,
  `takeScreenshot`, tap/longPress (+xy), swipe/scroll (id or direction), setText,
  pressKey (BACK/HOME/RECENTS/NOTIFICATIONS/QUICK_SETTINGS/ENTER), openApp (label
  resolution via `AppResolver`), wait — all `suspend`, return `ToolOutcome`.
- DI in new `di/ServiceModule.kt` (AppModule untouched). Debug hook:
  `com.assist.DEBUG_DUMP_SCREEN` broadcast dumps/acts for verification.
- Verified on emulator: Settings dump (27 elements, sensible bounds/flags),
  tap-by-id navigation + swipe change the screen, screenshot non-null 1440x3120.
  29 unit tests green (serializer mapping/caps/recycle, bounds/center, swipe
  geometry, label resolution, rendering).

### Next
- **Fan out: phases 03 / 04 / 05 in parallel** (see `.claude/README.md`). They
  touch `service/`, `llm/`, `data/` respectively and share only `SecretStore`
  (04) + locally-defined tool contracts. Good point to reset context and hand off.

## Notes / gotchas
- Homebrew `openjdk@17` won't bottle on this machine (needs full Xcode); used a
  prebuilt Temurin tarball into `~/Library/Java/JavaVirtualMachines` instead.
- The Pixel 7 Pro AVD profile required updating `cmdline-tools;latest` (the
  bundled catalog only went to Pixel 5).
- `brew install gradle` builds `gettext` from source here (no bottle, same
  CommandLineTools-only issue as the JDK) — too slow. Bootstrapped the wrapper
  instead by downloading the Gradle 8.9 dist zip directly and running
  `gradle wrapper`. The committed wrapper means no system Gradle is needed again.
