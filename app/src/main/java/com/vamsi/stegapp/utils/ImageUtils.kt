package com.vamsi.stegapp.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.InputStream

object ImageUtils {
    fun estimateCapacity(context: Context, uri: Uri): Int {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            val width = options.outWidth
            val height = options.outHeight

            if (width > 0 && height > 0) {
                // Safe estimate: 3 bits per pixel (LSB) / 8 bits per byte = ~0.375 bytes per pixel
                // But we use a safer ratio to account for overhead and encoding.
                // Let's assume ~1 byte per 3 pixels (very conservative) or just calculate max bytes.
                // Max bits = W * H * 3. 
                // Max bytes = (W * H * 3) / 8.
                // Let's reserve 50% for robustness or overhead.
                val maxBytes = (width * height * 3) / 8
                val safeChars = maxBytes / 2 // Rough safe estimate
                
                return safeChars
            } else {
                return 0
            }
        } catch (e: Exception) {
            return 0
        }
    }

    fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        return it.getLong(sizeIndex)
                    }
                }
            }
            0L
        } catch (e: Exception) {
            0L
        }
    }
}
