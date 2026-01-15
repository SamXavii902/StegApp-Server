package com.vamsi.stegapp.model

import android.net.Uri

data class Message(
    val id: String = java.util.UUID.randomUUID().toString(),
    val chatId: String,
    val text: String? = null,
    val imageUri: Uri? = null,
    val isFromMe: Boolean,
    val isStego: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
