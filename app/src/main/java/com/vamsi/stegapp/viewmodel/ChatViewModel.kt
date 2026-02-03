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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.vamsi.stegapp.network.SocketClient
import com.vamsi.stegapp.data.db.ContactDao
import com.vamsi.stegapp.utils.UserPrefs
import kotlinx.coroutines.flow.update
import com.vamsi.stegapp.network.ProgressRequestBody

class ChatViewModel(context: Context, private val chatId: String) : ViewModel() {

    private val repository = StegoRepository(context)
    private val appContext = context.applicationContext

    // DAOs (Initialize BEFORE init block)
    private val dao = AppDatabase.getDatabase(context).messageDao()
    private val contactDao = AppDatabase.getDatabase(context).contactDao()


    // Shared Secret (ECDH)
    private var derivedSecretKey: String? = null

    init {
        val username = UserPrefs.getUsername(context) ?: "Anonymous"
        SocketClient.connect(username)
        // Reset unread count when opening chat
        viewModelScope.launch {
            try {
                contactDao.resetUnreadCount(chatId)
                
                // Mark all received messages as read
                val receivedMessages = dao.getMessagesForChat(chatId).map { entities ->
                    entities.filter { !it.isFromMe && it.deliveryStatus < 3 }
                }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList()).value
                
                receivedMessages.forEach { msg ->
                    SocketClient.emitMessageRead(msg.id, username)
                }
                
                // Fetch Public Key & Derive Secret
                val response = NetworkModule.api.fetchKey(chatId)
                if (response.isSuccessful && response.body() != null) {
                    val remoteKey = response.body()!!.publicKey
                    derivedSecretKey = com.vamsi.stegapp.security.KeyManager.deriveSharedSecret(remoteKey)
                    if (derivedSecretKey == null) {
                       _error.value = "Failed to secure connection (Key Derivation Error)"
                    }
                } else {
                    _error.value = "Could not fetch encryption keys for $chatId"
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = "Connection Error during Key Exchange"
            }
        }
        
        // Auto-Retry Pending Text Messages on Reconnect
        viewModelScope.launch {
            SocketClient.isConnected.collect { connected ->
                if (connected) {
                    try {
                        val pendingMessages = dao.getPendingTextMessages(chatId)
                        if (pendingMessages.isNotEmpty()) {
                             val username = UserPrefs.getUsername(appContext) ?: "Anonymous"
                             pendingMessages.forEach { msg ->
                                SocketClient.emitMessage(
                                    id = msg.id,
                                    text = msg.text,
                                    imageUrl = null, // Text messages only
                                    sender = username,
                                    recipient = chatId,
                                    camouflageText = null, // Simplified for retry
                                    timestamp = msg.timestamp,
                                    replyToId = msg.replyToId
                                )
                                // Update to Sent (0)
                                dao.updateStatus(msg.id, 0)
                             }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
    
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

    val isConnected = SocketClient.isConnected
    
    val contactOnlineStatus = contactDao.getContactFlow(chatId)
        .map { contact -> contact?.isOnline ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _uploadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val uploadProgress = _uploadProgress.asStateFlow()


    fun sendMessage(text: String, imageUri: Uri?, camouflageText: String? = null, replyToId: String? = null) {
        if (derivedSecretKey == null) {
            _error.value = "Secure connection not established. Please re-open chat."
            return
        }
        val secretKey = derivedSecretKey!!

        viewModelScope.launch {
            _error.value = null
            
            val timestamp = System.currentTimeMillis()
            val newId = java.util.UUID.randomUUID().toString()
            val username = UserPrefs.getUsername(appContext) ?: "Anonymous"

            if (imageUri != null) {
                // --- STEGO IMAGE FLOW ---
                
                // 1. Convert Uri to File path
                val imagePath = copyUriToTempFile(imageUri)
                if (imagePath == null) {
                    _error.value = "Failed to process image"
                    return@launch
                }
                // 2. Embed message
                val result = repository.embedMessage(imagePath, text, secretKey)
                
                result.onSuccess { stegoPath ->
                    // Add to messages (Status = 1: SENDING)
                    val newMessage = Message(
                        id = newId,
                        chatId = chatId,
                        text = null,
                        imageUri = Uri.fromFile(File(stegoPath)),
                        isFromMe = true,
                        isStego = true,
                        status = 1, // SENDING
                        timestamp = timestamp,
                        replyToId = replyToId
                    )
                    dao.insertMessage(newMessage.toEntity())
                    contactDao.updateLastMessage(chatId, "Sent a photo", timestamp, 0)
                    
                    // Upload
                    uploadStegoImage(stegoPath, newMessage, camouflageText)
                }.onFailure {
                    _error.value = "Embedding failed: ${it.message}"
                }
            } else {
                // --- TEXT-ONLY (STEALTH) FLOW ---
                
                // Status 1 = SENDING (Clock). Status 0 = SENT (Tick).
                val isConnected = SocketClient.isConnected.value
                val initialStatus = if (isConnected) 0 else 1
                
                val newMessage = Message(
                    id = newId,
                    chatId = chatId,
                    text = text,
                    imageUri = null,
                    isFromMe = true,
                    isStego = false, // Pure text
                    status = initialStatus, 
                    deliveryStatus = 1, // Sent (but if offline, it's pending send)
                    timestamp = timestamp,
                    replyToId = replyToId
                )
                dao.insertMessage(newMessage.toEntity())
                
                contactDao.updateLastMessage(chatId, text, timestamp, 0)

                if (isConnected) {
                    SocketClient.emitMessage(
                        id = newId,
                        text = text,
                        imageUrl = null,
                        sender = username,
                        recipient = chatId,
                        camouflageText = camouflageText,
                        timestamp = timestamp,
                        replyToId = replyToId
                    )
                }
            }
        }
    }

    fun receiveMessage(imageUri: Uri) {
        if (derivedSecretKey == null) {
            _error.value = "Secure connection not established."
            return
        }
        val secretKey = derivedSecretKey!!

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
                    status = 4, // DOWNLOADED/REVEALED
                    timestamp = System.currentTimeMillis()
                )
                 dao.insertMessage(newMessage.toEntity())
                 contactDao.updateLastMessage(chatId, secretText, newMessage.timestamp, 0) 
            }.onFailure {
                 _error.value = "Extraction failed: ${it.message}"
            }
            _isLoading.value = false
        }
    }

    fun revealMessage(message: Message) {
        if (derivedSecretKey == null) {
            _error.value = "Secure connection not established."
            return
        }
        val secretKey = derivedSecretKey!!

        viewModelScope.launch {
            _isLoading.value = true
            
            // If local path exists, use it. Otherwise need to handle (but usually we have it if it's in the bubble)
            val imagePath = message.imageUri.toString() // Assuming it's already a path or we might need copyUriToTempFile if it's content://
            
            // We might need to handle content:// vs file://. 
            // In downloadMedia we save absolute path. 
            // extractMessage expects a path string. 
            // If it's a content URI, we need a temp file.
            val finalPath = if (imagePath.startsWith("content://")) copyUriToTempFile(message.imageUri!!) ?: imagePath else imagePath

            val result = repository.extractMessage(finalPath, secretKey)
            
            result.onSuccess { secretText ->
                // Update EXISTING message
                val updatedEntity = message.toEntity().copy(text = secretText)
                dao.insertMessage(updatedEntity)
                contactDao.updateLastMessage(chatId, secretText, message.timestamp, 0)
            }.onFailure {
                _error.value = "Reveal failed: ${it.message}"
            }
            _isLoading.value = false
        }
    }

    fun downloadMedia(message: Message) {
        // Use GlobalScope to ensure download continues even if ChatScreen is closed
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Update status to DOWNLOADING (3)
                val downloadingMsg = message.toEntity().copy(status = 3)
                dao.insertMessage(downloadingMsg) // Updates existing

                val url = message.imageUri.toString()
                android.util.Log.e("ChatViewModel", "Attempting download from: $url")
                
                val request = okhttp3.Request.Builder().url(url).build()
                val response = okhttp3.OkHttpClient().newCall(request).execute()
                
                android.util.Log.e("ChatViewModel", "Download Response: ${response.code} ${response.message}")
                
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}: ${response.message}")

                val bytes = response.body?.bytes() ?: throw Exception("Empty Response Body")
                
                // Save to Gallery
                val filename = "Stego_${System.currentTimeMillis()}.png"
                
                // Save to Internal Storage First (Safe for File access)
                val file = File(appContext.getExternalFilesDir("stego_received"), filename)
                if (file.parentFile?.exists() == false) file.parentFile?.mkdirs()
                
                val fos = FileOutputStream(file)
                fos.write(bytes)
                fos.close()

                // Save copy to Gallery for User
                repository.saveToMediaStore(file)

                // Update DB with local path
                val downloadedMsg = downloadingMsg.copy(
                    imageUri = file.absolutePath, // Uri.fromFile(file).toString()
                    status = 4 // DOWNLOADED
                )
                dao.insertMessage(downloadedMsg)
                
            } catch (e: Exception) {
                e.printStackTrace()
                // Show FULL error (Class Name + Message) to debug "null" errors
                _error.value = "Download Error: $e" 
                // Revert status
                 dao.insertMessage(message.toEntity().copy(status = 2)) // Back to Pending
            }
        }
    }

    private fun uploadStegoImage(path: String, message: Message, camouflageText: String? = null) {
        viewModelScope.launch {
            try {
                val file = File(path)
                val requestFile = ProgressRequestBody(file, "image/png".toMediaTypeOrNull()) { progress ->
                     _uploadProgress.update { it + (message.id to progress) }
                }
                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                
                val response = NetworkModule.api.uploadImage(body)
                if (response.isSuccessful && response.body() != null) {
                    val url = response.body()!!.url
                    val username = UserPrefs.getUsername(appContext) ?: "Anonymous"
                    
                    // Update Status to SENT (0)
                    dao.insertMessage(message.toEntity().copy(status = 0, deliveryStatus = 1))

                    SocketClient.emitMessage(
                        id = message.id,
                        text = null, 
                        imageUrl = url, 
                        sender = username,
                        recipient = chatId,
                        camouflageText = camouflageText,
                        timestamp = message.timestamp
                    )
                    _uploadProgress.update { it - message.id }
                } else {
                    val errorBody = response.errorBody()?.string()
                    _error.value = if (!errorBody.isNullOrBlank()) {
                         try {
                              // Try to parse JSON if possible, or just truncate
                              if (errorBody.contains("\"detail\"")) {
                                   org.json.JSONObject(errorBody).getString("detail")
                              } else {
                                   "Upload Failed: ${response.code()} - ${errorBody.take(100)}"
                              }
                         } catch (e: Exception) {
                              "Upload Failed: ${response.code()} - $errorBody"
                         }
                    } else {
                         "Upload Failed: ${response.code()}"
                    }
                    _uploadProgress.update { it - message.id }
                }
            } catch (e: Exception) {
                _uploadProgress.update { it - message.id }
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



    fun deleteMessage(message: Message, deleteForEveryone: Boolean = false) {
        viewModelScope.launch {
            // 1. Remote Delete (if requested)
            if (deleteForEveryone) {
                SocketClient.emitDeleteMessage(message.id, chatId)
            }

            // 2. Local Delete
            dao.deleteMessage(message.toEntity())
            
            // Sync Last Message with Contact
            val lastMsg = dao.getLastMessage(chatId)
            if (lastMsg != null) {
                val displayMsg = if (!lastMsg.text.isNullOrEmpty()) {
                    lastMsg.text
                } else if (lastMsg.isStego) {
                    "Sent a photo"
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

    fun deleteMessages(messages: List<Message>, deleteForEveryone: Boolean = false) {
        viewModelScope.launch {
            if (deleteForEveryone) {
                messages.forEach { SocketClient.emitDeleteMessage(it.id, chatId) }
            }
            dao.deleteMessages(messages.map { it.toEntity() })
            
            val lastMsg = dao.getLastMessage(chatId)
            if (lastMsg != null) {
                val displayMsg = if (!lastMsg.text.isNullOrEmpty()) {
                    lastMsg.text
                } else if (lastMsg.isStego) {
                    "Sent a photo"
                } else {
                    "Sent an image"
                }
                contactDao.updateLastMessage(chatId, displayMsg!!, lastMsg.timestamp, 0)
            } else {
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
        status = status,
        deliveryStatus = deliveryStatus,
        timestamp = timestamp,
        replyToId = replyToId
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
        status = status,
        deliveryStatus = deliveryStatus,
        timestamp = timestamp,
        replyToId = replyToId
    )
}
