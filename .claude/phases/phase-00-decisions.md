# Phase 00 — Decisions & Feasibility

**Status:** locked (this is the record; no code).

## Verdict
The proposed system is buildable as a phone-native "computer use" agent. The
hard parts are cost/latency and safety, not platform capability.

## Locked decisions
1. **Distribution:** personal sideload via `adb`. No Google Play. (Play prohibits
   general-purpose AccessibilityService automation; sideload has no such limit.)
2. **Dev target:** physical **Pixel 7 Pro** (USB debugging) is the source of
   truth. A Google-Play-image emulator is used only for fast UI inner loops.
3. **Perception:** **hybrid** — accessibility node tree by default, screenshot
   on model request. (See ARCHITECTURE.md → Perception strategy.)
4. **Build/debug loop:** `./gradlew` + `adb` + `logcat` from Bash. No MCP
   required; may add an ADB MCP later as a convenience, not a dependency.
5. **LLM:** model-agnostic `LlmClient`, concrete Anthropic Claude implementation,
   tested against the real API. Default model `claude-opus-4-8`.

## Known risks & mitigations (carry into design)
- **Cost/latency:** multi-step tasks = many vision round-trips. Mitigate with
  a11y-tree-first perception, prompt caching, dropping stale screenshots,
  context editing/compaction, and per-step model routing (Haiku/Sonnet for
  simple steps).
- **Prompt injection:** on-screen text is untrusted. Mitigate with confirmation
  gates on irreversible/sensitive actions in the ToolRouter (ARCHITECTURE.md → Safety).
- **API key on device:** personal use → `EncryptedSharedPreferences`. A hosted
  proxy is out of scope.
- **Accessibility gaps:** some apps block a11y, WebViews are messy → screenshot
  fallback + coordinate gestures.
- **Emulator realism:** real apps/accessibility differ on emulators → validate
  perception/action on the physical Pixel.

## Human checkpoints referenced by later phases
- Provide an `ANTHROPIC_API_KEY` (used in phase-04 real-model tests).
- Put the Pixel 7 Pro in Developer Mode + USB debugging, authorize the host
  (phase-11). Manually enable the Accessibility service in system settings
  (cannot be granted programmatically) — phase-03/11.
