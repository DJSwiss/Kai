package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.network.mcp.McpClient
import com.inspiredandroid.kai.network.mcp.McpServer
import com.inspiredandroid.kai.network.mcp.McpToolDefinition
import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolSchema

class McpTool(
    private val server: McpServer,
    private val toolDefinition: McpToolDefinition,
) : Tool {

    override val schema: ToolSchema = ToolSchema(
        name = "mcp_${server.id}_${toolDefinition.name}",
        description = toolDefinition.description,
        parameters = toolDefinition.parameters.entries.associate { (name, schema) ->
            name to ParameterSchema(
                type = schema.type,
                description = schema.description,
                required = schema.required,
            )
        },
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        return McpClient.callTool(server.url, toolDefinition.name, args)
    }
}
