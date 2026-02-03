package com.vamsi.stegapp.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val text: String?,
    val imageUri: String?, // Stored as String, convert to Uri when reading
    val isFromMe: Boolean,
    val isStego: Boolean,
    val status: Int = 0,
    val deliveryStatus: Int = 0,
    val timestamp: Long,
    val replyToId: String? = null // Added for Reply Logic
)
