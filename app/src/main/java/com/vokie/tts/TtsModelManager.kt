package com.vokie.tts

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** Validates and installs local model packs. It contains no downloader or network client. */
class TtsModelManager(private val context: Context) {
    private val root = File(context.filesDir, "tts-models")
    private val _installedLanguages = MutableStateFlow(scanInstalled())
    val installedLanguages: StateFlow<Set<TtsLanguage>> = _installedLanguages.asStateFlow()

    fun modelDirectory(language: TtsLanguage): File = File(root, language.iso6393)
    fun modelFile(language: TtsLanguage): File = File(modelDirectory(language), requirePackage(language).model.fileName)
    fun tokensFile(language: TtsLanguage): File = File(modelDirectory(language), requirePackage(language).tokens.fileName)

    fun isInstalled(language: TtsLanguage): Boolean {
        val pack = language.modelPackage ?: return false
        return File(modelDirectory(language), pack.model.fileName).length() == pack.model.sizeBytes &&
            File(modelDirectory(language), pack.tokens.fileName).length() == pack.tokens.sizeBytes
    }

    fun missingLanguages(): Set<TtsLanguage> = TtsLanguage.entries.filterNot(::isInstalled).toSet()
    fun availableOfficialPackages(): Set<TtsLanguage> = TtsLanguage.entries.filter { it.modelPackage != null }.toSet()

    suspend fun installZip(language: TtsLanguage, uri: Uri) = withContext(Dispatchers.IO) {
        val pack = requirePackage(language)
        root.mkdirs()
        val destination = modelDirectory(language)
        val temporary = File(root, ".${language.iso6393}.installing")
        temporary.deleteRecursively(); temporary.mkdirs()
        val expected = listOfNotNull(pack.model, pack.tokens, pack.lexicon).associateBy { it.fileName }
        val installed = mutableSetOf<String>()
        var totalUncompressed = 0L
        try {
            context.contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.isDirectory) continue
                        val name = entry.name.substringAfterLast('/')
                        val spec = expected[name] ?: continue
                        check(name !in installed) { "Duplicate $name in TTS model pack" }
                        val outputFile = File(temporary, name)
                        val digest = MessageDigest.getInstance("SHA-256")
                        var fileBytes = 0L
                        FileOutputStream(outputFile).use { output ->
                            val buffer = ByteArray(COPY_BUFFER_BYTES)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                fileBytes += count; totalUncompressed += count
                                if (fileBytes > spec.sizeBytes || totalUncompressed > MAX_PACK_BYTES) throw TtsException(TtsErrorCode.MODEL_INVALID, "TTS model pack exceeds verified file sizes")
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                            }
                        }
                        check(fileBytes == spec.sizeBytes) { "$name has an unexpected size" }
                        check(digest.digest().hex() == spec.sha256) { "$name checksum does not match the official package" }
                        installed += name
                    }
                }
            } ?: throw TtsException(TtsErrorCode.MODEL_INVALID, "Selected TTS model pack could not be opened")
            check(installed == expected.keys) { "TTS model pack is missing ${expected.keys - installed}" }
            destination.deleteRecursively()
            check(temporary.renameTo(destination)) { "TTS model pack could not be installed" }
            _installedLanguages.value = scanInstalled()
        } catch (error: Throwable) {
            temporary.deleteRecursively()
            if (error is TtsException) throw error
            throw TtsException(TtsErrorCode.MODEL_INVALID, error.message ?: "Invalid TTS model pack", error)
        }
    }

    fun releaseUnusedModel(activeLanguage: TtsLanguage?) {
        // Native contexts are released by TtsEngine. Installed files remain offline and reusable.
        require(activeLanguage == null || activeLanguage in TtsLanguage.entries)
    }

    private fun scanInstalled() = TtsLanguage.entries.filter(::isInstalled).toSet()
    private fun requirePackage(language: TtsLanguage) = language.modelPackage
        ?: throw TtsException(TtsErrorCode.UNSUPPORTED_LANGUAGE, "No official sherpa-onnx vits-mms package is available for ${language.nativeName}.")

    private fun ByteArray.hex() = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private companion object {
        const val COPY_BUFFER_BYTES = 64 * 1024
        const val MAX_PACK_BYTES = 150L * 1024 * 1024
    }
}
