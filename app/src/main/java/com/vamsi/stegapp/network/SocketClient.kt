package com.vamsi.stegapp.network

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object SocketClient {
    private const val TAG = "SocketClient"
    private var socket: Socket? = null
    
    private val _incomingMessages = MutableSharedFlow<JSONObject>(replay = 10, extraBufferCapacity = 10)
    val incomingMessages = _incomingMessages.asSharedFlow()

    private val _deletedMessages = MutableSharedFlow<JSONObject>(replay = 10, extraBufferCapacity = 10)
    val deletedMessages = _deletedMessages.asSharedFlow()
    
    private val _userStatusUpdates = MutableSharedFlow<JSONObject>(replay = 10, extraBufferCapacity = 10)
    val userStatusUpdates = _userStatusUpdates.asSharedFlow()
    
    // Connection Status
    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()


    fun connect(username: String) {
        try {
            if (socket != null && socket!!.connected()) {
                 // Already connected
                 _isConnected.value = true
                 return
            }
            
            // Explicitly disconnect previous socket if it exists but isn't connected
            // This prevents "ghost" connections from accumulating if reconnection attempts are active
            if (socket != null) {
                socket?.disconnect()
                socket?.off()
                socket = null
            }
            
            // Use the same Base URL from NetworkModule
            val opts = IO.Options().apply {
                forceNew = true
                reconnection = true
                query = "username=$username"
            }
            socket = IO.socket(NetworkModule.BASE_URL, opts)

            // Prevent duplicate listeners
            socket?.off()

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Socket Connected: ${socket?.id()} as $username")
                _isConnected.value = true
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.d(TAG, "Socket Disconnected")
                _isConnected.value = false
            }

            // Listen for incoming messages
            socket?.on("new_message") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    Log.d(TAG, "New Message Received: $data")
                    _incomingMessages.tryEmit(data)
                }
            }

            // Listen for deleted messages
            socket?.on("delete_message") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    Log.d(TAG, "Message Deleted: $data")
                    _deletedMessages.tryEmit(data)
                }
            }
            
            // Listen for user status updates
            socket?.on("user_status") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    Log.d(TAG, "User Status Update: $data")
                    _userStatusUpdates.tryEmit(data)
                }
            }

            socket?.connect()
        } catch (e: Exception) {
            Log.e(TAG, "Socket Connection Error", e)
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
    }

    fun emitMessage(id: String, text: String?, imageUrl: String?, sender: String, recipient: String, camouflageText: String? = null, timestamp: Long = System.currentTimeMillis()) {
        val json = JSONObject().apply {
            put("id", id)
            put("text", text)
            put("imageUrl", imageUrl)
            put("sender", sender)
            put("recipient", recipient)
            put("camouflageText", camouflageText)
            put("timestamp", timestamp)
        }
        socket?.emit("send_message", json)
    }

    fun emitDeleteMessage(messageId: String, recipient: String) {
         val json = JSONObject().apply {
            put("messageId", messageId)
            put("recipient", recipient)
        }
        Log.d(TAG, "🗑️ EMITTING DELETE: messageId=$messageId, recipient=$recipient, json=$json")
        socket?.emit("delete_message", json)
    }
    
    fun emitUserStatus(username: String, status: String) {
        val json = JSONObject().apply {
            put("username", username)
            put("status", status)
        }
        Log.d(TAG, "📡 EMITTING STATUS: username=$username, status=$status")
        socket?.emit("user_status", json)
    }
}
