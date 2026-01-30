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
            // Socket connection moved to SocketService
            // Incoming message handling moved to SocketService
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

    fun clearUnreadCount(chatName: String) {
        viewModelScope.launch {
            dao.resetUnreadCount(chatName)
        }
    }
}
