# Phase 13 — CI & Project Infrastructure (linting, static analysis, CI, Room schema)

**Objective:** add the project scaffolding a mature Kotlin/Android repo needs but
this one lacks: continuous integration, Kotlin static analysis + formatting,
Android Lint gating, and Room schema export (so DB migrations are testable). The
app code and tests are healthy; this phase is purely tooling/infrastructure and
must **not change app behavior**.

**Prerequisites:** none functionally, but **read the coordination note** — this
touches `app/build.gradle.kts`, `gradle/libs.versions.toml`, and
`data/AssistDatabase.kt`, which Phase 12 also touches. Ideally run **after Phase
12 merges** to avoid build-file conflicts.

**Self-contained:** everything needed is below; you should not need the
conversation history. Also read `.claude/CONVENTIONS.md`.

---

## Current state (audited 2026-07)

**Present (do not re-add):** Gradle Kotlin DSL, committed wrapper (Gradle 8.9),
version catalog `gradle/libs.versions.toml`, 122 unit tests (`testDebugUnitTest`),
a key-gated instrumented smoke test (`androidTest`,
`com.wisp.llm.anthropic.AnthropicSmokeTest`), portable OS-aware `scripts/env.sh`
(auto-generates gitignored `local.properties` from the resolved SDK), `.gitignore`,
`proguard-rules.pro`.

**Missing (this phase adds):**
- **No CI** — pushes to `github.com/braeden/assist` run zero automated checks.
- **No Kotlin static analysis / formatter** — no detekt, ktlint, or spotless.
- **No `.editorconfig`.**
- **Android Lint** runs by default but is unconfigured and not gated / not in CI.
- **Room `exportSchema = false`** (`data/AssistDatabase.kt`) — blocks proper
  migration tests. Phase 12 is adding a `Migration(1,2)`.
- No Kover/coverage, no dependabot, no pre-commit hook.

## Environment / fixed constraints (match these exactly)
- App id `com.wisp`; single `:app` module; `minSdk 30`, `target/compileSdk 35`.
- **JDK 17** (Temurin). AGP **8.7.3**, Gradle **8.9** (wrapper committed), Kotlin
  **2.0.21**, KSP **2.0.21-1.0.28**, Hilt 2.52, Room 2.6.1, Compose BOM 2024.10.01.
- Pick tool versions **compatible with Kotlin 2.0.21 / AGP 8.7.3 / JDK 17**.
- **Portability is a hard rule:** no absolute paths, machine serials, or committed
  `local.properties`. CI resolves the SDK/JDK via actions; local dev via `env.sh`.
- **Version-catalog everything** (plugins + libraries in `libs.versions.toml`).
- **Do not break the build:** `:app:assembleDebug` + `:app:testDebugUnitTest`
  (122 tests) must stay green. Grandfather existing code with **baselines** — do
  not do a mass reformat that rewrites every file.

---

## Deliverables

### 1. GitHub Actions CI — `.github/workflows/ci.yml`
- Triggers: `push` to `main` and `pull_request`.
- Runner `ubuntu-latest`. Steps:
  - `actions/checkout@v4`
  - `actions/setup-java@v4` (temurin, java-version 17)
  - `android-actions/setup-android@v3` (installs cmdline-tools; then accept
    licenses + install `platforms;android-35` and `build-tools;35.0.0`), **or**
    rely on AGP auto-provisioning if enabled. Ensure `ANDROID_HOME`/
    `ANDROID_SDK_ROOT` is exported so AGP resolves the SDK without a committed
    `local.properties`.
  - `gradle/actions/setup-gradle@v4` (Gradle cache).
  - Run: `./gradlew assembleDebug testDebugUnitTest lintDebug detekt ktlintCheck`
    (or a single `./gradlew check assembleDebug` once `check` aggregates them).
  - Upload reports as artifacts on failure (`app/build/reports/**`).
- **Do NOT run `connectedAndroidTest`** in the default job (needs an emulator +
  the Anthropic key). Optionally add a **separate, non-blocking** job using
  `reactivecircus/android-emulator-runner@v2` (API 35, `google_apis` arm/x86_64)
  that runs `connectedDebugAndroidTest`; the smoke test **skips cleanly with no
  key** (`assumeTrue` on empty `BuildConfig.ANTHROPIC_API_KEY`), so leave the key
  out unless the user adds a repo secret `ANTHROPIC_API_KEY` (then pass it via
  `-PanthropicApiKey=${{ secrets.ANTHROPIC_API_KEY }}`). **Never commit a key.**

### 2. detekt (static analysis)
- Plugin `io.gitlab.arturbosch.detekt` (≈ **1.23.7**, Kotlin-2.0 compatible) in the
  catalog `[plugins]` + applied in `:app` (or root). Config
  `config/detekt/detekt.yml` (start from `detektGenerateConfig`), and a committed
  **baseline** `config/detekt/baseline.xml` (`detektBaseline`) so existing code
  passes and only new issues fail. Keep type-resolution off first pass (plain
  `detekt` task) for speed; note in the file how to enable `detektMain` with
  classpath later.

### 3. ktlint (formatting) + `.editorconfig`
- Plugin `org.jlleitschuh.gradle.ktlint` (≈ **12.1.1**, using ktlint ≈ 1.3.x).
  Tasks `ktlintCheck` / `ktlintFormat`.
- Root **`.editorconfig`** is the source of truth — MATCH the existing code style
  so ktlint doesn't demand a rewrite:
  ```editorconfig
  root = true
  [*]
  charset = utf-8
  end_of_line = lf
  insert_final_newline = true
  indent_style = space
  indent_size = 4
  [*.{kt,kts}]
  max_line_length = 100
  ktlint_standard_trailing-comma-on-call-site = enabled
  ktlint_standard_trailing-comma-on-declaration-site = enabled
  # relax only if a rule fights the existing style; prefer a baseline over disabling
  ```
  The code wraps ~100 cols and uses trailing commas — verify these match before
  committing. Generate a ktlint **baseline** if a clean `ktlintFormat` would touch
  many files; prefer baseline over a mass reformat in this phase.

### 4. Android Lint gating
- Add an `android { lint { ... } }` block: `warningsAsErrors = false`,
  `abortOnError = true`, `checkDependencies = true`, and a committed
  `lint-baseline.xml` (run `./gradlew updateLintBaseline` once) so existing
  warnings don't block. Wire `lintDebug` into CI. If needed, a `lint.xml` for
  project-specific severities (note: `QUERY_ALL_PACKAGES` is already
  `tools:ignore`d in the manifest).

### 5. Room schema export (coordinate with Phase 12)
- In `data/AssistDatabase.kt` set `exportSchema = true`, and configure the schema
  output dir — either the Room Gradle plugin
  (`androidx.room` / `room { schemaDirectory("$projectDir/schemas") }`) or a KSP
  arg: `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`. Commit the
  generated `schemas/**` JSON.
- Add a **migration test** using `androidx.room:room-testing` `MigrationTestHelper`
  verifying `1 → 2` (session data preserved, `task_recipes` created). If Phase 12
  has already landed its migration, test it; otherwise scaffold the harness and a
  v1 schema so Phase 12 can drop its test in.

### 6. Aggregate + docs (+ optional)
- Ensure `./gradlew check` runs unit tests + `lintDebug` + `detekt` + `ktlintCheck`
  (wire task deps if AGP's `check` doesn't already include them).
- **Optional / document as stretch, don't block:** Kover (`org.jetbrains.kotlinx.kover`
  ≈ 0.8.x) for unit-test coverage; `.github/dependabot.yml` (gradle ecosystem,
  weekly); a `pre-commit` hook running `ktlintCheck`; Gradle config-cache toggle.
- Update root `README.md` with a **"Developer / CI"** section (the `check`
  command, how to run each tool, how to update baselines) and add a Phase-13
  progress entry to `CLAUDE.md`.

---

## Coordination note (Phase 12 in flight)
Phase 12 (session UI + task memory) edits `app/build.gradle.kts`,
`gradle/libs.versions.toml`, and bumps the Room DB (`data/AssistDatabase.kt`,
`version = 2` + `Migration(1,2)`). This phase edits the same three. **Run this
after Phase 12 merges** for a clean integration. If forced to overlap:
union-resolve the catalog/build files, and confirm the Room schema-export flag +
migration test agree with Phase 12's actual migration (don't fight over
`exportSchema`/`version`).

## Acceptance criteria
- On a test branch/PR, **CI is green**: `assembleDebug`, `testDebugUnitTest` (122+),
  `lintDebug`, `detekt`, `ktlintCheck` all pass on `ubuntu-latest`.
- `./gradlew check` runs all of the above locally and passes.
- Baselines (detekt, ktlint if used, lint) are committed so **existing code passes
  but new violations fail** — no mass reformat in this phase.
- `schemas/` is generated + committed; a `MigrationTestHelper` `1→2` test passes
  (or a ready harness if 12 hasn't landed).
- No secrets committed; the instrumented smoke test still skips cleanly with no key.
- `README.md` "Developer / CI" section + `CLAUDE.md` entry added.

## Verification
```bash
./gradlew assembleDebug testDebugUnitTest lintDebug detekt ktlintCheck
./gradlew check
# then push a branch / open a PR and confirm the Actions run is green
```

## Human checkpoints
- If the emulator CI job or an optional `ANTHROPIC_API_KEY` repo secret is wanted,
  the user must add the secret in GitHub settings (don't add it to code).
