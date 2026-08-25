package com.vokie.stt

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/** Installs a user-supplied GGML model into private app storage. It never performs network I/O. */
class SttModelStore(private val context: Context, private val model: SttModel) {
    fun isInstalled(): Boolean = model.localFile(context).let { it.isFile && it.length() in MIN_MODEL_BYTES..MAX_MODEL_BYTES }

    suspend fun install(uri: Uri): File = withContext(Dispatchers.IO) {
        val destination = model.localFile(context)
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.installing")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    val digest = MessageDigest.getInstance("SHA-256")
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_MODEL_BYTES) throw IOException("Selected model exceeds the supported size limit")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    val actualHash = digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
                    require(actualHash == model.sha256) { "Selected model checksum does not match ${model.displayName}" }
                }
            } ?: throw IOException("Selected model could not be opened")
            validateHeaderAndSize(temporary)
            if (destination.exists() && !destination.delete()) throw IOException("Existing STT model could not be replaced")
            if (!temporary.renameTo(destination)) throw IOException("STT model could not be installed")
            destination
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    fun delete() { model.localFile(context).delete() }

    private fun validateHeaderAndSize(file: File) {
        require(file.length() in MIN_MODEL_BYTES..MAX_MODEL_BYTES) { "Selected file is not a supported Whisper model size" }
        val magic = file.inputStream().use { input -> ByteArray(4).also { check(input.read(it) == it.size) } }
        require(magic.contentEquals(GGML_MAGIC)) { "Selected file is not a whisper.cpp GGML model" }
    }

    companion object {
        private const val COPY_BUFFER_BYTES = 64 * 1024
        private const val MIN_MODEL_BYTES = 10L * 1024 * 1024
        private const val MAX_MODEL_BYTES = 300L * 1024 * 1024
        private val GGML_MAGIC = byteArrayOf(0x6c, 0x6d, 0x67, 0x67)
    }
}
