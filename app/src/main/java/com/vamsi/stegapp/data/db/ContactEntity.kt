package com.vamsi.stegapp.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val id: String,
    val name: String,
    val lastMessage: String? = null,
    val lastMessageTime: Long = 0,
    val unreadCount: Int = 0,
    val profileImageUri: String? = null,
    val isOnline: Boolean = false
)
