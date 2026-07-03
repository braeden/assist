# Anthropic Claude client (`com.wisp.llm.anthropic`)

Concrete `LlmClient` implementation for the Assist agent. All Anthropic-specific
types are confined to this package; nothing here leaks into `com.wisp.llm`.

## Decision: direct REST (OkHttp + kotlinx.serialization), not the Java SDK

**Chosen: B — direct HTTPS via OkHttp + kotlinx.serialization.**

The phase-04 spec asks us to try the official `com.anthropic:anthropic-java` SDK
first and fall back to a REST client if it doesn't run cleanly on `minSdk 30`.
We went with REST. Rationale:

1. **minSdk-30 fit, verified by build.** OkHttp 4.12 targets Android API 21+ and
   needs no core-library desugaring. The Anthropic Java SDK is built for server
   JVMs — it bundles Jackson (reflection-heavy) and other transitive deps whose
   Android/`minSdk 30` behavior we cannot verify here (the single emulator is
   reserved for another agent, so "runs cleanly on device" is unprovable in this
   worktree). REST removes that risk entirely: no desugaring, no method-count or
   R8 surprises, one small well-known Android dependency.
2. **Streaming + cooperative cancellation.** Interruptibility (phase-08) requires
   that cancelling the calling coroutine promptly aborts the in-flight HTTP
   stream. With OkHttp we own the `Call` and wire coroutine cancellation directly
   to `call.cancel()` — exact, prompt, and easy to reason about. The SDK's
   streaming abstractions hide the call and make this harder to guarantee.
3. **Beta features are just headers + JSON.** Adaptive thinking, `output_config.
   effort`, prompt-cache `cache_control`, context editing
   (`clear_tool_uses_20250919`), and compaction (`compact_20260112`) are all
   request fields / `anthropic-beta` headers. REST expresses them with a couple of
   lines of `buildJsonObject`, with no dependency on the SDK having shipped typed
   bindings for the newest betas.
4. **Fewer/smaller deps.** `kotlinx.serialization` is already in the project, so
   REST adds only OkHttp. The SDK would pull a much larger dependency graph into
   a sideloaded personal app for no functional gain.

No desugaring was enabled — OkHttp 4.12 and kotlinx.serialization run on
`minSdk 30` without it.

## Contents

- `AnthropicLlmClient` — the `LlmClient` impl: `send()` (SSE streaming with
  cooperative cancellation), `countTokens()`, plus `dropOldScreenshots()` /
  `compactConversation()` convenience methods that map to context editing /
  compaction. Auth reads `x-api-key` from `SecretStore` per request; the key is
  never logged.
- `AnthropicRequestFactory` — maps `LlmRequest` → Messages-API JSON: adaptive
  thinking, effort, base64 image blocks (vision), tool defs, `tool_result`
  blocks, prompt-cache breakpoints, and `context_management` edits (+ the beta
  headers they require).
- `AnthropicError` — typed errors with a `retryable` flag (rate limit / overloaded
  / 5xx / network = retryable; auth / bad request = not).
- `ModelRouter` — step difficulty → model id. Default: `SIMPLE →
  claude-haiku-4-5`, `STANDARD → claude-sonnet-5`, `COMPLEX → claude-opus-4-8`
  (also the overall default). Overridable via the constructor.

## Live smoke test

`app/src/androidTest/.../AnthropicSmokeTest.kt` runs one text turn, one tool-use
turn, and one vision turn against the real API. It is gated by
`BuildConfig.ANTHROPIC_API_KEY` (injected from the `anthropicApiKey` Gradle
property or the `ANTHROPIC_API_KEY` env var) and **skips cleanly** when no key is
present. Run it with:

```bash
ANTHROPIC_API_KEY=sk-ant-... ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.wisp.llm.anthropic.AnthropicSmokeTest
```

Never commit an API key — it is read from the environment / a Gradle property at
build time only.
