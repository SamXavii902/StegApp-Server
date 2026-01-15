package com.vamsi.stegapp.repo

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class StegoRepository(private val context: Context) {

    init {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
    }

    private val python = Python.getInstance()
    private val stegoModule = python.getModule("stego")

    suspend fun embedMessage(imagePath: String, message: String, secretKey: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // Determine output path
                val originalFile = File(imagePath)
                val outputDir = context.getExternalFilesDir("stego_images")
                if (outputDir != null && !outputDir.exists()) {
                    outputDir.mkdirs()
                }
                val outputFile = File(outputDir, "stego_${System.currentTimeMillis()}.png")

                val result = stegoModule.callAttr("embed_pvd", imagePath, message, outputFile.absolutePath, secretKey).toString()
                
                if (result.startsWith("SUCCESS:")) {
                    val finalPath = result.removePrefix("SUCCESS:")
                    // Copy to Public Gallery
                    saveToMediaStore(File(finalPath))
                    Result.success(finalPath)
                } else {
                    Result.failure(Exception(result.removePrefix("FAILURE:")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun saveToMediaStore(file: File) {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "Stego_${System.currentTimeMillis()}.png")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/StegApp")
            put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val collection = android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)

        uri?.let {
            resolver.openOutputStream(it).use { out ->
                java.io.FileInputStream(file).use { input ->
                    input.copyTo(out!!)
                }
            }
            values.clear()
            values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(it, values, null, null)
        }
    }

    suspend fun extractMessage(imagePath: String, secretKey: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val result = stegoModule.callAttr("extract_pvd", imagePath, secretKey).toString()
                if (result.startsWith("[Error") || result.startsWith("[Extraction Failed")) {
                    Result.failure(Exception(result))
                } else {
                    Result.success(result)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
