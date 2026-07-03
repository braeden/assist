package com.wisp.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One sub-action of a [AgentTools.PERFORM_ACTIONS] batch. */
data class BatchAction(
    val tool: String,
    val argsJson: String,
)

/**
 * Parses `perform_actions` arguments (`{"actions":[{"tool":"tap","args":{...}}]}`)
 * into [BatchAction]s. Pure and lenient only about a missing `args` (defaults to
 * `{}`); anything else malformed returns null so the caller can hand the model a
 * usable error instead of throwing.
 */
object BatchActionParser {
    fun parse(
        json: Json,
        argumentsJson: String,
    ): List<BatchAction>? =
        runCatching {
            json
                .parseToJsonElement(argumentsJson)
                .jsonObject["actions"]!!
                .jsonArray
                .map { element ->
                    val obj = element.jsonObject
                    BatchAction(
                        tool = obj["tool"]!!.jsonPrimitive.content,
                        argsJson = (obj["args"]?.jsonObject ?: JsonObject(emptyMap())).toString(),
                    )
                }
        }.getOrNull()
}
