// JNI bridge between Kotlin (WhisperNative.kt) and whisper.cpp's public C API.
// Owns nothing beyond marshalling: model load/free, running whisper_full over a
// caller-supplied float PCM buffer, and formatting the resulting segments as a
// delimited string that WhisperNative.kt parses back into TranscriptSegment objects.
//
// This talks to the real whisper.cpp API (whisper_init_from_file / whisper_full /
// whisper_full_get_segment_*). It intentionally does not fabricate output: if
// whisper_init_from_file fails, nativeLoadModel returns 0 and the Kotlin layer throws
// ModelNotReadyException; if whisper_full returns non-zero, nativeTranscribe returns
// null and the Kotlin layer throws PipelineException(TRANSCRIPTION_FAILED).

#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper.h"

#define LOG_TAG "ReelBotWhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_reelbot_mobile_ai_native_WhisperNative_nativeLoadModel(
        JNIEnv *env, jobject /*thiz*/, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);

    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    if (!path) return 0;
    // Falls back to CPU automatically if no GPU backend is compiled in for this ABI.
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);

    env->ReleaseStringUTFChars(modelPath, path);

    if (ctx == nullptr) {
        LOGE("whisper_init_from_file_with_params failed");
        return 0;
    }
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_reelbot_mobile_ai_native_WhisperNative_nativeRelease(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong ctxPtr) {
    if (ctxPtr == 0) return;
    auto *ctx = reinterpret_cast<struct whisper_context *>(ctxPtr);
    whisper_free(ctx);
}

// Returns a UTF-8 string with one line per segment, "startMs|endMs|text", segments
// separated by \u0001. Returns nullptr on failure — the Kotlin layer treats null as a
// real transcription failure, never as "no speech."
JNIEXPORT jstring JNICALL
Java_com_reelbot_mobile_ai_native_WhisperNative_nativeTranscribe(
        JNIEnv *env, jobject /*thiz*/, jlong ctxPtr, jfloatArray samples,
        jstring language, jint nThreads) {
    if (ctxPtr == 0) return nullptr;
    auto *ctx = reinterpret_cast<struct whisper_context *>(ctxPtr);

    jsize numSamples = env->GetArrayLength(samples);
    jfloat *sampleData = env->GetFloatArrayElements(samples, nullptr);

    if (!sampleData || numSamples <= 0) return nullptr;

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_special = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.n_threads = nThreads > 0 ? nThreads : 4;
    params.translate = false;
    params.no_context = true;
    params.single_segment = false;

    const char *langChars = nullptr;
    std::string langStr;
    if (language != nullptr) {
        const char *raw = env->GetStringUTFChars(language, nullptr);
        langStr = raw;
        env->ReleaseStringUTFChars(language, raw);
        if (!langStr.empty() && langStr != "auto") {
            params.language = langStr.c_str();
        } else {
            params.language = "auto";
            params.detect_language = false;
        }
    }

    int result = whisper_full(ctx, params, sampleData, numSamples);
    env->ReleaseFloatArrayElements(samples, sampleData, JNI_ABORT);

    if (result != 0) {
        LOGE("whisper_full failed with code %d", result);
        return nullptr;
    }

    int nSegments = whisper_full_n_segments(ctx);
    std::string out;
    out.reserve(nSegments * 64);

    for (int i = 0; i < nSegments; i++) {
        // whisper.cpp timestamps are in 10ms units.
        int64_t t0Ms = whisper_full_get_segment_t0(ctx, i) * 10;
        int64_t t1Ms = whisper_full_get_segment_t1(ctx, i) * 10;
        const char *text = whisper_full_get_segment_text(ctx, i);

        if (i > 0) out += "\x01";
        out += std::to_string(t0Ms);
        out += "|";
        out += std::to_string(t1Ms);
        out += "|";
        out += text;
    }

    // Whisper emits ordinary UTF-8; NewStringUTF expects modified UTF-8.
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(out.size()));
    if (!bytes) return nullptr;
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(out.size()), reinterpret_cast<const jbyte*>(out.data()));
    jclass stringClass = env->FindClass("java/lang/String");
    jmethodID ctor = env->GetMethodID(stringClass, "<init>", "([BLjava/lang/String;)V");
    jstring encoding = env->NewStringUTF("UTF-8");
    auto text = static_cast<jstring>(env->NewObject(stringClass, ctor, bytes, encoding));
    env->DeleteLocalRef(bytes);
    env->DeleteLocalRef(encoding);
    env->DeleteLocalRef(stringClass);
    return text;
}

} // extern "C"
