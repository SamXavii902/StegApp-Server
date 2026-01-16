package com.vamsi.stegapp.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY lastMessageTime DESC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity)

    @Delete
    suspend fun deleteContact(contact: ContactEntity)

    @Query("UPDATE contacts SET lastMessage = :message, lastMessageTime = :time, unreadCount = :unreadCount WHERE name = :chatName")
    suspend fun updateLastMessage(chatName: String, message: String, time: Long, unreadCount: Int = 0)

    @Query("SELECT * FROM contacts WHERE name = :name LIMIT 1")
    suspend fun getContactById(name: String): ContactEntity?
}
