package com.damn.aisuper.engine

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class QuickJsKtEngineTest {

    @Test
    fun `quickjs backend executes sync host function`() = runTest {
        val engine = createPlatformJSEngine(JSEngineBackend.QuickJs) // explicit, not via override
        try {
            engine.registerFunction("plusOne") { args ->
                val value = args.first().jsonPrimitive.int
                JsonPrimitive(value + 1)
            }
            engine.loadScript(
                """
                function callPlusOne(x) {
                    return plusOne(x);
                }
                """.trimIndent()
            )

            val result = engine.callFunction("callPlusOne", listOf(JsonPrimitive(41)))
            assertEquals(42, result.jsonPrimitive.int)
        } finally {
            engine.close()
        }
    }

    @Test
    fun `quickjs backend executes suspend host function`() = runTest {
        val engine = createPlatformJSEngine(JSEngineBackend.QuickJs) // explicit, not via override
        try {
            engine.registerSuspendFunction("delayedEcho") { args ->
                args.firstOrNull() ?: JsonPrimitive("")
            }
            engine.loadScript(
                """
                async function callDelayedEcho(value) {
                    return await delayedEcho(value);
                }
                """.trimIndent()
            )

            val result = engine.callFunction("callDelayedEcho", listOf(JsonPrimitive("ok")))
            assertEquals("ok", result.jsonPrimitive.content)
        } finally {
            engine.close()
        }
    }
}

