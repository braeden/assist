# Phase 05 — Session DB + Context Management

**Objective:** persist LLM sessions locally; track context window usage and cost;
let the user (and agent) start/resume/switch sessions; give the agent tools to
introspect and shrink its own context.

**Prerequisites:** phase-02. **Parallelizable with 03 and 04** (owns `data/`).

## Deliverables (all under `com.assist.data`)
1. **Room schema:**
   - `SessionEntity(id, title, createdAt, updatedAt, modelDefault, status,
     systemPromptVersion)`
   - `MessageEntity(id, sessionId, role, seq, createdAt, contentJson, kind)` —
     `role ∈ {user, assistant, tool_result, system-note}`; `contentJson` stores
     text + references to images (store screenshots as files in app storage, not
     blobs in the row; keep a `MediaEntity` for image files).
   - `ToolCallEntity(id, sessionId, messageId, name, argsJson, resultJson,
     success, durationMs, createdAt)`
   - `UsageEntity(id, sessionId, messageId, model, inputTokens, outputTokens,
     cacheReadTokens, cacheWriteTokens, costUsd, createdAt)`
   - `MediaEntity(id, sessionId, path, kind, createdAt, dropped: Boolean)`
   - `NoteEntity(id, sessionId, text, createdAt)` (durable scratch notes,
     survive compaction).
2. **DAOs + `SessionRepository`** exposing:
   - `createSession(...)`, `getSession(id)`, `listSessions(): Flow<...>`,
     `resumeSession(id)`, `renameSession(id, title)`, `endSession(id)`.
   - `appendMessage(...)`, `appendToolCall(...)`, `recordUsage(...)`,
     `addNote(...)`, `saveScreenshot(bytes): MediaEntity`.
   - **Reconstruction**: `buildLlmMessages(sessionId): List<LlmMessage>` that
     rebuilds the conversation for a fresh request, honoring dropped media (omit
     or replace dropped screenshots with a placeholder text block).
3. **Cost accounting** `CostCalculator`: model-id → input/output/cache prices
   (table; update from pricing docs) → `costUsd` per usage row. Aggregate
   `sessionCost(sessionId)` and `todaySpend()`.
4. **Context accounting** `ContextTracker`:
   - Current estimated context tokens for a session (sum of live messages;
     dropped media excluded), against the model's window.
   - `contextStatus(sessionId): ContextStatus(usedTokens, windowTokens,
     costUsd, screenshotCount)` for the agent's introspection tools and the
     overlay HUD.
5. **Context-shrink operations** (invoked by agent tools in phase-06):
   - `markScreenshotsDropped(sessionId, keepLast)` → sets `MediaEntity.dropped`.
   - `summarizeAndCompact(sessionId, summary)` → replace a span of messages with
     a summary note (local compaction), or record that server-side compaction
     happened. Keep notes intact.

## Contracts I own (consumed by phase-06/07)
- `com.assist.data.SessionRepository` + all entities/DAOs
- `ContextStatus`, `ContextTracker`, `CostCalculator`
- Media storage layout under app-private files dir

## Steps
1. Entities + DAOs + Room DB; migration strategy (destructive for now, documented).
2. `SessionRepository` CRUD + append APIs; unit tests with in-memory Room.
3. `CostCalculator` + `ContextTracker` with unit tests (fixed pricing fixtures).
4. `buildLlmMessages` reconstruction incl. dropped-media handling; unit tests.

## Acceptance criteria
- Create → append messages/tool calls/usage → read back reconstructs a coherent
  `List<LlmMessage>`; dropped screenshots are omitted/placeholdered.
- `sessionCost` and `contextStatus` compute correctly against fixtures.
- `listSessions()` emits updates as sessions change (Flow).
- Unit tests pass with in-memory Room.

## Verification
```bash
./gradlew :app:testDebugUnitTest --tests "com.assist.data.*"
```

## Notes
- Don't store raw API keys or full base64 images in the DB. Images live as files;
  rows hold paths. Redact message content from logs.
- `buildLlmMessages` is the seam that makes "drop screenshots"/"compact" real:
  the agent mutates DB state, and the next request is rebuilt cheaper.
