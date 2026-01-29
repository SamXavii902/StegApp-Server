package com.vamsi.stegapp.network

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SocketClient {
    private const val TAG = "SocketClient"
    private var socket: Socket? = null
    
    private val _incomingMessages = MutableSharedFlow<JSONObject>(extraBufferCapacity = 10)
    val incomingMessages = _incomingMessages.asSharedFlow()

    fun connect(username: String) {
        try {
            if (socket != null && socket!!.connected()) {
                 // Already connected, maybe check if username matches logic if needed
                 return
            }
            
            // Use the same Base URL from NetworkModule
            val opts = IO.Options().apply {
                forceNew = true
                reconnection = true
                query = "username=$username"
            }
            socket = IO.socket(NetworkModule.BASE_URL, opts)

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Socket Connected: ${socket?.id()} as $username")
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.d(TAG, "Socket Disconnected")
            }

            // Listen for incoming messages
            socket?.on("new_message") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    Log.d(TAG, "New Message Received: $data")
                    _incomingMessages.tryEmit(data)
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

    fun emitMessage(text: String?, imageUrl: String?, sender: String, recipient: String, timestamp: Long = System.currentTimeMillis()) {
        val json = JSONObject().apply {
            put("text", text)
            put("imageUrl", imageUrl)
            put("sender", sender)
            put("recipient", recipient)
            put("timestamp", timestamp)
        }
        socket?.emit("send_message", json)
    }
}
