# Conventions (shared — keep parallel work compatible)

## Identifiers
- Application ID / namespace: `com.assist`
- App module: `:app` (single module to start; split later only if needed)
- Min SDK: **30** (needed for `AccessibilityService.takeScreenshot`). Target/compile SDK: **35**.
- JDK: **17** (Android Gradle Plugin requirement). Use the JBR bundled with
  Android Studio or a Temurin 17; do NOT build with the Homebrew JDK 20.
- Kotlin, Jetpack Compose, Coroutines/Flow.

## Libraries (pin in `gradle/libs.versions.toml`)
- DI: **Hilt**
- DB: **Room** (KSP)
- Async: **kotlinx-coroutines**
- HTTP/LLM: decided in phase-04 (official `com.anthropic:anthropic-java` **or**
  Ktor/OkHttp direct). Until then, code against the `LlmClient` interface only.
- Serialization: **kotlinx.serialization** (JSON for a11y tree + tool args)
- Overlay UI: Compose in a foreground service via `ComposeView` + `WindowManager`
- Prefs/secrets: **EncryptedSharedPreferences** (androidx.security-crypto) for API key

## Code style
- Match existing Kotlin idioms; ktlint-clean. No wildcard imports.
- One public class per file; package-private helpers allowed in the same file.
- Coroutines: structured concurrency; expose `Flow` for streams, `suspend` for
  one-shots. No `GlobalScope`.
- All user-facing strings in `res/values/strings.xml`.
- Log tag = class simple name; never log API keys, message content at INFO+, or
  full screenshots.

## Testing
- Unit tests: JUnit + Turbine (flows) + MockK. Live under `src/test/`.
- Instrumented tests (need device/emulator): `src/androidTest/`.
- The **real-Claude** smoke test lives behind a gradle flag / env var
  (`ANTHROPIC_API_KEY`) and is opt-in — never run in CI without a key.

## Git
- Commit per logical, buildable step. Conventional-commit style subjects
  (`feat:`, `chore:`, `docs:`, `fix:`).
- Keep `CLAUDE.md` (repo root) updated as the running progress log; update
  `README.md` (repo root) with build/run instructions as they solidify.
- Co-author trailer on commits per repo policy.

## Definition of done for any phase
1. `./gradlew :app:assembleDebug` succeeds.
2. `./gradlew :app:testDebugUnitTest` passes (phase's unit tests).
3. New/changed public contracts documented in the phase file are honored.
4. `CLAUDE.md` progress log updated with what landed.
