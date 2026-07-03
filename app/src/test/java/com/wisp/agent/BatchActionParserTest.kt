package com.wisp.agent

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatchActionParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses ordered actions with their args`() {
        val actions =
            BatchActionParser.parse(
                json,
                """
                {"actions":[
                  {"tool":"tap","args":{"element_id":7}},
                  {"tool":"set_text","args":{"element_id":9,"text":"hi"}},
                  {"tool":"press_key","args":{"key":"enter"}}
                ]}
                """.trimIndent(),
            )
        requireNotNull(actions)
        assertEquals(3, actions.size)
        assertEquals("tap", actions[0].tool)
        assertEquals("""{"element_id":7}""", actions[0].argsJson)
        assertEquals("set_text", actions[1].tool)
        assertEquals("press_key", actions[2].tool)
        assertEquals("""{"key":"enter"}""", actions[2].argsJson)
    }

    @Test
    fun `missing args defaults to an empty object`() {
        val actions = BatchActionParser.parse(json, """{"actions":[{"tool":"wait"}]}""")
        requireNotNull(actions)
        assertEquals("{}", actions[0].argsJson)
    }

    @Test
    fun `missing actions array is malformed`() {
        assertNull(BatchActionParser.parse(json, """{"tool":"tap"}"""))
    }

    @Test
    fun `action without a tool name is malformed`() {
        assertNull(
            BatchActionParser.parse(json, """{"actions":[{"args":{"element_id":1}}]}"""),
        )
    }

    @Test
    fun `non-json input is malformed`() {
        assertNull(BatchActionParser.parse(json, "not json"))
    }

    @Test
    fun `empty actions array parses to an empty list`() {
        assertEquals(emptyList<BatchAction>(), BatchActionParser.parse(json, """{"actions":[]}"""))
    }

    @Test
    fun `every batchable tool is a real catalog tool and batch cannot nest`() {
        val catalogNames =
            AgentTools
                .catalog()
                .map { it.name }
                .toSet()
        AgentTools.BATCHABLE.forEach { name ->
            assertEquals("$name must be in the catalog", true, name in catalogNames)
        }
        assertEquals(false, AgentTools.PERFORM_ACTIONS in AgentTools.BATCHABLE)
    }
}
