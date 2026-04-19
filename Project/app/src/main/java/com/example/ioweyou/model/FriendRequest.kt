package com.example.ioweyou.model

data class FriendRequest(
    val id: String = "",
    val fromUid: String = "",
    val fromName: String = "",
    val fromEmail: String = "",
    val toUid: String = "",
    val status: String = "pending"  // "pending" | "accepted" | "declined"
)
