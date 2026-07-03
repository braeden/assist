package com.wisp.agent

import com.wisp.llm.ToolSpec

/**
 * The single source of truth for the model's action space (ARCHITECTURE.md tool
 * catalog), expressed as [ToolSpec.ClientTool]s fed to `LlmRequest.tools`. Grouped
 * perception/control, user-interaction, and context/economy. Gesture tools are
 * marked `strict` so the provider enforces the argument schema. [ToolRouter]
 * executes each by [name]; keep the names here and there in lockstep.
 *
 * The learned-task memory tool (phase-12) is advertised as a
 * [ToolSpec.ProviderTool] (`memory_20250818`, name `memory`); Anthropic owns its
 * schema and injects the "always view your memory first" protocol. [ToolRouter]
 * executes `memory` `tool_use` blocks against `MemoryStore`.
 */
object AgentTools {
    // Perception & control
    const val GET_SCREEN_STATE = "get_screen_state"
    const val TAKE_SCREENSHOT = "take_screenshot"
    const val TAP = "tap"
    const val TAP_XY = "tap_xy"
    const val LONG_PRESS = "long_press"
    const val LONG_PRESS_XY = "long_press_xy"
    const val SWIPE = "swipe"
    const val SWIPE_XY = "swipe_xy"
    const val SCROLL = "scroll"
    const val SET_TEXT = "set_text"
    const val PRESS_KEY = "press_key"
    const val OPEN_APP = "open_app"
    const val WAIT = "wait"
    const val PERFORM_ACTIONS = "perform_actions"

    // User interaction
    const val SAY = "say"
    const val ASK = "ask"
    const val FINISH = "finish"

    // Context / economy
    const val DROP_OLD_SCREENSHOTS = "drop_old_screenshots"
    const val COMPACT_CONVERSATION = "compact_conversation"
    const val NOTE = "note"

    // Learned task memory (provider tool — Anthropic-owned schema)
    const val MEMORY = "memory"
    const val MEMORY_TYPE = "memory_20250818"

    // Anthropic server tools (executed on Anthropic's side; results arrive as
    // provider-owned content blocks — never routed through ToolRouter).
    const val WEB_SEARCH = "web_search"
    const val WEB_SEARCH_TYPE = "web_search_20260209"
    const val WEB_SEARCH_TYPE_BASIC = "web_search_20250305"
    const val WEB_FETCH = "web_fetch"
    const val WEB_FETCH_TYPE = "web_fetch_20260209"
    const val ADVISOR = "advisor"
    const val ADVISOR_TYPE = "advisor_20260301"

    /** The advisor tool always consults Opus (must be ≥ the executor model). */
    const val ADVISOR_MODEL = "claude-opus-4-8"

    /** Model prefixes that support the `_20260209` web tools (dynamic filtering). */
    private val DYNAMIC_WEB_TOOL_MODEL_PREFIXES =
        listOf(
            "claude-opus-4-6",
            "claude-opus-4-7",
            "claude-opus-4-8",
            "claude-sonnet-5",
            "claude-sonnet-4-6",
        )

    /** Executor-model prefixes that get the advisor tool (pointed at Opus). */
    private val ADVISOR_EXECUTOR_PREFIXES = listOf("claude-sonnet-", "claude-haiku-")

    /**
     * Control tools allowed inside a [PERFORM_ACTIONS] batch. Perception, user
     * interaction, context, and memory tools are excluded on purpose: they either
     * return rich payloads (screens/screenshots), block on the user, or edit the
     * conversation — none of which compose into one aggregated `tool_result`.
     */
    val BATCHABLE: Set<String> =
        setOf(
            TAP,
            TAP_XY,
            LONG_PRESS,
            LONG_PRESS_XY,
            SWIPE,
            SWIPE_XY,
            SCROLL,
            SET_TEXT,
            PRESS_KEY,
            OPEN_APP,
            WAIT,
        )

    /**
     * The full catalog advertised to the model, in a stable order
     * (cache-friendly). When [model] is given, Anthropic server tools that the
     * model supports are appended: web_search + web_fetch (`_20260209` on
     * Opus 4.6+/Sonnet 4.6+/Sonnet 5; basic web_search on Haiku), and the
     * advisor tool — pointed at Opus — only for sonnet/haiku executors (Opus
     * consulting Opus adds cost without capability). The set varies only by
     * model, so it never busts the prompt cache mid-session (caches are
     * model-scoped anyway).
     */
    fun catalog(model: String? = null): List<ToolSpec> = baseCatalog() + serverTools(model)

    private fun serverTools(model: String?): List<ToolSpec> {
        if (model == null) return emptyList()
        val tools = mutableListOf<ToolSpec>()
        if (DYNAMIC_WEB_TOOL_MODEL_PREFIXES.any { model.startsWith(it) }) {
            tools += ToolSpec.ProviderTool(type = WEB_SEARCH_TYPE, name = WEB_SEARCH)
            tools += ToolSpec.ProviderTool(type = WEB_FETCH_TYPE, name = WEB_FETCH)
        } else if (model.startsWith("claude-haiku-")) {
            // Haiku predates the dynamic-filtering variants; basic search only
            // (basic web_fetch still sits behind a beta we don't request).
            tools += ToolSpec.ProviderTool(type = WEB_SEARCH_TYPE_BASIC, name = WEB_SEARCH)
        }
        if (ADVISOR_EXECUTOR_PREFIXES.any { model.startsWith(it) }) {
            tools +=
                ToolSpec.ProviderTool(
                    type = ADVISOR_TYPE,
                    name = ADVISOR,
                    model = ADVISOR_MODEL,
                )
        }
        return tools
    }

    private fun baseCatalog(): List<ToolSpec> =
        listOf(
            // --- Perception & control ---
            client(
                GET_SCREEN_STATE,
                "Return the current foreground screen as a compact accessibility outline " +
                    "(one line per element with a stable #id). Your default way to perceive.",
                objectSchema(),
            ),
            client(
                TAKE_SCREENSHOT,
                "Capture a PNG screenshot of the current screen. Use only when the a11y " +
                    "outline is insufficient (canvas/WebView/visual judgement).",
                objectSchema(),
            ),
            client(
                TAP,
                "Tap the element with the given id from the latest screen outline.",
                objectSchema(
                    required = listOf("element_id"),
                    props = """"element_id":{"type":"integer","description":"#id from the outline"}""",
                ),
                strict = true,
            ),
            client(
                TAP_XY,
                "Tap absolute screen coordinates. Prefer tap(element_id) when an element fits.",
                objectSchema(
                    required = listOf("x", "y"),
                    props = """"x":{"type":"integer"},"y":{"type":"integer"}""",
                ),
                strict = true,
            ),
            client(
                LONG_PRESS,
                "Long-press the element with the given id.",
                objectSchema(
                    required = listOf("element_id"),
                    props = """"element_id":{"type":"integer"}""",
                ),
                strict = true,
            ),
            client(
                LONG_PRESS_XY,
                "Long-press absolute screen coordinates.",
                objectSchema(
                    required = listOf("x", "y"),
                    props = """"x":{"type":"integer"},"y":{"type":"integer"}""",
                ),
                strict = true,
            ),
            client(
                SWIPE,
                "Swipe the screen in a direction (the finger travels that way; content " +
                    "moves opposite). Optional distance fraction of the screen (0..1).",
                objectSchema(
                    required = listOf("direction"),
                    props =
                        """"direction":{"type":"string","enum":["up","down","left","right"]},""" +
                            """"distance":{"type":"number","minimum":0,"maximum":1}""",
                ),
            ),
            client(
                SWIPE_XY,
                "Swipe from (x1,y1) to (x2,y2) over an optional duration in ms.",
                objectSchema(
                    required = listOf("x1", "y1", "x2", "y2"),
                    props =
                        """"x1":{"type":"integer"},"y1":{"type":"integer"},""" +
                            """"x2":{"type":"integer"},"y2":{"type":"integer"},""" +
                            """"duration_ms":{"type":"integer","minimum":0}""",
                ),
            ),
            client(
                SCROLL,
                "Scroll a scrollable element by id, or the screen in a direction. Provide " +
                    "either element_id or direction.",
                objectSchema(
                    props =
                        """"element_id":{"type":"integer"},""" +
                            """"direction":{"type":"string","enum":["up","down","left","right"]},""" +
                            """"forward":{"type":"boolean","description":"for element_id scroll"}""",
                ),
            ),
            client(
                SET_TEXT,
                "Focus an editable element by id and set its text (replaces existing text).",
                objectSchema(
                    required = listOf("element_id", "text"),
                    props = """"element_id":{"type":"integer"},"text":{"type":"string"}""",
                ),
                strict = true,
            ),
            client(
                PRESS_KEY,
                "Press a global navigation key.",
                objectSchema(
                    required = listOf("key"),
                    props =
                        """"key":{"type":"string","enum":["back","home","recents",""" +
                            """"notifications","quick_settings","enter"]}""",
                ),
                strict = true,
            ),
            client(
                OPEN_APP,
                "Launch an app by package name or human label (e.g. \"Clock\" or " +
                    "\"com.google.android.deskclock\").",
                objectSchema(
                    required = listOf("app"),
                    props = """"app":{"type":"string"}""",
                ),
                strict = true,
            ),
            client(
                WAIT,
                "Wait for the UI to settle / animations / loading. Milliseconds (capped).",
                objectSchema(
                    required = listOf("ms"),
                    props = """"ms":{"type":"integer","minimum":0}""",
                ),
            ),
            client(
                PERFORM_ACTIONS,
                "Perform a short sequence of control actions in one call, in order, " +
                    "stopping at the first failure. Use for bursts on the SAME stable " +
                    "screen (keypad/PIN digits, filling several form fields, " +
                    "type-then-enter). Element ids must all come from the latest " +
                    "outline — if an action navigates or changes the screen, later " +
                    "element_id actions may miss; use separate calls instead. Each " +
                    "action is {tool, args} using that tool's own arguments (insert a " +
                    "wait action if the UI needs time between steps).",
                objectSchema(
                    required = listOf("actions"),
                    props =
                        """"actions":{"type":"array","minItems":1,"maxItems":10,""" +
                            """"items":{"type":"object","properties":{""" +
                            """"tool":{"type":"string","enum":["tap","tap_xy","long_press",""" +
                            """"long_press_xy","swipe","swipe_xy","scroll","set_text",""" +
                            """"press_key","open_app","wait"]},""" +
                            """"args":{"type":"object"}},""" +
                            """"required":["tool","args"]}}""",
                ),
            ),
            // --- User interaction ---
            client(
                SAY,
                "Speak/display a short message to the user. One-way; does not block.",
                objectSchema(required = listOf("text"), props = """"text":{"type":"string"}"""),
            ),
            client(
                ASK,
                "Ask the user a question and wait for their reply. Use when you need a " +
                    "decision or missing information.",
                objectSchema(
                    required = listOf("question"),
                    props = """"question":{"type":"string"}""",
                ),
            ),
            client(
                FINISH,
                "End the task. `summary` is a one-line status for the log/UI and is NOT " +
                    "spoken aloud — if you want the user to hear the outcome, call `say` " +
                    "first (a brief spoken result), then `finish`. Call finish when the " +
                    "task is complete or cannot proceed. Do not call more tools after.",
                objectSchema(
                    required = listOf("summary"),
                    props = """"summary":{"type":"string"}""",
                ),
            ),
            // --- Context / economy ---
            client(
                DROP_OLD_SCREENSHOTS,
                "Drop old screenshots/tool results from context to save tokens, optionally " +
                    "keeping the most recent few.",
                objectSchema(
                    props = """"keep_last":{"type":"integer","minimum":0}""",
                ),
            ),
            client(
                COMPACT_CONVERSATION,
                "Summarize and compact earlier conversation to free up context.",
                objectSchema(),
            ),
            client(
                NOTE,
                "Write a durable scratch note into the session (survives compaction).",
                objectSchema(required = listOf("text"), props = """"text":{"type":"string"}"""),
            ),
            // --- Learned task memory (provider tool) ---
            ToolSpec.ProviderTool(type = MEMORY_TYPE, name = MEMORY),
        )

    private fun client(
        name: String,
        description: String,
        inputSchemaJson: String,
        strict: Boolean = false,
    ) = ToolSpec.ClientTool(name, description, inputSchemaJson, strict)

    /**
     * Build a JSON-Schema object. [props] is the raw comma-separated property
     * entries (may be empty). `additionalProperties:false` is required when
     * `strict` is used and is harmless otherwise.
     */
    private fun objectSchema(
        required: List<String> = emptyList(),
        props: String = "",
    ): String {
        val requiredJson = required.joinToString(",") { "\"$it\"" }
        return """{"type":"object","properties":{$props},""" +
            """"required":[$requiredJson],"additionalProperties":false}"""
    }
}
