#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <atomic>
#include <string>
#include <thread>
#include "whisper.h"

namespace {
constexpr const char * TAG = "VOKIE][STT";
// The app owns one inference context, so one process-local abort flag is sufficient.
std::atomic<bool> g_abort_requested{false};

bool whisper_abort_callback(void *) {
    return g_abort_requested.load(std::memory_order_relaxed);
}

void throw_java(JNIEnv * env, const char * type, const char * message) {
    jclass clazz = env->FindClass(type);
    if (clazz != nullptr) env->ThrowNew(clazz, message);
}

whisper_context * context_from(jlong handle) {
    return reinterpret_cast<whisper_context *>(handle);
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_vokie_stt_WhisperNative_nativeInit(JNIEnv * env, jobject, jstring model_path) {
    if (model_path == nullptr) {
        throw_java(env, "java/lang/IllegalArgumentException", "Model path is required");
        return 0;
    }
    const char * path = env->GetStringUTFChars(model_path, nullptr);
    if (path == nullptr) return 0;
    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;
    whisper_context * context = whisper_init_from_file_with_params(path, params);
    env->ReleaseStringUTFChars(model_path, path);
    if (context == nullptr) {
        throw_java(env, "java/lang/IllegalStateException", "whisper.cpp could not load the model");
        return 0;
    }
    return reinterpret_cast<jlong>(context);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_vokie_stt_WhisperNative_nativeTranscribe(
        JNIEnv * env,
        jobject,
        jlong handle,
        jfloatArray samples,
        jstring language,
        jint requested_threads) {
    whisper_context * context = context_from(handle);
    if (context == nullptr || samples == nullptr || language == nullptr) {
        throw_java(env, "java/lang/IllegalStateException", "Whisper context, audio, and language are required");
        return nullptr;
    }

    const char * language_code = env->GetStringUTFChars(language, nullptr);
    if (language_code == nullptr) return nullptr;
    if (whisper_lang_id(language_code) < 0) {
        env->ReleaseStringUTFChars(language, language_code);
        throw_java(env, "java/lang/IllegalArgumentException", "Unsupported Whisper language");
        return nullptr;
    }

    const jsize sample_count = env->GetArrayLength(samples);
    jfloat * audio = env->GetFloatArrayElements(samples, nullptr);
    if (audio == nullptr) {
        env->ReleaseStringUTFChars(language, language_code);
        return nullptr;
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = language_code;
    params.translate = false;
    params.no_context = true;
    params.single_segment = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.suppress_blank = true;
    params.suppress_nst = true;
    g_abort_requested.store(false, std::memory_order_relaxed);
    params.abort_callback = whisper_abort_callback;
    params.abort_callback_user_data = nullptr;
    const unsigned int available = std::max(1u, std::thread::hardware_concurrency());
    params.n_threads = std::max(1, std::min(static_cast<int>(available), static_cast<int>(requested_threads)));

    whisper_reset_timings(context);
    const int result = whisper_full(context, params, audio, sample_count);
    env->ReleaseFloatArrayElements(samples, audio, JNI_ABORT);
    env->ReleaseStringUTFChars(language, language_code);
    if (result != 0) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "whisper_full failed with code %d", result);
        throw_java(env, "java/lang/IllegalStateException", "whisper.cpp inference failed");
        return nullptr;
    }

    std::string text;
    const int segments = whisper_full_n_segments(context);
    for (int i = 0; i < segments; ++i) {
        const char * segment = whisper_full_get_segment_text(context, i);
        if (segment != nullptr) text.append(segment);
    }
    return env->NewStringUTF(text.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_vokie_stt_WhisperNative_nativeAbort(JNIEnv *, jobject) {
    g_abort_requested.store(true, std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_com_vokie_stt_WhisperNative_nativeFree(JNIEnv *, jobject, jlong handle) {
    whisper_context * context = context_from(handle);
    if (context != nullptr) whisper_free(context);
}
