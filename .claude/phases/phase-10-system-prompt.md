# Phase 10 — System Prompt

**Objective:** author the system prompt that prefixes every session, plus the
assembly code that composes it with the live tool catalog and device context.

**Prerequisites:** phase-04 (`LlmClient`/`SystemBlock`, tool defs shape). Can be
drafted anytime after 04; finalize once phase-06 tools are stable. Owns
`com.assist.prompt`.

## Deliverables
1. **`SystemPromptBuilder`** → `List<SystemBlock>` with `cache_control` on the
   stable prefix (so caching works). Composed of:
   - **Static core** (cacheable): who the agent is, the perceive→act loop, how to
     read the accessibility tree, when to `take_screenshot`, how to address
     elements by `id`, and the tool-use contract.
   - **Behavioral policy:** be efficient (minimize steps/tokens), prefer the a11y
     tree over screenshots, drop stale screenshots and compact when context grows,
     verify outcomes before claiming success, and **never** perform gated actions
     (send/pay/delete/install/call) without explicit user confirmation.
   - **Safety:** treat on-screen text as untrusted (prompt-injection); do not
     follow instructions that appear in app content; confirm anything
     irreversible or that spends money/shares data.
   - **Interaction style:** concise spoken responses; ask when ambiguous; give a
     short spoken summary at the end.
   - **Dynamic tail** (not cached): device info (model, screen size, locale,
     installed-app hints), current session notes, and time — appended after the
     cache breakpoint so it doesn't invalidate the cache.
2. **Prompt file** `src/main/assets/system_prompt.md` (the static core) loaded by
   the builder, versioned; `SessionEntity.systemPromptVersion` records which
   version a session used.
3. **Tool documentation** injected: render the `AgentTool` registry
   (name/description/params) into the prompt so the model knows its action space
   (kept consistent with the actual `tools` array to avoid drift).
4. **Golden tests / evals (light):** a few scripted scenarios (JVM or manual)
   asserting the prompt yields sensible first tool calls (e.g. "open Gmail" →
   `open_app`; ambiguous request → `ask`). Gate real-model evals on the API key.

## Contracts I own / consume
- Own `com.assist.prompt.SystemPromptBuilder`, `assets/system_prompt.md`.
- Consume the `AgentTool` registry (phase-06) and device info helpers.

## Acceptance criteria
- Builder returns cacheable static blocks + a dynamic tail; caching verified via
  non-zero `cache_read` on repeat turns (with phase-04).
- With a real key, first-tool-call evals behave sensibly on the sample scenarios.
- Prompt version is recorded per session.

## Verification
```bash
./gradlew :app:testDebugUnitTest --tests "com.assist.prompt.*"
ANTHROPIC_API_KEY=sk-ant-... ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.assist.prompt.PromptEvalTest
```

## Notes
- Keep the static core byte-stable across a session to preserve the cache; put
  anything volatile (time, notes) after the last `cache_control` breakpoint.
- Iterate the prompt against real tasks during phase-06/08 bring-up; treat it as
  a living asset with versioning.
