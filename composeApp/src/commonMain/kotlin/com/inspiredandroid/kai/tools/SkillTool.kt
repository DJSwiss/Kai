package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.data.Skill
import com.inspiredandroid.kai.httpClient
import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolSchema
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class SkillTool(private val skill: Skill) : Tool {

    override val schema: ToolSchema = ToolSchema(
        name = skill.name.replace(" ", "_").lowercase(),
        description = skill.description,
        parameters = skill.parameters.associate { param ->
            param.name to ParameterSchema(
                type = param.type,
                description = param.description,
                required = param.required,
            )
        },
    )

    private val client = httpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun execute(args: Map<String, Any>): Any {
        return try {
            val jsonBody = JsonObject(
                args.entries.associate { (k, v) ->
                    k to when (v) {
                        is String -> JsonPrimitive(v)
                        is Boolean -> JsonPrimitive(v)
                        is Number -> JsonPrimitive(v)
                        else -> JsonPrimitive(v.toString())
                    }
                },
            )
            val response = client.post(skill.url) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(JsonObject.serializer(), jsonBody))
            }
            response.bodyAsText()
        } catch (e: Exception) {
            """{"success": false, "error": "Skill execution failed: ${e.message}"}"""
        }
    }
}
