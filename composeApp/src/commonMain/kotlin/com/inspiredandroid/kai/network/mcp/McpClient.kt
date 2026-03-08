package com.inspiredandroid.kai.network.mcp

import com.inspiredandroid.kai.httpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class McpToolDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, McpParameterSchema>,
)

data class McpParameterSchema(
    val type: String,
    val description: String,
    val required: Boolean,
)

@Serializable
private data class McpRequest(
    val jsonrpc: String = "2.0",
    val id: Int? = null,
    val method: String,
    val params: JsonElement = JsonNull,
)

@Serializable
private data class McpResponse(
    val jsonrpc: String = "2.0",
    val id: Int? = null,
    val result: JsonElement? = null,
    val error: JsonElement? = null,
)

object McpClient {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = httpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
        }
    }

    suspend fun initialize(serverUrl: String): Boolean {
        return try {
            val initRequest = McpRequest(
                id = 1,
                method = "initialize",
                params = JsonObject(
                    mapOf(
                        "protocolVersion" to JsonPrimitive("2024-11-05"),
                        "capabilities" to JsonObject(emptyMap()),
                        "clientInfo" to JsonObject(
                            mapOf(
                                "name" to JsonPrimitive("Kai"),
                                "version" to JsonPrimitive("1.0"),
                            ),
                        ),
                    ),
                ),
            )
            val response = client.post(serverUrl) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(initRequest))
            }
            val responseText = response.bodyAsText()
            val parsed = json.decodeFromString<McpResponse>(responseText)

            if (parsed.result != null) {
                // Send initialized notification
                val notification = McpRequest(
                    id = null,
                    method = "notifications/initialized",
                )
                try {
                    client.post(serverUrl) {
                        contentType(ContentType.Application.Json)
                        setBody(json.encodeToString(notification))
                    }
                } catch (_: Exception) {
                    // Notifications are fire-and-forget
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun listTools(serverUrl: String): List<McpToolDefinition> {
        return try {
            val request = McpRequest(
                id = 2,
                method = "tools/list",
                params = JsonObject(emptyMap()),
            )
            val response = client.post(serverUrl) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(request))
            }
            val responseText = response.bodyAsText()
            val parsed = json.decodeFromString<McpResponse>(responseText)
            val result = parsed.result?.jsonObject ?: return emptyList()
            val tools = result["tools"]?.jsonArray ?: return emptyList()

            tools.mapNotNull { toolElement ->
                try {
                    val toolObj = toolElement.jsonObject
                    val name = toolObj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val description = toolObj["description"]?.jsonPrimitive?.content ?: ""
                    val inputSchema = toolObj["inputSchema"]?.jsonObject
                    val properties = inputSchema?.get("properties")?.jsonObject
                    val requiredArray = inputSchema?.get("required")?.jsonArray
                        ?.map { it.jsonPrimitive.content }
                        ?.toSet() ?: emptySet()

                    val parameters = properties?.entries?.associate { (paramName, paramSchema) ->
                        val schemaObj = paramSchema.jsonObject
                        val type = schemaObj["type"]?.jsonPrimitive?.content ?: "string"
                        val desc = schemaObj["description"]?.jsonPrimitive?.content ?: ""
                        paramName to McpParameterSchema(
                            type = type,
                            description = desc,
                            required = paramName in requiredArray,
                        )
                    } ?: emptyMap()

                    McpToolDefinition(
                        name = name,
                        description = description,
                        parameters = parameters,
                    )
                } catch (_: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun callTool(serverUrl: String, toolName: String, args: Map<String, Any>): String {
        return try {
            val arguments = JsonObject(
                args.entries.associate { (k, v) ->
                    k to when (v) {
                        is String -> JsonPrimitive(v)
                        is Boolean -> JsonPrimitive(v)
                        is Number -> JsonPrimitive(v)
                        else -> JsonPrimitive(v.toString())
                    }
                },
            )
            val request = McpRequest(
                id = 3,
                method = "tools/call",
                params = JsonObject(
                    mapOf(
                        "name" to JsonPrimitive(toolName),
                        "arguments" to arguments,
                    ),
                ),
            )
            val response = client.post(serverUrl) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(request))
            }
            val responseText = response.bodyAsText()
            val parsed = json.decodeFromString<McpResponse>(responseText)
            val result = parsed.result?.jsonObject ?: return """{"success": false, "error": "No result"}"""
            val content = result["content"]?.jsonArray ?: return """{"success": false, "error": "No content"}"""
            content.mapNotNull { item ->
                val obj = item.jsonObject
                if (obj["type"]?.jsonPrimitive?.content == "text") {
                    obj["text"]?.jsonPrimitive?.content
                } else {
                    null
                }
            }.joinToString("\n").ifEmpty { """{"success": true}""" }
        } catch (e: Exception) {
            """{"success": false, "error": "MCP tool call failed: ${e.message}"}"""
        }
    }
}
