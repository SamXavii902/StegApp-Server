package com.vamsi.stegapp.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamsi.stegapp.model.Message
import com.vamsi.stegapp.repo.StegoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import com.vamsi.stegapp.network.NetworkModule
import com.vamsi.stegapp.data.db.AppDatabase
import com.vamsi.stegapp.data.db.MessageEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.vamsi.stegapp.network.SocketClient
import com.vamsi.stegapp.data.db.ContactDao
import com.vamsi.stegapp.utils.UserPrefs

class ChatViewModel(context: Context, private val chatId: String) : ViewModel() {

    private val repository = StegoRepository(context)
    private val appContext = context.applicationContext

    init {
        val username = UserPrefs.getUsername(context) ?: "Anonymous"
        SocketClient.connect(username)
    }
    
    private val dao = AppDatabase.getDatabase(context).messageDao()
    private val contactDao = AppDatabase.getDatabase(context).contactDao()

    // Persistent Messages from DB
    val messages = dao.getMessagesForChat(chatId)
        .map { entities -> entities.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _success = MutableStateFlow<String?>(null)
    val success = _success.asStateFlow()

    fun sendMessage(text: String, imageUri: Uri, secretKey: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            // 1. Convert Uri to File path
            val imagePath = copyUriToTempFile(imageUri)
            if (imagePath == null) {
                _error.value = "Failed to process image"
                _isLoading.value = false
                return@launch
            }
            // 2. Embed message
            val result = repository.embedMessage(imagePath, text, secretKey)
            
            result.onSuccess { stegoPath ->
                // Add to messages
                // Save to DB
                val newMessage = Message(
                    id = java.util.UUID.randomUUID().toString(),
                    chatId = chatId,
                    text = null, // Hidden!
                    imageUri = Uri.fromFile(File(stegoPath)),
                    isFromMe = true,
                    isStego = true,
                    timestamp = System.currentTimeMillis()
                )
                dao.insertMessage(newMessage.toEntity())
                contactDao.updateLastMessage(chatId, "Sent a hidden message", newMessage.timestamp, 0)
                
                // Upload to Server for "Lossless" transfer
                uploadStegoImage(stegoPath)
            }.onFailure {
                _error.value = "Embedding failed: ${it.message}"
            }
            
            _isLoading.value = false
        }
    }

    fun receiveMessage(imageUri: Uri, secretKey: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            val imagePath = copyUriToTempFile(imageUri)
             if (imagePath == null) {
                _error.value = "Failed to process image"
                _isLoading.value = false
                return@launch
            }

            val result = repository.extractMessage(imagePath, secretKey)
            
            result.onSuccess { secretText ->
                val newMessage = Message(
                    id = java.util.UUID.randomUUID().toString(),
                    chatId = chatId,
                    text = secretText,
                    imageUri = imageUri,
                    isFromMe = false,
                    isStego = true,
                    timestamp = System.currentTimeMillis()
                )
                 dao.insertMessage(newMessage.toEntity())
                 contactDao.updateLastMessage(chatId, secretText, newMessage.timestamp, 0) // Reset unread or increment? For now 0 as we are IN chat.
            }.onFailure {
                 _error.value = "Extraction failed: ${it.message}"
                 // Still show the image but maybe mark as failed extraction?
                 val newMessage = Message(
                    id = java.util.UUID.randomUUID().toString(),
                    chatId = chatId,
                    text = "[No secret found or Wrong Key]",
                    imageUri = imageUri,
                    isFromMe = false,
                    isStego = false,
                    timestamp = System.currentTimeMillis()
                )
                dao.insertMessage(newMessage.toEntity())
                contactDao.updateLastMessage(chatId, "Received an image", newMessage.timestamp, 0)
            }
            _isLoading.value = false
        }
    }

    private fun uploadStegoImage(path: String) {
        viewModelScope.launch {
            try {
                val file = File(path)
                val requestFile = file.asRequestBody("image/png".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                
                val response = NetworkModule.api.uploadImage(body)
                if (response.isSuccessful && response.body() != null) {
                    val url = response.body()!!.url
                    val username = UserPrefs.getUsername(appContext) ?: "Anonymous"
                    // Notify Recipient via Socket
                    SocketClient.emitMessage(
                        text = null, 
                        imageUrl = url, 
                        sender = username,
                        recipient = chatId // chatId is the contact name
                    )
                    _success.value = "Sent & Uploaded Securely!"
                } else {
                    _error.value = "Upload failed: ${response.code()}"
                }
            } catch (e: Exception) {
                _error.value = "Network Error: ${e.message}"
            }
        }
    }

    private fun copyUriToTempFile(uri: Uri): String? {
        return try {
            val contentResolver = appContext.contentResolver
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val tempFile = File.createTempFile("temp_stego", ".png", appContext.cacheDir)
            val outputStream = FileOutputStream(tempFile)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            tempFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        SocketClient.disconnect()
    }

    fun deleteMessage(message: Message) {
        viewModelScope.launch {
            dao.deleteMessage(message.toEntity())
            
            // Sync Last Message with Contact
            val lastMsg = dao.getLastMessage(chatId)
            if (lastMsg != null) {
                val displayMsg = if (!lastMsg.text.isNullOrEmpty()) {
                    lastMsg.text
                } else if (lastMsg.isStego) {
                    "Sent a hidden message"
                } else {
                    "Sent an image"
                }
                contactDao.updateLastMessage(chatId, displayMsg!!, lastMsg.timestamp, 0)
            } else {
                // No messages left: Set default text
                contactDao.updateLastMessage(chatId, "Start a conversation", 0L, 0)
            }
        }
    }
}

private fun MessageEntity.toDomain(): Message {
    return Message(
        id = id,
        chatId = chatId,
        text = text,
        imageUri = imageUri?.let { Uri.parse(it) },
        isFromMe = isFromMe,
        isStego = isStego,
        timestamp = timestamp
    )
}

private fun Message.toEntity(): MessageEntity {
    return MessageEntity(
        id = id,
        chatId = chatId,
        text = text,
        imageUri = imageUri?.toString(),
        isFromMe = isFromMe,
        isStego = isStego,
        timestamp = timestamp
    )
}
