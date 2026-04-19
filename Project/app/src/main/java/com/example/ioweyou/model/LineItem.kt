package com.example.ioweyou.model

data class LineItem(
    val name: String = "",
    val price: Double = 0.0,
    val assignees: List<String> = emptyList()
)
