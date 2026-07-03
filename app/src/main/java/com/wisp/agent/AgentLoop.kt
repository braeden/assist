package com.wisp.agent

import android.util.Log
import com.wisp.data.AgentModel
import com.wisp.data.SessionRepository
import com.wisp.data.SettingsStore
import com.wisp.data.TaskMemoryRepository
import com.wisp.di.AppScope
import com.wisp.llm.ContentBlock
import com.wisp.llm.ContextManagement
import com.wisp.llm.Effort
import com.wisp.llm.LlmClient
import com.wisp.llm.LlmRequest
import com.wisp.llm.LlmStreamEvent
import com.wisp.llm.Role
import com.wisp.llm.Speed
import com.wisp.llm.ToolCall
import com.wisp.service.DeviceController
import com.wisp.service.ScreenChangeSignals
import com.wisp.service.ScreenState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * The agent orchestration spine: builds requests, runs the tool-use loop with
 * safety gates + auto-perception, persists everything, and streams events.
 * Cancellable — [interrupt] aborts the in-flight `llm.send`/gesture within ~1s and
 * idles the agent ([AgentEvent.Finished]); "Listening" is only ever shown while a
 * capture is genuinely open (ask / dictation), never as an aspirational state.
 */
@Singleton
class AgentLoop
    @Inject
    constructor(
        private val llm: LlmClient,
        private val repository: SessionRepository,
        private val device: DeviceController,
        private val router: ToolRouter,
        private val actionGate: ActionGate,
        private val bus: AgentEventBus,
        private val userIo: UserIo,
        private val promptProvider: SystemPromptProvider,
        private val screenChangeSignals: ScreenChangeSignals,
        private val settings: SettingsStore,
        private val taskMemory: TaskMemoryRepository,
        private val json: Json,
        @AppScope private val scope: CoroutineScope,
    ) {
        @Volatile
        private var currentJob: Job? = null

        /** The outline text the model last saw, for the "(unchanged)" dedupe. */
        private var lastSentOutline: String? = null

        /** True while a run is active. */
        val isRunning: Boolean get() = currentJob?.isActive == true

        /**
         * Start a run for [sessionId] with [userIntent]. Cancels any in-flight run
         * first. Returns the [Job] (join it to await completion). Runs on the app
         * scope so it survives the triggering caller.
         */
        fun start(
            sessionId: Long,
            userIntent: String,
        ): Job {
            currentJob?.cancel(CancellationException("superseded by a new run"))
            var job: Job? = null
            job =
                scope.launch(CoroutineName("AgentLoop")) {
                    try {
                        runLoop(sessionId, userIntent)
                    } catch (c: CancellationException) {
                        Log.i(TAG, "loop cancelled")
                        // A user interrupt idles the agent (emitting "Listening" here left
                        // the UI claiming an open mic that wasn't). When the cancel came
                        // from being superseded, stay silent — the new run's own events
                        // drive the UI and must not be stamped finished by the old job.
                        if (currentJob === job) bus.emit(AgentEvent.Finished("Interrupted"))
                        throw c
                    } catch (t: Throwable) {
                        Log.e(TAG, "agent loop error", t)
                        bus.emit(AgentEvent.Error(t.message ?: t::class.simpleName ?: "error"))
                    }
                }
            currentJob = job
            return job
        }

        /**
         * Interrupt the running task: cancels the in-flight LLM stream / gesture and
         * idles the agent. Returns promptly; the loop unwinds within ~1s because
         * `llm.send` aborts its HTTP call on cancellation.
         */
        fun interrupt() {
            Log.i(TAG, "interrupt requested")
            currentJob?.cancel(CancellationException("interrupted by user"))
        }

        // --- Core loop ----------------------------------------------------------

        private suspend fun runLoop(
            sessionId: Long,
            userIntent: String,
        ) {
            bus.emit(AgentEvent.Started(sessionId, userIntent))

            // A marker must only ever refer to an outline in *this* conversation.
            lastSentOutline = null
            var latestScreen = safeGetScreen()
            repository.appendMessage(
                sessionId,
                Role.USER,
                buildList {
                    add(ContentBlock.Text(userIntent))
                    add(ContentBlock.Text(screenBlockText(latestScreen)))
                    // Recipe recall is pushed, not pulled: matching recipes ride along
                    // in the first turn so the model never spends a tool call browsing
                    // memory speculatively (it reads a file only on a real match).
                    recallBlock(userIntent)?.let { add(it) }
                },
            )

            var pendingContext: ContextManagement? = null
            val progress = NoProgressTracker()
            var step = 0
            // True while the last thing the user *heard* is still current — i.e. a
            // `say` succeeded and no device action has happened since. Used to keep
            // `finish` silent when the model already announced the outcome (whether
            // in the same turn or a previous one), while still speaking summaries
            // that follow further actions (those are genuinely new information).
            var spokenIsCurrent = false

            while (true) {
                coroutineContext.ensureActive()
                step++
                if (step > MAX_STEPS) {
                    finishRun(sessionId, "Reached the step limit without completing the task.")
                    return
                }

                // The session row is the model's source of truth, re-read every step so
                // a mid-session swap (from the transcript screen) applies on the next
                // request. A swap busts the prompt cache once, then re-caches. Fast mode
                // only applies on models that support it (Opus 4.8/4.7).
                val model =
                    repository.getSession(sessionId)?.modelDefault
                        ?: settings.agentModel.value.modelId
                val speed =
                    if (settings.isFastModeEnabled() && AgentModel.supportsFast(model)) {
                        Speed.FAST
                    } else {
                        Speed.STANDARD
                    }
                // Per-model: server tools (web_search/web_fetch/advisor) vary by
                // model support. Stable within a model, so cache-safe.
                val tools = AgentTools.catalog(model)
                val toolNames = tools.map { it.name }
                // A mid-session model swap can strand server-tool activity from a
                // tool the new model doesn't get (e.g. advisor results after
                // sonnet→opus) — prune it rather than let the request 400.
                val messages =
                    ServerToolPruner.prune(
                        json,
                        repository.buildLlmMessages(sessionId),
                        toolNames.toSet(),
                    )
                val request =
                    LlmRequest(
                        model = model,
                        system =
                            promptProvider.system(
                                SystemPromptContext(sessionId, userIntent, toolNames),
                            ),
                        messages = messages,
                        tools = tools,
                        maxTokens = MAX_TOKENS,
                        effort = Effort.MEDIUM,
                        thinkingAdaptive = true,
                        contextManagement = pendingContext,
                        speed = speed,
                    )
                pendingContext = null

                val response = llm.send(request) { event -> onStream(event) }
                repository.appendMessage(sessionId, Role.ASSISTANT, response.content)
                repository.recordUsage(
                    sessionId,
                    messageId = null,
                    model = model,
                    usage = response.usage,
                )
                bus.emit(AgentEvent.UsageUpdated(response.usage))

                if (response.toolCalls.isEmpty()) {
                    // Server tools (web search/fetch, advisor) run inside the
                    // provider's own loop; pause_turn means it hit its iteration
                    // cap. The assistant turn is already persisted — re-sending
                    // resumes the server-side work where it left off.
                    if (response.stopReason == PAUSE_TURN) {
                        Log.i(TAG, "pause_turn — resuming server-tool turn")
                        continue
                    }
                    // No tool calls: the model is done (or just talking). Treat as
                    // finish; skip TTS if a `say` already delivered the outcome.
                    val summary = response.text.ifBlank { null }
                    if (!spokenIsCurrent) summary?.let { userIo.say(it) }
                    bus.emit(AgentEvent.Finished(summary))
                    return
                }

                val resultBlocks = mutableListOf<ContentBlock>()
                var acted = false
                var producedPerception = false
                var finished = false
                var finishSummary: String? = null

                for (call in response.toolCalls) {
                    coroutineContext.ensureActive()
                    bus.emit(AgentEvent.ToolCallStarted(call.id, call.name, call.argumentsJson))

                    val exec = gateAndExecute(sessionId, call, latestScreen)
                    if (call.name == AgentTools.SAY && exec.success) {
                        spokenIsCurrent = true
                    } else if (exec.didAct) {
                        spokenIsCurrent = false
                    }
                    repository.appendToolCall(
                        sessionId = sessionId,
                        messageId = null,
                        name = call.name,
                        argsJson = call.argumentsJson,
                        resultJson = exec.message,
                        success = exec.success,
                        durationMs = 0,
                    )
                    bus.emit(
                        AgentEvent.ToolCallFinished(call.id, call.name, exec.success, exec.message),
                    )
                    Log.i(
                        TAG,
                        "step $step tool=${call.name} ok=${exec.success} : ${exec.message.take(
                            80,
                        )}",
                    )

                    resultBlocks += exec.resultBlock
                    acted = acted || exec.didAct
                    producedPerception = producedPerception || exec.producedPerception
                    exec.screenState?.let {
                        latestScreen = it
                        // A get_screen_state result put its outline in front of the
                        // model; record it so the next auto-perception can dedupe.
                        if (call.name ==
                            AgentTools.GET_SCREEN_STATE
                        ) {
                            lastSentOutline = it.toOutline()
                        }
                    }
                    exec.contextEdit?.let { pendingContext = mergeContext(pendingContext, it) }
                    if (exec.finished) {
                        finished = true
                        finishSummary = exec.finishSummary
                        break
                    }
                }

                if (finished) {
                    // Speak the finish summary only if the user hasn't already heard
                    // the outcome (a `say` with no device action after it) — whether
                    // that say happened this turn or an earlier one. Avoids the
                    // "says it, then re-says it with different framing" double-TTS.
                    if (!spokenIsCurrent) {
                        finishSummary?.takeIf { it.isNotBlank() }?.let {
                            userIo.say(
                                it,
                            )
                        }
                    }
                    repository.appendMessage(
                        sessionId,
                        Role.USER,
                        resultBlocks,
                        kind = TOOL_RESULT_KIND,
                    )
                    bus.emit(AgentEvent.Finished(finishSummary))
                    return
                }

                // Auto-perception: attach a fresh screen outline for the next turn,
                // unless a perception tool already produced one this turn.
                if (acted && !producedPerception) {
                    awaitSettle()
                    latestScreen = safeGetScreen()
                    resultBlocks += ContentBlock.Text(screenBlockText(latestScreen))
                }
                repository.appendMessage(
                    sessionId,
                    Role.USER,
                    resultBlocks,
                    kind = TOOL_RESULT_KIND,
                )

                // Loop guard: no-progress detection (same screen + same lead action).
                val signature = latestScreen.signature() + "|" + response.toolCalls.first().name
                if (progress.record(signature)) {
                    val answer =
                        userIo.ask(
                            "I don't seem to be making progress. Should I keep trying? (yes/no)",
                        )
                    if (!ActionGate.isAffirmative(answer)) {
                        bus.emit(AgentEvent.Finished("Stopped: no progress."))
                        return
                    }
                    progress.reset()
                }

                // Cost/context ceiling: drop old screenshots and request a context edit.
                if (response.usage.inputTokens > CONTEXT_CEILING_TOKENS) {
                    Log.i(
                        TAG,
                        "context ceiling hit (${response.usage.inputTokens} tok) — dropping screenshots",
                    )
                    repository.markScreenshotsDropped(sessionId, keepLast = 1)
                    pendingContext =
                        mergeContext(
                            pendingContext,
                            ContextManagement(clearToolUses = true, keepLastToolUses = 1),
                        )
                }
            }
        }

        private suspend fun gateAndExecute(
            sessionId: Long,
            call: ToolCall,
            latestScreen: ScreenState,
        ): ToolExecution {
            if (call.name == AgentTools.PERFORM_ACTIONS) {
                return executeBatch(sessionId, call, latestScreen)
            }
            val gateInput = buildGateInput(call, latestScreen)
            val decision = actionGate.classify(gateInput)
            if (decision.gated) {
                val proceed = actionGate.confirm(gateInput, userIo, bus)
                if (!proceed) {
                    val label = decision.category?.label ?: "action"
                    return ToolExecution(
                        resultBlock =
                            ContentBlock.ToolResult(
                                toolUseId = call.id,
                                content =
                                    listOf(
                                        ContentBlock.Text(
                                            "The user declined this $label. " +
                                                "Do not retry it; consider an alternative or ask the user.",
                                        ),
                                    ),
                                isError = true,
                            ),
                        success = false,
                        message = "declined (${decision.category?.name})",
                    )
                }
            }
            return router.execute(sessionId, call)
        }

        /**
         * Run a `perform_actions` batch: gate + execute each sub-action in order
         * (so a gated `tap` inside a batch still asks for confirmation), stop at
         * the first failure/decline, and fold everything into one `tool_result`
         * for the batch's own tool_use id. Sub-actions emit their own
         * ToolCallStarted/Finished events so the overlay shows live progress, but
         * only the batch call itself is persisted by the main loop. The screen is
         * deliberately NOT re-captured between actions — element ids stay bound to
         * the frame the model chose them from (that is the contract in the tool
         * description: batch only same-screen bursts).
         */
        private suspend fun executeBatch(
            sessionId: Long,
            call: ToolCall,
            latestScreen: ScreenState,
        ): ToolExecution {
            val actions =
                BatchActionParser.parse(json, call.argumentsJson)?.takeIf { it.isNotEmpty() }
                    ?: return batchResult(
                        call,
                        "perform_actions needs a non-empty `actions` array of {tool, args}.",
                        success = false,
                        acted = false,
                        message = "batch: malformed actions",
                    )
            actions.firstOrNull { it.tool !in AgentTools.BATCHABLE }?.let { bad ->
                return batchResult(
                    call,
                    "Tool '${bad.tool}' cannot be batched. Batchable tools: " +
                        AgentTools.BATCHABLE.joinToString(", ") + ".",
                    success = false,
                    acted = false,
                    message = "batch: unbatchable tool '${bad.tool}'",
                )
            }

            val lines = mutableListOf<String>()
            var acted = false
            for ((index, action) in actions.withIndex()) {
                coroutineContext.ensureActive()
                val sub = ToolCall("${call.id}_$index", action.tool, action.argsJson)
                bus.emit(AgentEvent.ToolCallStarted(sub.id, sub.name, sub.argumentsJson))
                val exec = gateAndExecute(sessionId, sub, latestScreen)
                bus.emit(
                    AgentEvent.ToolCallFinished(sub.id, sub.name, exec.success, exec.message),
                )
                acted = acted || exec.didAct
                lines += "${index + 1}. ${action.tool}: ${if (exec.success) "ok" else "FAILED"}" +
                    " — ${exec.message}"
                if (!exec.success) {
                    lines += "Stopped at action ${index + 1} of ${actions.size}; " +
                        "the remaining actions were not run."
                    return batchResult(
                        call,
                        lines.joinToString("\n"),
                        success = false,
                        acted = acted,
                        message = "batch stopped at ${index + 1}/${actions.size}: ${exec.message}",
                    )
                }
                if (exec.didAct && index < actions.lastIndex) delay(BATCH_INTER_ACTION_MS)
            }
            return batchResult(
                call,
                lines.joinToString("\n"),
                success = true,
                acted = acted,
                message = "batch: ${actions.size} actions ok",
            )
        }

        private fun batchResult(
            call: ToolCall,
            text: String,
            success: Boolean,
            acted: Boolean,
            message: String,
        ): ToolExecution =
            ToolExecution(
                resultBlock =
                    ContentBlock.ToolResult(
                        toolUseId = call.id,
                        content = listOf(ContentBlock.Text(text)),
                        isError = !success,
                    ),
                success = success,
                message = message,
                didAct = acted,
            )

        private fun buildGateInput(
            call: ToolCall,
            screen: ScreenState,
        ): GateInput {
            val elementId = argInt(call.argumentsJson, "element_id")
            val (targetText, isPassword) = screen.elementText(elementId)
            return GateInput(
                toolName = call.name,
                argsJson = call.argumentsJson,
                targetText = targetText,
                isPasswordField = isPassword,
            )
        }

        // --- Helpers ------------------------------------------------------------

        private suspend fun finishRun(
            sessionId: Long,
            message: String,
        ) {
            userIo.say(message)
            bus.emit(AgentEvent.Finished(message))
        }

        private fun onStream(event: LlmStreamEvent) {
            when (event) {
                is LlmStreamEvent.TextDelta -> bus.emit(AgentEvent.AssistantText(event.text))
                is LlmStreamEvent.ThinkingDelta -> bus.emit(AgentEvent.Thinking(event.text))
                is LlmStreamEvent.UsageUpdate -> bus.emit(AgentEvent.UsageUpdated(event.usage))
                else -> Unit
            }
        }

        private suspend fun safeGetScreen(): ScreenState =
            runCatching { device.getScreenState() }.getOrElse { ScreenState.EMPTY }

        /** Recipes matching [userIntent], as a first-turn text block (null if none). */
        private suspend fun recallBlock(userIntent: String): ContentBlock.Text? {
            val hits = runCatching { taskMemory.recallHint(userIntent) }.getOrElse { emptyList() }
            if (hits.isEmpty()) return null
            val lines =
                hits.joinToString("\n") { r ->
                    "- ${r.title}${r.appPackage?.let { " ($it)" } ?: ""} — ${r.memoryPath}"
                }
            return ContentBlock.Text(
                "Possibly relevant saved recipes (memory `view` one only if it matches):\n$lines",
            )
        }

        private suspend fun awaitSettle() {
            // Catch the (late) window-content-changed event, then debounce so the tree
            // stabilizes. Both are bounded so a static screen costs at most the timeout.
            withTimeoutOrNull(SETTLE_TIMEOUT_MS) { screenChangeSignals.events.first() }
            delay(SETTLE_DEBOUNCE_MS)
        }

        /**
         * The outline for [screen], or a one-line "(unchanged)" marker when it is
         * byte-identical to what the model last saw. Identical outline text implies
         * identical element ids, so acting on the previous outline stays safe.
         */
        private fun screenBlockText(screen: ScreenState): String {
            val outline = screen.toOutline()
            if (outline == lastSentOutline) return SessionRepository.UNCHANGED_SCREEN_MARKER
            lastSentOutline = outline
            return SessionRepository.SCREEN_OUTLINE_PREFIX + outline
        }

        private fun argInt(
            argsJson: String,
            key: String,
        ): Int? =
            runCatching {
                json
                    .parseToJsonElement(argsJson)
                    .jsonObject[key]
                    ?.jsonPrimitive
                    ?.intOrNull
            }.getOrNull()

        private fun mergeContext(
            a: ContextManagement?,
            b: ContextManagement?,
        ): ContextManagement? {
            if (a == null) return b
            if (b == null) return a
            return ContextManagement(
                clearToolUses = a.clearToolUses || b.clearToolUses,
                keepLastToolUses = b.keepLastToolUses ?: a.keepLastToolUses,
                compact = a.compact || b.compact,
            )
        }

        /** Detects the same (screen, lead-action) repeating N times in a row. */
        private class NoProgressTracker(
            private val threshold: Int = 3,
        ) {
            private var last: String? = null
            private var count = 0

            /** Records [signature]; returns true when the repeat threshold is reached. */
            fun record(signature: String): Boolean {
                if (signature == last) {
                    count++
                } else {
                    last = signature
                    count = 1
                }
                return count >= threshold
            }

            fun reset() {
                last = null
                count = 0
            }
        }

        companion object {
            private const val TAG = "AgentLoop"
            private const val MAX_STEPS = 25
            private const val MAX_TOKENS = 4096
            private const val SETTLE_TIMEOUT_MS = 1500L
            private const val SETTLE_DEBOUNCE_MS = 300L

            /** Breather between batched actions so ripples/IME keep up. */
            private const val BATCH_INTER_ACTION_MS = 150L

            /** Provider stop reason: server-tool loop paused; re-send to resume. */
            private const val PAUSE_TURN = "pause_turn"
            private const val CONTEXT_CEILING_TOKENS = 300_000
            private const val TOOL_RESULT_KIND = com.wisp.data.MessageKind.TOOL_RESULT
        }
    }

/** Stable-ish signature of a screen for no-progress detection. */
internal fun ScreenState.signature(): String =
    appPackage + "#" + elements.size + "#" + toOutline().hashCode()
