package com.vamsi.stegapp.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamsi.stegapp.data.db.AppDatabase
import com.vamsi.stegapp.data.db.ContactEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import com.vamsi.stegapp.data.db.MessageEntity
import java.util.UUID

data class HomeUiState(
    val contacts: List<ContactEntity> = emptyList(),
    val messageResults: List<MessageEntity> = emptyList(),
    val query: String = ""
)

class ContactViewModel(private val context: Context) : ViewModel() {

    private val dao = AppDatabase.getDatabase(context).contactDao()
    private val messageDao = AppDatabase.getDatabase(context).messageDao()

    private val _searchQuery = MutableStateFlow("")
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val _searchResults = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                kotlinx.coroutines.flow.flowOf(emptyList<MessageEntity>())
            } else {
                messageDao.searchMessages(query)
            }
        }

    val uiState = combine(
        dao.getAllContacts(),
        _searchQuery,
        _searchResults
    ) { contacts, query, messages ->
        if (query.isBlank()) {
             HomeUiState(contacts = contacts, query = query, messageResults = emptyList())
        } else {
             val filteredContacts = contacts.filter { it.name.contains(query, ignoreCase = true) }
             HomeUiState(
                 contacts = filteredContacts, 
                 messageResults = messages, 
                 query = query
             )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    init {
        viewModelScope.launch {
            // Initial setup if needed
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
