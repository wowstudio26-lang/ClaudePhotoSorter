package com.wowstudio.claudephotosorter

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

class PhotoAnalyzer(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    suspend fun analyze(uri: Uri, name: String): PhotoItem = try {
        val image = InputImage.fromFilePath(appContext, uri)
        val labels = labeler.process(image).await()
            .sortedByDescending { it.confidence }
            .take(8)
            .map { it.text }
        PhotoItem(
            uri = uri.toString(),
            displayName = name,
            category = CategoryRules.classify(labels),
            labels = labels,
            sha256 = sha256(uri)
        )
    } catch (e: Exception) {
        PhotoItem(uri.toString(), name, error = e.localizedMessage ?: "Could not analyze image")
    }

    private fun sha256(uri: Uri): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        } ?: return null
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { null }

    fun close() = labeler.close()
}
