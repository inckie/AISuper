package com.damn.aisuper.engine

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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

    @Test
    fun `quickjs backend converts nested objects in registered functions`() = runTest {
        val engine = createPlatformJSEngine(JSEngineBackend.QuickJs)
        try {
            var receivedArgs: List<JsonElement>? = null
            engine.registerFunction("captureOptions") { args ->
                receivedArgs = args
                JsonPrimitive(true)
            }
            engine.loadScript(
                """
                function callCaptureOptions() {
                    return captureOptions(
                        "themeOptions",
                        [
                            { value: "light", label: "Light" },
                            { value: "dark", label: "Dark" }
                        ]
                    );
                }
                """.trimIndent()
            )

            engine.callFunction("callCaptureOptions", emptyList())

            // Check what the function received
            assertNotNull(receivedArgs)
            assertEquals(2, receivedArgs!!.size)

            val keyArg = receivedArgs!![0]
            assertEquals("themeOptions", keyArg.jsonPrimitive.content)

            val arrayArg = receivedArgs!![1] as kotlinx.serialization.json.JsonArray
            assertEquals(2, arrayArg.size)

            val firstItem = arrayArg[0] as kotlinx.serialization.json.JsonObject
            println("[TEST] firstItem value: ${firstItem["value"]}")
            println("[TEST] firstItem label: ${firstItem["label"]}")
            assertEquals("light", firstItem["value"]?.jsonPrimitive?.content)
            assertEquals("Light", firstItem["label"]?.jsonPrimitive?.content)
        } finally {
            engine.close()
        }
    }

    @Test
    fun `quickjs backend mirrors feature setValue pattern`() = runTest {
        val engine = createPlatformJSEngine(JSEngineBackend.QuickJs)
        try {
            var lastKey: String? = null
            var lastValue: JsonElement? = null
            engine.registerFunction("setValue") { args ->
                if (args.size >= 2) {
                    lastKey = try { args[0].jsonPrimitive.content } catch (_: Exception) { null }
                    lastValue = args[1]
                }
                JsonPrimitive(true)
            }
            engine.loadScript(
                """
                function callSetValue() {
                    return setValue(
                        "themeOptions",
                        [
                            { value: "light", label: "Light" },
                            { value: "dark", label: "Dark" }
                        ]
                    );
                }
                """.trimIndent()
            )

            engine.callFunction("callSetValue", emptyList())

            assertEquals("themeOptions", lastKey)
            assertNotNull(lastValue)
            val arrayValue = lastValue as kotlinx.serialization.json.JsonArray
            assertEquals(2, arrayValue.size)
            val firstOptionObj = arrayValue[0] as kotlinx.serialization.json.JsonObject
            println("[TEST-setValue] firstOption value type: ${firstOptionObj["value"]?.let { it::class.simpleName }}")
            println("[TEST-setValue] firstOption value: ${firstOptionObj["value"]}")
            assertEquals("light", firstOptionObj["value"]?.jsonPrimitive?.content)
            assertEquals("Light", firstOptionObj["label"]?.jsonPrimitive?.content)
        } finally {
            engine.close()
        }
    }

    @Test
    fun `quickjs backend direct object parsing to check JsObject handling`() = runTest {
        val engine = createPlatformJSEngine(JSEngineBackend.QuickJs)
        try {
            engine.loadScript(
                """
                function getDirectObject() {
                    const obj = { value: "test_value", label: "test_label" };
                    return obj;
                }
                """.trimIndent()
            )

            val result = engine.callFunction("getDirectObject", emptyList())
            println("[TEST-direct] result type: ${result::class.simpleName}")
            println("[TEST-direct] result: $result")

            val obj = result as kotlinx.serialization.json.JsonObject
            println("[TEST-direct] value field type: ${obj["value"]?.let { it::class.simpleName }}")
            println("[TEST-direct] value field: ${obj["value"]}")
            assertEquals("test_value", obj["value"]?.jsonPrimitive?.content)
            assertEquals("test_label", obj["label"]?.jsonPrimitive?.content)
        } finally {
            engine.close()
        }
    }

    @Test
    fun `quickjs backend host function returning JsonObject has readable string properties in JS`() = runTest {
        val engine = createPlatformJSEngine(JSEngineBackend.QuickJs)
        try {
            // Host function returns a structured JsonObject
            engine.registerFunction("getConfig") { _ ->
                kotlinx.serialization.json.buildJsonObject {
                    put("theme", JsonPrimitive("dark"))
                    put("lang", JsonPrimitive("en"))
                }
            }
            // Return capture
            var capturedTheme: String? = null
            var capturedLang: String? = null
            engine.registerFunction("capture") { args ->
                capturedTheme = args.getOrNull(0)?.jsonPrimitive?.content
                capturedLang = args.getOrNull(1)?.jsonPrimitive?.content
                JsonPrimitive(true)
            }
            engine.loadScript(
                """
                function run() {
                    const cfg = getConfig();
                    capture(cfg.theme, cfg.lang);
                }
                """.trimIndent()
            )
            engine.callFunction("run", emptyList())
            assertEquals("dark", capturedTheme)
            assertEquals("en", capturedLang)
        } finally {
            engine.close()
        }
    }

    @Test
    fun `quickjs backend host function returning JsonArray of JsonObjects has readable properties in JS`() = runTest {
        val engine = createPlatformJSEngine(JSEngineBackend.QuickJs)
        try {
            engine.registerFunction("getOptions") { _ ->
                kotlinx.serialization.json.buildJsonArray {
                    add(kotlinx.serialization.json.buildJsonObject {
                        put("value", JsonPrimitive("light"))
                        put("label", JsonPrimitive("Light"))
                    })
                    add(kotlinx.serialization.json.buildJsonObject {
                        put("value", JsonPrimitive("dark"))
                        put("label", JsonPrimitive("Dark"))
                    })
                }
            }
            var capturedFirst: String? = null
            var capturedSecond: String? = null
            engine.registerFunction("capture") { args ->
                capturedFirst = args.getOrNull(0)?.jsonPrimitive?.content
                capturedSecond = args.getOrNull(1)?.jsonPrimitive?.content
                JsonPrimitive(true)
            }
            engine.loadScript(
                """
                function run() {
                    const opts = getOptions();
                    capture(opts[0].value, opts[1].label);
                }
                """.trimIndent()
            )
            engine.callFunction("run", emptyList())
            assertEquals("light", capturedFirst)
            assertEquals("Dark", capturedSecond)
        } finally {
            engine.close()
        }
    }
}
