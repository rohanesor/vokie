package com.vokie.translation

import android.app.Activity
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import android.widget.TextView
import java.io.File
import java.security.MessageDigest
import kotlin.math.ceil

/** Debug-only foreground CT2 validation surface. It is not a launcher or production UI. */
class Ct2SmokeActivity : Activity() {
    private lateinit var output: TextView
    private var peakPssKb = 0
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        output = TextView(this).apply { textSize = 18f; setPadding(32, 48, 32, 48); text = "CT2 SMOKE TEST\n\nActivity started" }
        setContentView(output); log("onCreate"); log("starting worker")
        Thread({ runSmoke() }, "ct2-smoke").start()
    }
    private fun runSmoke() {
        try {
            log("worker started"); status("JNI loading..."); log("loading JNI")
            val native = Ctranslate2Native(); log("JNI loaded"); status("JNI loaded\nModel staging..."); log("staging model")
            val dir = File(filesDir, "models/ct2/nllb600m"); val model = File(dir, "model.bin")
            check(model.length() == 619_704_329L) { "model.bin size=${model.length()}" }
            listOf("config.json", "shared_vocabulary.json", "sentencepiece.bpe.model").forEach { check(File(dir, it).isFile) { "Missing CT2 $it" } }
            log("verifying model"); check(model.sha256() == MODEL_SHA) { "CT2 model integrity mismatch" }; log("model hash verified")
            peakPssKb = pssKb(); log("MEMORY beforeLoadPssKb=$peakPssKb")
            val loadStarted = SystemClock.elapsedRealtime(); log("nativeLoadModel begin")
            val handle = native.nativeLoadModel(dir.absolutePath); check(handle != 0L)
            recordPss(); log("nativeLoadModel success ms=${SystemClock.elapsedRealtime() - loadStarted} pssKb=${pssKb()}")
            try {
                val cases = listOf(
                    Triple("EN", "HI", "Help me."), Triple("EN", "TA", "Help me."),
                    Triple("HI", "EN", "मुझे मदद चाहिए।"), Triple("HI", "TA", "मुझे मदद चाहिए।"),
                    Triple("TA", "EN", "எனக்கு உதவி தேவை."), Triple("TA", "HI", "எனக்கு உதவி தேவை.")
                )
                status("Model ready\nSix directions...")
                cases.forEach { (source, target, text) -> direct(native, handle, source, target, text) }
                listOf(Triple("EN", "EN", "Help me."), Triple("HI", "HI", "मुझे मदद चाहिए।"), Triple("TA", "TA", "எனக்கு உதவி தேவை.")).forEach { (source, target, text) ->
                    check(source == target); log("BYPASS $source->$target nativeTranslate skipped output=$text")
                }
                status("Six directions passed\n20 warm translations...")
                val samples = (1..20).map { direct(native, handle, "EN", "HI", "Help me.", "WARM_$it") }
                val ordered = samples.sorted(); val median = ordered[9]; val p95 = ordered[ceil(samples.size * .95).toInt() - 1]
                log("STABILITY first=${samples.first()} median=$median p95=$p95 final=${samples.last()} pssKb=${pssKb()} peakPssKb=$peakPssKb")
                status("PASS\n6/6 directions\n20 warm translations")
            } finally { native.nativeUnloadModel(handle); log("model unloaded pssKb=${pssKb()} peakPssKb=$peakPssKb") }
        } catch (error: Throwable) {
            Log.e(TAG, "FAILURE ${error::class.java.name}: ${error.message}", error); status("FAIL\n${error::class.java.simpleName}: ${error.message}")
        }
    }
    private fun direct(native: Ctranslate2Native, handle: Long, source: String, target: String, text: String, label: String = "RESULT"): Long {
        check(source != target); val start = SystemClock.elapsedRealtime(); log("$label begin $source->$target")
        val value = native.nativeTranslate(handle, source, target, text); val ms = SystemClock.elapsedRealtime() - start
        check(value.isNotBlank()) { "Empty $source->$target result" }; recordPss(); log("$label $source->$target ms=$ms output=$value"); return ms
    }
    private fun pssKb(): Int { val info = Debug.MemoryInfo(); Debug.getMemoryInfo(info); return info.totalPss }
    private fun recordPss() { peakPssKb = maxOf(peakPssKb, pssKb()) }
    private fun log(message: String) = Log.i(TAG, message)
    private fun status(message: String) = runOnUiThread { output.text = "CT2 SMOKE TEST\n\n$message" }
    private fun File.sha256() = inputStream().use { input -> val digest = MessageDigest.getInstance("SHA-256"); val bytes = ByteArray(65536); generateSequence { input.read(bytes).takeIf { it > 0 } }.forEach { digest.update(bytes, 0, it) }; digest.digest().joinToString("") { "%02x".format(it) } }
    companion object { const val TAG = "VOKIE_CT2_SMOKE"; const val MODEL_SHA = "ca3362e6e81906c0cf9c33bd6917674222c71d69617d0afb18507ce0b6c2e2e8" }
}
