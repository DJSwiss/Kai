package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.network.tools.Tool

object DynamicToolRegistry {
    private val tools = mutableListOf<Tool>()

    fun registerTools(newTools: List<Tool>) {
        tools.clear()
        tools.addAll(newTools)
    }

    fun getTools(): List<Tool> = tools.toList()
}
