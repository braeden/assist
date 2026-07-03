# Phase 06 — Agent Orchestration Loop

**Objective:** tie perception (03), the LLM (04), and persistence (05) into the
running agent: build requests, run the tool-use loop, execute device tools with
safety gates, persist everything, and emit events for the overlay.

**Prerequisites:** phases 03, 04, 05 complete. **Integration phase — sequential.**

## Deliverables (all under `com.wisp.agent`)
1. **`AgentTool` registry** — the tool catalog from ARCHITECTURE.md as
   `ToolDef`s (name, JSON schema, description) fed to `LlmRequest.tools`. One
   source of truth; grouped: perception/control, user-interaction, context.
2. **`ToolRouter`** — maps a `ToolCall` (from `LlmResponse`) to an execution:
   - control/perception → `DeviceController` (phase-03),
   - `say`/`ask` → voice interface (phase-08; behind an interface `UserIo` so
     this phase can ship with a stub/log impl and phase-08 swaps it in),
   - `drop_old_screenshots`/`compact_conversation`/`note` → `SessionRepository`
     (phase-05) + `AnthropicLlmClient` context-editing/compaction (phase-04),
   - `get_screen_state`/`take_screenshot` → capture, persist media, return as
     tool_result content (text tree and/or base64 image).
   - Returns `tool_result` blocks (with images where relevant) for the next turn.
3. **Safety policy `ActionGate`** — before executing gated tools (send, pay,
   delete, install, call, password `set_text`, and any action classified
   irreversible), force a confirmation: emit an `ask()` and only proceed on
   explicit user "yes". Configurable allowlist in settings. Log every gated
   decision.
4. **`AgentLoop`** — the core coroutine:
   ```
   start(sessionId, userIntent):
     append user message; loop:
       req = build(system prompt [phase-10], history [buildLlmMessages],
                   tools, model = ModelRouter.pick(step))
       resp = llm.send(req, onEvent = emit to bus)   // cancellable
       persist assistant message + usage
       if resp.toolCalls empty or stopReason == end_turn/finish -> break
       for each toolCall: ActionGate -> ToolRouter.execute -> persist -> collect result
       after actions: await screen settle (ScreenChangeSignals) or wait()
   ```
   - **Interruptible**: expose `interrupt()` that cancels the in-flight
     `llm.send` and any running gesture, then transitions to "listening" so the
     user can redirect (drives phase-08 barge-in).
   - **Auto-perception**: after actions, attach fresh `get_screen_state` (tree)
     to the next turn automatically; only send screenshots when the model asked.
   - **Loop guards**: max steps per task, no-progress detection (same screen +
     same action N times → ask user), cost/context ceiling → auto
     `compact_conversation` then continue or ask.
5. **`AgentEventBus`** — `SharedFlow<AgentEvent>` (`AssistantText`,
   `ToolCallStarted/Finished`, `Thinking`, `AwaitingConfirmation`, `Listening`,
   `Speaking`, `UsageUpdated`, `Error`, `Finished`). The overlay (07) and voice
   (08) subscribe.
6. **`AgentService`** — a foreground service hosting `AgentLoop` so tasks survive
   backgrounding while driving other apps (shares the foreground-service +
   notification with the overlay in phase-07; coordinate the single FGS).
7. **`UserIo` interface** (`suspend fun say(text)`, `suspend fun ask(q): String`)
   with a logging stub here; real impl in phase-08.

## Contracts I own (consumed by 07/08)
- `com.wisp.agent.AgentLoop`, `AgentEvent`, `AgentEventBus`
- `com.wisp.agent.UserIo` (interface)
- `AgentTool`/`ToolDef` registry, `ActionGate`

## Steps
1. Tool registry + `ToolRouter` with `DeviceController` + stub `UserIo`.
2. `ActionGate` + policy; unit test classification.
3. `AgentLoop` with event bus; loop guards; interrupt.
4. Wire an end-to-end **text-in** run (no voice yet): a debug entry point takes a
   typed intent and runs the loop on the emulator/device against the real model.

## Acceptance criteria
- End-to-end on device: typed intent "open the Clock app and start a 1-minute
  timer" (or similar) completes via real tool calls, persisted, with events
  streaming on the bus.
- `interrupt()` stops mid-task within ~1s and leaves the session resumable.
- A gated action (e.g. "send a text") triggers a confirmation before executing.
- Context ceiling triggers an automatic compaction/drop and the run continues.

## Verification
```bash
ANTHROPIC_API_KEY=sk-ant-... bash scripts/install.sh
# trigger the debug intent (adb broadcast or a temporary MainActivity text box):
adb shell am broadcast -a com.wisp.DEBUG_RUN --es intent "open the Clock app and start a 1 minute timer"
bash scripts/logcat.sh   # watch AgentLoop + tool calls
```

## Human checkpoint
Real device recommended (phase-11) to validate perception/action on real apps.
Needs `ANTHROPIC_API_KEY`.

## Handoff notes
After this phase: **07 (overlay) and 08 (voice) run in parallel** — both consume
`AgentEventBus`; 08 provides the real `UserIo`.
