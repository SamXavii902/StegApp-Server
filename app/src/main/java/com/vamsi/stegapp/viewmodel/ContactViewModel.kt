package com.vamsi.stegapp.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamsi.stegapp.data.db.AppDatabase
import com.vamsi.stegapp.data.db.ContactEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class ContactViewModel(private val context: Context) : ViewModel() {

    private val dao = AppDatabase.getDatabase(context).contactDao()
    private val messageDao = AppDatabase.getDatabase(context).messageDao()

    val contacts = dao.getAllContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            com.vamsi.stegapp.network.SocketClient.incomingMessages.collect { json ->
                try {
                    val sender = json.getString("sender")
                    val text = if (json.isNull("text")) null else json.getString("text")
                    val imageUrl = if (json.isNull("imageUrl")) null else json.getString("imageUrl")
                    val timestamp = json.getLong("timestamp")
                    
                    // 1. Ensure Contact Exists
                    val existing = dao.getContactById(sender) // Assuming ID is name for simplicity
                    if (existing == null) {
                         val newContact = ContactEntity(
                            id = sender,
                            name = sender,
                            lastMessage = "New Conversation",
                            lastMessageTime = timestamp
                        )
                        dao.insertContact(newContact)
                    }

                    // 2. Save Message
                    val message = com.vamsi.stegapp.data.db.MessageEntity(
                        id = UUID.randomUUID().toString(),
                        chatId = sender, // Chat with 'Sender'
                        text = text,
                        imageUri = imageUrl,
                        isFromMe = false,
                        isStego = imageUrl != null, // Assuming all images via this socket are stego
                        timestamp = timestamp
                    )
                    messageDao.insertMessage(message)

                    // 3. Update Last Message
                    val displayMsg = if (text != null) text else "Received a hidden message"
                    dao.updateLastMessage(sender, displayMsg, timestamp, 1) // Increment unread count? -> 1 for now

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun addContact(name: String) {
        viewModelScope.launch {
            try {
                // Verify user exists on backend
                val response = com.vamsi.stegapp.network.NetworkModule.api.checkUser(name)
                if (response.isSuccessful && response.body()?.exists == true) {
                    val newContact = ContactEntity(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        lastMessage = "Start a conversation",
                        lastMessageTime = System.currentTimeMillis()
                    )
                    dao.insertContact(newContact)
                    launch(kotlinx.coroutines.Dispatchers.Main) {
                         android.widget.Toast.makeText(context, "Contact added!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    launch(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "User '$name' not found on server", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                launch(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
                android.util.Log.e("ContactViewModel", "Error checking user", e)
            }
        }
    }

    fun deleteContact(contact: ContactEntity) {
        viewModelScope.launch {
            dao.deleteContact(contact)
        }
    }
}
