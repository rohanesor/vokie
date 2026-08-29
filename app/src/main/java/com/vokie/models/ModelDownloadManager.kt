package com.vokie.models

import android.content.Context
import android.net.ConnectivityManager
import android.os.StatFs
import com.vokie.BuildConfig
import com.vokie.tts.TtsLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val language: TtsLanguage, val percent: Int) : DownloadState
    data class Complete(val language: TtsLanguage) : DownloadState
    data class Error(val language: TtsLanguage, val message: String) : DownloadState
}

/** User-confirmed one-time language pack delivery. Runtime speech never uses the network. */
class ModelDownloadManager(private val context: Context, private val bundled: BundledModelStore) {
    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    suspend fun download(language: TtsLanguage) = withContext(Dispatchers.IO) {
        require(language != TtsLanguage.ENGLISH) { "English is included in iTantra." }
        val model = bundled.spec("tts/${language.iso6393}/model.onnx")
        val tokens = bundled.spec("tts/${language.iso6393}/tokens.txt")
        requireFreeSpace(model.sizeBytes + tokens.sizeBytes + SAFETY_BYTES)
        val root = File(context.filesDir, "models/tts")
        val target = File(root, language.iso6393)
        val temporary = File(root, ".${language.iso6393}.downloading")
        try {
            root.mkdirs(); temporary.mkdirs()
            downloadFile(language, "model.onnx", model, File(temporary, "model.onnx"), model.sizeBytes + tokens.sizeBytes)
            downloadFile(language, "tokens.txt", tokens, File(temporary, "tokens.txt"), model.sizeBytes + tokens.sizeBytes)
            verify(File(temporary, "model.onnx"), model)
            verify(File(temporary, "tokens.txt"), tokens)
            target.deleteRecursively()
            check(temporary.renameTo(target)) { "Could not finalize downloaded ${language.nativeName} pack" }
            _state.value = DownloadState.Complete(language)
        } catch (error: Throwable) {
            temporary.deleteRecursively()
            _state.value = DownloadState.Error(language, error.message ?: "Language pack download failed")
            throw error
        }
    }

    private fun downloadFile(language: TtsLanguage, name: String, spec: BundledModelStore.ModelFile, target: File, total: Long) {
        val existing = target.takeIf(File::isFile)?.length() ?: 0L
        if (existing == spec.sizeBytes) return
        val url = URL("${BuildConfig.MODEL_CDN_BASE_URL}/models/v1.0.0/tts/vits-mms-${language.iso6393}/$name")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS; readTimeout = READ_TIMEOUT_MS
            if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            check(connection.responseCode in setOf(HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_PARTIAL)) { "Download failed: HTTP ${connection.responseCode}" }
            val append = existing > 0 && connection.responseCode == HttpURLConnection.HTTP_PARTIAL
            if (!append) target.delete()
            connection.inputStream.use { input -> FileOutputStream(target, append).use { output ->
                val buffer = ByteArray(BUFFER); var downloaded = if (append) existing else 0L
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    downloaded += count
                    check(downloaded <= spec.sizeBytes) { "Downloaded $name exceeds its verified size" }
                    output.write(buffer, 0, count)
                    _state.value = DownloadState.Downloading(language, ((downloaded * 100) / total).toInt().coerceIn(0, 99))
                }
            } }
            verify(target, spec)
        } finally { connection.disconnect() }
    }

    private fun verify(file: File, spec: BundledModelStore.ModelFile) {
        check(file.length() == spec.sizeBytes) { "Downloaded file has an unexpected size" }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(BUFFER); while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) } }
        check(digest.digest().joinToString("") { "%02x".format(it) } == spec.sha256) { "Downloaded file checksum does not match release manifest" }
    }
    private fun requireFreeSpace(required: Long) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        check(cm.activeNetwork != null && !cm.isActiveNetworkMetered) { "Connect to Wi-Fi to download this offline language pack." }
        check(StatFs(context.filesDir.path).availableBytes >= required) { "At least 150 MB of free storage is required." }
    }
    private companion object { const val BUFFER = 64 * 1024; const val SAFETY_BYTES = 16L * 1024 * 1024; const val CONNECT_TIMEOUT_MS = 15_000; const val READ_TIMEOUT_MS = 30_000 }
}
