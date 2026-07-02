# Phase 02 — App Skeleton

**Objective:** a minimal, installable, launchable Kotlin/Compose app with the
package structure, DI, and permission onboarding that all later phases build on.

**Prerequisites:** phase-01 (build loop + SDK). **Sequential** (blocks the fan-out).

## Deliverables
1. **Gradle project** (Kotlin DSL): root `build.gradle.kts`, `settings.gradle.kts`,
   `gradle/libs.versions.toml`, `:app` module. Configure: `namespace com.assist`,
   `minSdk 30`, `compile/targetSdk 35`, Compose, Hilt, KSP, kotlinx-serialization,
   kotlinx-coroutines, androidx.security-crypto, Room (deps declared, unused ok).
   Gradle wrapper committed (`./gradlew` works).
2. **Package skeleton** under `com.assist` matching ARCHITECTURE.md (empty
   packages with a `package-info`-style KDoc placeholder or a single stub each):
   `service/ llm/ llm/anthropic/ agent/ data/ voice/ overlay/ ui/ di/ prompt/`.
3. **`AssistApplication`** (`@HiltAndroidApp`).
4. **`MainActivity`** (Compose) — an onboarding/home screen that:
   - Shows status of required permissions and deep-links to grant them:
     Accessibility service, `SYSTEM_ALERT_WINDOW` (overlay), microphone,
     `POST_NOTIFICATIONS`.
   - Has a field to paste the **Anthropic API key**, stored via
     `EncryptedSharedPreferences` (`data/` or `di/` helper `SecretStore`).
   - A "Start session" button (wired to a no-op for now).
5. **`AndroidManifest.xml`** declares: the application class, `MainActivity`,
   permissions (`SYSTEM_ALERT_WINDOW`, `RECORD_AUDIO`, `POST_NOTIFICATIONS`,
   `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `QUERY_ALL_PACKAGES`
   with justification comment), and **placeholder registrations** (commented or
   stubbed) for the AccessibilityService (phase-03) and overlay foreground
   service (phase-07) so later phases only fill implementations.
6. **`SecretStore`** interface + EncryptedSharedPreferences impl:
   `fun getApiKey(): String?`, `fun setApiKey(v: String)`. Owned here; consumed
   by phase-04.
7. **DI:** `di/AppModule` providing `SecretStore` and app-scoped coroutine scope.

## Contracts I own (consumed by later phases)
- `com.assist.data.SecretStore`
- App theme + a `Permissions` helper (`isAccessibilityEnabled`, `canDrawOverlays`, etc.)

## Steps
1. Scaffold Gradle + module + wrapper. Get `assembleDebug` green with an empty
   Compose activity first, commit.
2. Add Hilt + Application class, commit.
3. Add permission-onboarding UI + `SecretStore`, commit.
4. Update `scripts/run.sh` launcher activity name if needed; verify install+launch.

## Acceptance criteria
- `scripts/install.sh` installs; `scripts/run.sh` launches `MainActivity` on the
  emulator; app shows the onboarding screen.
- Pasting a key persists it across app restarts (encrypted).
- `./gradlew :app:testDebugUnitTest` passes (add a trivial `SecretStore` test with
  a fake/Robolectric or extract logic to test without Android).
- Package directories exist per ARCHITECTURE.md.

## Verification
```bash
bash scripts/install.sh && bash scripts/run.sh
bash scripts/logcat.sh   # see AssistApplication onCreate log
```

## Handoff notes
**After this phase, fan out: phases 03, 04, 05 run in parallel.** They touch
`service/`, `llm/`, `data/` respectively and share only `SecretStore` (04) and
the tool contracts, which each phase defines locally per its "Contracts" section.
