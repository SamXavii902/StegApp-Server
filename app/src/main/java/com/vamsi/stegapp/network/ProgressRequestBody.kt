package com.vamsi.stegapp.network

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import android.os.Handler
import android.os.Looper

class ProgressRequestBody(
    private val file: File,
    private val contentType: MediaType?,
    private val onProgress: (Float) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val fileLength = file.length()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val fis = file.inputStream()
        var uploaded: Long = 0
        
        // Use Handler to dispatch to Main Thread if needed, but VM handles Flow collection on Main normally
        // We calling callback on background thread (Retrofit's thread).
        
        try {
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                sink.write(buffer, 0, read)
                uploaded += read
                onProgress(uploaded.toFloat() / fileLength)
            }
        } finally {
            fis.close()
        }
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 2048
    }
}
