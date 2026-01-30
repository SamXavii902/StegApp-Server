package com.vamsi.stegapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vamsi.stegapp.MainActivity
import com.vamsi.stegapp.R
import com.vamsi.stegapp.network.SocketClient
import com.vamsi.stegapp.utils.UserPrefs
import com.vamsi.stegapp.data.db.AppDatabase
import com.vamsi.stegapp.data.db.ContactEntity
import com.vamsi.stegapp.data.db.MessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

class SocketService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    companion object {
        const val CHANNEL_ID = "StegAppServiceChannel"
        const val MSG_CHANNEL_ID = "StegAppMessageChannel"
        const val NOTIFICATION_ID = 1
        var isForground = false // Track if app is visible to avoid double notifications
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        
        // Setup Socket Listeners ONCE when service is created
        val username = UserPrefs.getUsername(this)
        if (username != null) {
            // Connect Socket
            SocketClient.connect(username)

            // Listen for Messages
            serviceScope.launch {
                SocketClient.incomingMessages.collect { message ->
                    handleNewMessage(message)
                }
            }

            // Listen for Deletions
            serviceScope.launch {
                SocketClient.deletedMessages.collect { data ->
                    val messageId = data.optString("messageId")
                    if (!messageId.isNullOrEmpty()) {
                        val messageDao = AppDatabase.getDatabase(applicationContext).messageDao()
                        val contactDao = AppDatabase.getDatabase(applicationContext).contactDao()
                        
                        // 1. Identify Chat
                        val msg = messageDao.getMessageById(messageId)
                        if (msg != null) {
                            val chatName = msg.chatId
                            
                            // 2. Delete Message
                            messageDao.deleteMessageById(messageId)
                            
                            // 3. Find New Last Message
                            val newLastMsg = messageDao.getLastMessage(chatName)
                            
                            if (newLastMsg != null) {
                                val displayMsg = (newLastMsg.text ?: if (newLastMsg.imageUri != null) "Photo" else "Message")
                                contactDao.updateLastMessageOnly(chatName, displayMsg, newLastMsg.timestamp)
                            } else {
                                // Chat is empty
                                contactDao.updateLastMessageOnly(chatName, "", System.currentTimeMillis())
                            }
                        } else {
                             // ID not found locally, just try delete anyway
                             messageDao.deleteMessageById(messageId)
                        }
                    }
                }
            }
            
            // Listen for User Status Updates
            serviceScope.launch {
                SocketClient.userStatusUpdates.collect { data ->
                    val username = data.optString("username")
                    val status = data.optString("status")
                    
                    if (!username.isNullOrEmpty() && !status.isNullOrEmpty()) {
                        val contactDao = AppDatabase.getDatabase(applicationContext).contactDao()
                        val isOnline = (status == "online")
                        contactDao.updateContactStatus(username, isOnline)
                        Log.d("SocketService", "Updated $username status: $status")
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always Start Foreground immediately to avoid crash
        val notification = createForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        // Restore Connection Logic: Ensure we try to connect whenever the Service is started (App Launch)
        // This handles cases where the Service was alive but Socket disconnected, or user switched networks.
        val username = UserPrefs.getUsername(this)
        if (username != null) {
            SocketClient.connect(username)
        }
        
        return START_STICKY
    }

    private fun handleNewMessage(message: JSONObject) {
        serviceScope.launch {
            try {
                val sender = message.optString("sender")
                val text = if (message.isNull("text")) null else message.getString("text")
                val imageUrl = if (message.isNull("imageUrl")) null else message.getString("imageUrl")
                val camouflageText = if (message.isNull("camouflageText")) null else message.getString("camouflageText")
                // Use current time if timestamp is missing or weird, but prefer message timestamp
                val timestamp = message.optLong("timestamp", System.currentTimeMillis())

                val dao = AppDatabase.getDatabase(applicationContext).messageDao()
                val contactDao = AppDatabase.getDatabase(applicationContext).contactDao()

                // 1. Ensure Contact Exists
                val existing = contactDao.getContactById(sender)
                if (existing == null) {
                    val newContact = ContactEntity(
                        id = sender,
                        name = sender,
                        lastMessage = "New Conversation",
                        lastMessageTime = timestamp
                    )
                    contactDao.insertContact(newContact)
                }

                // 2. Save Message
                val msgId = message.optString("id").takeIf { !it.isNullOrEmpty() } ?: UUID.randomUUID().toString()
                val newMessage = MessageEntity(
                    id = msgId,
                    chatId = sender,
                    text = text,
                    imageUri = imageUrl,
                    isFromMe = false,
                    isStego = imageUrl != null,
                    status = if (imageUrl != null) 2 else 4, // 2: REMOTE/PENDING, 4: RECEIVED/REVEALED (Text-only)
                    timestamp = timestamp
                )
                dao.insertMessage(newMessage)

                // 3. Update Last Message
                val displayMsg = camouflageText ?: text ?: (if (imageUrl != null) "Received a hidden message" else "New Message")
                contactDao.incrementUnreadCount(sender, displayMsg, timestamp)

                // 4. Notification
                if (!isForground) {
                    var bitmap: android.graphics.Bitmap? = null
                    if (imageUrl != null) {
                         try {
                            val url = java.net.URL(imageUrl)
                            bitmap = android.graphics.BitmapFactory.decodeStream(url.openConnection().getInputStream())
                        } catch (e: Exception) {
                            Log.e("SocketService", "Failed to download notification image", e)
                        }
                    }
                    
                    val contentText = camouflageText ?: text ?: (if (imageUrl != null) "Received a hidden image" else "New Message")
                    showNewMessageNotification(sender, contentText, bitmap)
                }
            } catch (e: Exception) {
                Log.e("SocketService", "Error handling message", e)
            }
        }
    }

    private fun showNewMessageNotification(sender: String, content: String, image: android.graphics.Bitmap?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // We could pass chatName here to open specific chat
            putExtra("chatName", sender) 
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val builder = NotificationCompat.Builder(this, MSG_CHANNEL_ID)
            .setContentTitle(sender)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_email) // Default icon for now
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (image != null) {
            builder.setStyle(NotificationCompat.BigPictureStyle()
                .bigPicture(image)
                .setBigContentTitle(sender)
                .setSummaryText(content))
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build()) // Unique ID
    }

    private fun createForegroundNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("StegApp Sync")
            .setContentText("Listening for incoming messages...")
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Background Sync",
                NotificationManager.IMPORTANCE_LOW
            )
            val msgChannel = NotificationChannel(
                MSG_CHANNEL_ID,
                "New Messages",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(msgChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        SocketClient.disconnect()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
