# Architecture (shared reference)

Read this before executing any phase. It defines the runtime model, the tool
catalog, and the module boundaries every phase must respect.

## The core loop

```
              ┌──────────────────────── AgentLoop (agent/) ───────────────────────┐
 user intent  │  build request  →  Claude (llm/)  →  tool_use blocks              │
 (voice/text) │        ▲                                   │                       │
              │        │ tool_result (+ new screen state)  ▼                       │
              │  Session/context (data/)         ToolRouter → DeviceTools          │
              │        ▲                                   │                       │
              │        └─────────── screen state ──────────┤                       │
              └───────────────────────────────────────────┼───────────────────────┘
                                                           ▼
                                        AccessibilityService (service/) → gestures
                                                           │
                                              Overlay (overlay/) shows the exchange
```

One "turn": the agent sends the conversation (system prompt + history + latest
screen state) to Claude. Claude replies with text and/or one or more `tool_use`
blocks. The `ToolRouter` executes each tool via the `AccessibilityService`,
captures the resulting screen state, and returns `tool_result` blocks. Repeat
until Claude emits no tool calls (task done) or the user interrupts.

## Perception strategy (hybrid — locked)

- **Default:** serialize the accessibility node tree (`getRootInActiveWindow()`)
  to a compact JSON/outline: for each node, `{id, role, text, contentDesc,
  bounds, clickable, editable, scrollable}`. Cheap, precise, no image tokens.
- **On demand:** the model calls `take_screenshot` when the tree is insufficient
  (canvas/WebView/games/visual judgement). Screenshot returned as a base64 image
  block. Old screenshots are dropped from context aggressively (see context mgmt).
- Elements are addressed by a **stable per-frame integer id** assigned during
  serialization, mapping back to an `AccessibilityNodeInfo`. The model clicks
  `id`, not raw coordinates, whenever a node exists; coordinate gestures are the
  fallback.

## Tool catalog (the model's action space)

Defined once in `agent/` as `AgentTool` definitions (name + JSON schema +
description) and implemented in `service/`. Phases 03/06 build these.

Perception & control:
- `get_screen_state()` → serialized a11y tree of the foreground window.
- `take_screenshot()` → base64 PNG of current screen (API 30+).
- `tap(element_id)` / `tap_xy(x, y)`
- `long_press(element_id)` / `long_press_xy(x, y)`
- `swipe(direction, distance?)` / `swipe_xy(x1,y1,x2,y2,duration_ms?)`
- `scroll(element_id|direction)`
- `set_text(element_id, text)` — focus + input text
- `press_key(key)` — BACK, HOME, RECENTS, ENTER, notifications, quick settings
- `open_app(package_or_label)` — launch by package name or resolved label
- `wait(ms)` — allow UI to settle / animations / loading

User interaction:
- `say(text)` — speak via TTS (see voice phase); also rendered in overlay.
- `ask(question)` — speak + block for a user reply (voice or tap), returns answer.
- `finish(summary)` — end the task; speak summary.

Context/economy (model-controllable — satisfies "compact/drop screenshots"):
- `drop_old_screenshots(keep_last?)` — request context editing to clear stale
  images/tool results.
- `compact_conversation()` — request server-side compaction / local summary.
- `note(text)` — write a durable scratch note into the session (survives compaction).

Every tool call and result is emitted on an event bus the overlay subscribes to.

## LLM abstraction (model-agnostic; concrete = Claude)

Interface (Kotlin, in `llm/`):

```kotlin
interface LlmClient {
    /** One assistant turn. Streams events; resolves to the full assistant message. */
    suspend fun send(request: LlmRequest, onEvent: (LlmStreamEvent) -> Unit): LlmResponse
    suspend fun countTokens(request: LlmRequest): Int
}

data class LlmRequest(
    val model: String,
    val system: List<SystemBlock>,        // cacheable
    val messages: List<LlmMessage>,       // user/assistant/tool_result history
    val tools: List<ToolDef>,
    val maxTokens: Int,
    val effort: Effort? = null,           // low|medium|high|xhigh|max
    val thinkingAdaptive: Boolean = true,
)

sealed interface LlmStreamEvent { /* TextDelta, ThinkingDelta, ToolUseStart, Usage, ... */ }
data class LlmResponse(
    val text: String,
    val toolCalls: List<ToolCall>,
    val stopReason: String,
    val usage: Usage,                     // input/output/cacheRead/cacheWrite tokens
)
```

The **concrete Claude implementation** (`llm/anthropic/`) targets:
- Model default `claude-opus-4-8` (configurable; route cheap steps to
  `claude-haiku-4-5` or `claude-sonnet-5`).
- Adaptive thinking (`thinking: {type: "adaptive"}`), `output_config.effort`.
- **Prompt caching** on the system prompt + tool definitions (stable prefix).
- **Vision** via base64 image content blocks for screenshots.
- **Tool use** loop (`stop_reason == "tool_use"`).
- **Context editing** (`clear_tool_uses_20250919`) and **compaction**
  (`compact_20260112`) to implement `drop_old_screenshots` / `compact_conversation`.
- Streaming (for interruptibility and live overlay).

See `phases/phase-04-llm-claude.md` for the exact SDK-vs-REST decision and the
concrete request shapes. Do NOT hardcode Anthropic types outside `llm/anthropic/`.

## Sessions, usage, cost (data/)

Room DB persists sessions, messages, tool calls, token usage, and cost.
The agent has tools/state to introspect its own context size, usage, and cost,
and the user can start/resume/switch sessions by voice or UI. See phase-05.

## Module / package layout (single Gradle module `app`)

```
com.assist
├── service/     AccessibilityService, node serialization, gesture dispatch
├── llm/         LlmClient interface + models  (llm/anthropic/ = Claude impl)
├── agent/       AgentLoop, ToolRouter, AgentTool defs, event bus
├── data/        Room entities/DAOs, repositories (sessions, usage, cost)
├── voice/       SpeechRecognizer (STT), TextToSpeech, VAD, wake word
├── overlay/     Foreground service + Compose overlay (SYSTEM_ALERT_WINDOW)
├── ui/          Settings, session list, onboarding (permissions)
├── di/          Hilt modules
└── prompt/      System prompt assembly (phase-10)
```

**Boundary rule for parallel work:** a phase only creates/edits files under its
own package(s) plus shared interfaces it is explicitly assigned. Cross-package
contracts (interfaces/data classes) are defined in the phase that owns the
consumer and imported by others — see each phase's "Contracts I own / consume".

## Safety (cross-cutting — do not skip)

The model acts with the user's full authority and reads untrusted on-screen
text (prompt-injection surface). Enforce **confirmation gates** for irreversible
/ sensitive actions: sending messages, payments, deletions, installs,
calls, and any `set_text` into password fields. Implemented as a policy in the
`ToolRouter` (phase-06) that forces an `ask()` before executing gated actions.
