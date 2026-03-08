package com.inspiredandroid.kai.data

import kotlinx.serialization.Serializable

@Serializable
data class Skill(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val parameters: List<SkillParameter> = emptyList(),
    val enabled: Boolean = true,
)

@Serializable
data class SkillParameter(
    val name: String,
    val description: String,
    val type: String = "string",
    val required: Boolean = true,
)
