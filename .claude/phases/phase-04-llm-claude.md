# Phase 04 — LLM Abstraction + Claude Client

**Objective:** a model-agnostic `LlmClient` and a concrete Anthropic Claude
implementation, tested against the **real** API.

**Prerequisites:** phase-02 (`SecretStore`). **Parallelizable with 03 and 05**
(owns `llm/`).

**Reference:** load the `claude-api` skill / Anthropic Java SDK docs before
writing request code. Kotlin uses the **Java SDK** (`com.anthropic:anthropic-java`).
Model default `claude-opus-4-8`.

## First task — SDK vs direct HTTP spike (decide & document)
Evaluate two options and pick one; record the decision + rationale at the top of
`llm/anthropic/README.md`:
- **A) Official `com.anthropic:anthropic-java`** — richest (tool runner, typed
  blocks, streaming, structured outputs) but heavier on Android; verify it builds
  and runs on `minSdk 30` (check desugaring / OkHttp / any `java.time` usage).
- **B) Direct REST via Ktor or OkHttp** — lighter, full control over SSE
  streaming and cancellation (good for interruptibility), but you hand-roll
  request/response types.

Recommendation: try A first (a 1-file spike calling the real API from an
instrumented test); fall back to B if it doesn't run cleanly on device. Either
way, **all Anthropic types stay inside `llm/anthropic/`.**

## Deliverables
1. **`com.wisp.llm`** interfaces/models exactly as in ARCHITECTURE.md:
   `LlmClient`, `LlmRequest`, `LlmMessage`, `SystemBlock`, `ToolDef`, `ToolCall`,
   `LlmStreamEvent` (sealed: `TextDelta`, `ThinkingDelta`, `ToolUseStart`,
   `ToolUseArgsDelta`, `Usage`, `Done`), `LlmResponse`, `Usage`, `Effort`.
   Content blocks must support **text** and **image (base64)** for screenshots,
   and **tool_result** messages carrying text and/or images.
2. **`com.wisp.llm.anthropic.AnthropicLlmClient : LlmClient`** implementing:
   - Auth from `SecretStore` (never hardcode; header `x-api-key`).
   - `model` from `LlmRequest` (default `claude-opus-4-8`).
   - **Adaptive thinking** (`thinking: {type:"adaptive"}`) + `output_config.effort`.
   - **Streaming** with cooperative **cancellation** (coroutine cancel aborts the
     HTTP stream) — required for interruptibility (phase-08).
   - **Tool use**: send `tools`, parse `tool_use` blocks into `ToolCall`s, accept
     `tool_result` blocks (incl. images) on the next turn. Do NOT auto-execute —
     the agent loop (phase-06) owns execution.
   - **Vision**: screenshots as base64 image content blocks.
   - **Prompt caching**: `cache_control` on the system prompt + tool definitions
     (stable prefix) so repeated turns are cheap. Surface cache hit/write token
     counts in `Usage`.
   - **Context editing** (`clear_tool_uses_20250919`) and **compaction**
     (`compact_20260112`) exposed as methods the agent calls to implement
     `drop_old_screenshots` / `compact_conversation`. If preserving compaction
     state, append full response content back (per SDK guidance).
   - `countTokens()` via the count-tokens endpoint.
   - Typed error mapping (rate limit / auth / overloaded → retryable vs not).
3. **`ModelRouter`** (small) mapping a step "difficulty" to a model id so the
   agent can route simple steps to `claude-haiku-4-5` / `claude-sonnet-5`.
   Default policy documented; overridable in settings.
4. **Real-model smoke test** in `src/androidTest/` (or a JVM test if SDK path A
   runs on the JVM) gated by `ANTHROPIC_API_KEY`: one text turn, one tool-use
   turn (define a trivial `echo` tool and assert Claude calls it), one
   vision turn (send a tiny image, assert a coherent description). Skips cleanly
   when the key is absent.

## Contracts I own (consumed by phase-06/10)
- All of `com.wisp.llm.*` (interfaces + models)
- `AnthropicLlmClient`, `ModelRouter`
- DI provider for `LlmClient` (Hilt) reading `SecretStore`

## Acceptance criteria
- With a real key, the smoke test passes all three turns against the live API.
- Streaming can be cancelled mid-response (test: cancel the coroutine, assert the
  call returns promptly and no further events arrive).
- `Usage` reports cache read/write tokens; a second identical-prefix call shows
  non-zero `cache_read`.
- No Anthropic type leaks outside `llm/anthropic/`.

## Verification
```bash
ANTHROPIC_API_KEY=sk-ant-... bash scripts/install.sh   # if instrumented test
ANTHROPIC_API_KEY=sk-ant-... ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.wisp.llm.anthropic.AnthropicSmokeTest
```

## Human checkpoint
Requires an `ANTHROPIC_API_KEY` from the user to run real-model tests. Ask for
one; do not commit it (env var / gitignored `local.properties`).
