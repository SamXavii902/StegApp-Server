package com.vamsi.stegapp.model

import android.net.Uri

data class Message(
    val id: String = java.util.UUID.randomUUID().toString(),
    val chatId: String,
    val text: String? = null,
    val imageUri: Uri? = null,
    val isFromMe: Boolean,
    val isStego: Boolean = false,
    val status: Int = 0, // 0: Normal/Sent, 1: Sending/Uploading, 2: Remote/Pending Download, 3: Downloading, 4: Downloaded
    val timestamp: Long = System.currentTimeMillis()
)
