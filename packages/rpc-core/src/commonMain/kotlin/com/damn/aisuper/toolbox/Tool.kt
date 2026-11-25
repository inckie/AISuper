package com.damn.aisuper.toolbox

/**
 * Core, transport-agnostic abstraction of a single callable tool.
 */
interface Tool {
    val name: String
    val description: String
    val parameters: List<ToolParameter>
    suspend fun invoke(arguments: ToolArguments): ToolResult
}

/**
 * Convenience [Tool] implementation backed by a suspend lambda.
 */
class SimpleTool(
    override val name: String,
    override val description: String,
    override val parameters: List<ToolParameter> = emptyList(),
    private val handler: suspend (ToolArguments) -> ToolResult
) : Tool {
    override suspend fun invoke(arguments: ToolArguments): ToolResult = handler(arguments)
}

/**
 * DSL helper to build a [Tool] cleanly.
 */
fun tool(
    name: String,
    description: String,
    vararg parameters: ToolParameter,
    handler: suspend (ToolArguments) -> ToolResult
): Tool = SimpleTool(name, description, parameters.toList(), handler)
