# Phase 03 — Accessibility Service + Device Tools

**Objective:** implement the `AccessibilityService` and the concrete device
actions that the agent's tools map onto: perceive the screen and perform
gestures/interactions.

**Prerequisites:** phase-02. **Parallelizable with 04 and 05** (owns `service/`).

**Required reading:** Android `AccessibilityService` reference —
https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
and `AccessibilityNodeInfo`, `GestureDescription`. Key APIs you will use:
`getRootInActiveWindow()`, `AccessibilityNodeInfo.performAction(...)`,
`dispatchGesture(...)`, `performGlobalAction(...)`, `takeScreenshot(...)` (API 30+),
`AccessibilityServiceInfo` flags (`flagRetrieveInteractiveWindows`,
`flagRequestFilterKeyEvents` as needed), and `onAccessibilityEvent`.

## Deliverables (all under `com.wisp.service`)
1. **`AssistAccessibilityService : AccessibilityService`** registered in the
   manifest with `<meta-data>` pointing to `res/xml/accessibility_service_config.xml`
   (capabilities: retrieve window content, perform gestures, can take screenshot;
   `canRetrieveWindowContent=true`, appropriate `accessibilityFlags`). Holds a
   process-wide singleton reference (`instance`) so the agent can reach it.
2. **Node serialization** `ScreenSerializer`:
   `fun serialize(root: AccessibilityNodeInfo?): ScreenState`.
   - Walks the tree (bounded depth/count to cap tokens; document the cap).
   - Emits `ScreenState(elements: List<UiElement>, appPackage: String, window: ...)`
     where `UiElement(id: Int, role, text, contentDesc, boundsInScreen,
     clickable, editable, scrollable, focused)`.
   - Assigns a **stable per-frame integer `id`** and keeps an `id -> NodeInfo`
     map valid until the next serialize (recycle old nodes).
   - Provides a compact string/JSON rendering for the LLM.
3. **`DeviceController`** — the concrete action surface (this is what the
   ToolRouter in phase-06 calls). All methods `suspend` and return a
   `ToolOutcome(success, message)`:
   - `getScreenState(): ScreenState`
   - `takeScreenshot(): Bitmap` (wrap `takeScreenshot` callback in a coroutine)
   - `tap(elementId)` / `tapXy(x,y)` — click via `ACTION_CLICK` when the node is
     clickable, else `dispatchGesture` tap at bounds center.
   - `longPress(...)`, `swipe(direction/xy)`, `scroll(elementId|direction)`
   - `setText(elementId, text)` — focus + `ACTION_SET_TEXT` (or clipboard paste
     fallback); flag password fields to the safety layer.
   - `pressKey(key)` → `performGlobalAction` (BACK/HOME/RECENTS/NOTIFICATIONS/
     QUICK_SETTINGS) or key event.
   - `openApp(packageOrLabel)` — resolve label→package via `PackageManager`,
     launch intent.
   - `wait(ms)`.
4. **`GestureFactory`** helpers building `GestureDescription` for tap/long-press/
   swipe from screen coordinates.
5. **`ScreenChangeSignals`** — expose a `Flow<Unit>` that fires on relevant
   `onAccessibilityEvent` (window state/content changed) so callers can await UI
   settle after an action (used by the agent loop to know when to re-perceive).
6. Unit-testable pieces (`ScreenSerializer` node→model mapping, bounds math,
   label resolution) split from Android framework calls so `src/test/` can cover
   them with fakes.

## Contracts I own (consumed by phase-06)
- `com.wisp.service.ScreenState`, `UiElement`, `ToolOutcome`
- `com.wisp.service.DeviceController` (interface + impl)
- `com.wisp.service.AssistAccessibilityService.instance`

## Steps
1. Service + config XML + manifest registration; verify it appears in system
   Accessibility settings and can be enabled (`scripts/enable-service.sh`).
2. `ScreenSerializer` + `ScreenState` model; log a serialized dump of the current
   screen to logcat to eyeball quality on 2–3 real apps.
3. `GestureFactory` + `DeviceController` actions; test each with an internal
   debug trigger (e.g., a hidden button or `adb` broadcast that taps element 0,
   swipes, opens an app).
4. `ScreenChangeSignals`.

## Acceptance criteria
- Enabling the service in settings does not crash; logcat shows serialized screen
  state for the foreground app with sensible element ids/bounds/flags.
- A debug command can: open Settings app, tap an element by id, swipe up, and
  read back the new screen state.
- `takeScreenshot()` returns a non-null bitmap on the device/emulator (API 35).
- Unit tests cover serialization mapping and bounds/center math.

## Verification
```bash
bash scripts/install.sh
bash scripts/enable-service.sh          # then confirm toggle in Settings
adb shell am broadcast -a com.wisp.DEBUG_DUMP_SCREEN   # if you add a debug receiver
bash scripts/logcat.sh                  # inspect ScreenState output
```

## Human checkpoint
The Accessibility service must be enabled **manually** in system settings on a
non-rooted device (Pixel). The emulator path in `enable-service.sh` may set the
secure setting directly.

## Notes / pitfalls
- Recycle `AccessibilityNodeInfo` objects; leaks cause ANRs.
- WebViews/canvas: nodes may be sparse — that's expected; screenshot fallback
  covers it (used by the model, not this phase).
- Cap serialized element count (e.g. ~150) and text length to control tokens.
