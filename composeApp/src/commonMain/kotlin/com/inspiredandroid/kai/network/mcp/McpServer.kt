package com.inspiredandroid.kai.network.mcp

import kotlinx.serialization.Serializable

@Serializable
data class McpServer(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
)
