# Phase 07 — Overlay UI (Interaction Viz + Interrupt)

**Objective:** a floating, always-on-top UI that shows the user↔model exchange
(text + tool calls), current status, usage/cost HUD, and an interrupt control —
drawn over whatever app the agent is driving.

**Prerequisites:** phase-06 (`AgentEventBus`, `AgentLoop.interrupt`, `ContextStatus`).
**Parallelizable with 08.** Owns `com.wisp.overlay`.

## Deliverables
1. **`OverlayService`** — the single foreground service (coordinate with phase-06
   `AgentService`; one FGS + persistent notification with `specialUse` type).
   Adds a Compose overlay to the `WindowManager` using `TYPE_APPLICATION_OVERLAY`,
   `FLAG_NOT_FOCUSABLE` (so the driven app still receives input) with focusable
   toggled only when capturing a reply. Requires `SYSTEM_ALERT_WINDOW`.
2. **Compose overlay content** (host Compose in a service via a `ComposeView`
   with a `LifecycleOwner`/`SavedStateRegistryOwner`/`ViewModelStoreOwner`
   wrapper — implement the small `OverlayLifecycleOwner` helper):
   - **Bubble (collapsed):** small floating pill showing state (idle/listening/
     thinking/speaking/acting) with a mic/stop affordance; draggable.
   - **Panel (expanded):** transcript of the exchange from `AgentEventBus`
     (assistant text streaming, `tool_call` chips with args + success/fail,
     thinking indicator, confirmation prompts), plus a **HUD**: context used /
     window, session cost, screenshot count (from `ContextStatus`).
   - **Interrupt button** → `AgentLoop.interrupt()` (barge-in). Also a
     "type instead" affordance for a keyboard reply to `ask()`.
   - Buttons: new session / switch session (list from `SessionRepository`),
     compact-now, drop-screenshots-now (call the same repo/LLM ops the tools use).
3. **`OverlayController`** — collects `AgentEventBus` into overlay UI state
   (`StateFlow<OverlayUiState>`); throttles high-frequency text deltas.
4. Enable/disable overlay from `MainActivity` (start/stop `OverlayService`) and
   an onboarding step to grant "Display over other apps".

## Contracts I consume
- `com.wisp.agent.AgentEventBus`, `AgentEvent`, `AgentLoop.interrupt()`
- `com.wisp.data.SessionRepository`, `ContextStatus`

## Steps
1. FGS + `WindowManager` add/remove of a trivial Compose bubble; verify it draws
   over other apps and doesn't steal touches from the driven app.
2. Collapsed/expanded states + drag; wire state from a fake event flow first.
3. Bind to the real `AgentEventBus`; render transcript + tool chips + HUD.
4. Interrupt + session controls + typed-reply path to `ask()`.

## Acceptance criteria
- Overlay renders above a third-party app (e.g. Settings) without blocking its
  input while collapsed.
- During a live agent run, the panel streams assistant text and shows tool-call
  chips in order with results, and the HUD updates cost/context.
- Interrupt button stops an in-flight task (verified with phase-06 loop).
- Confirmation prompts (`AwaitingConfirmation`) render with Yes/No that feed back
  into `ActionGate`.

## Verification
```bash
bash scripts/install.sh   # grant overlay permission in onboarding
# start a task (voice later; for now the phase-06 debug intent) and watch overlay
```

## Notes / pitfalls
- Compose-in-a-window needs a manual lifecycle/saved-state owner; without it the
  `ComposeView` crashes. Provide `OverlayLifecycleOwner`.
- Keep the window non-focusable except while actively capturing a typed reply,
  or you'll block the driven app.
- Respect `POST_NOTIFICATIONS`; FGS needs an ongoing notification.
