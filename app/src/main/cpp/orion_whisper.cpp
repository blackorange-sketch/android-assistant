#include <jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>

#include <string>
#include <vector>

#include "whisper.h"

#define TAG "ORION_WHISPER"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct AssetContext {
    AAsset* asset;
};

static size_t assetRead(void* ctx, void* output, size_t readSize) {
    auto* ac = static_cast<AssetContext*>(ctx);
    return AAsset_read(ac->asset, output, readSize);
}

static bool assetEof(void* ctx) {
    auto* ac = static_cast<AssetContext*>(ctx);
    return AAsset_getRemainingLength64(ac->asset) <= 0;
}

static void assetClose(void* ctx) {
    auto* ac = static_cast<AssetContext*>(ctx);
    if (ac->asset) {
        AAsset_close(ac->asset);
        ac->asset = nullptr;
    }
    delete ac;
}

static whisper_context* loadModel(
    JNIEnv* env,
    jobject assetManagerObject,
    const char* assetPath
) {
    AAssetManager* manager =
        AAssetManager_fromJava(env, assetManagerObject);

    if (!manager) {
        LOGE("AAssetManager_fromJava failed");
        return nullptr;
    }

    AAsset* asset =
        AAssetManager_open(manager, assetPath, AASSET_MODE_STREAMING);

    if (!asset) {
        LOGE("Failed to open model asset: %s", assetPath);
        return nullptr;
    }

    auto* ac = new AssetContext();
    ac->asset = asset;

    whisper_model_loader loader{};
    loader.context = ac;
    loader.read = assetRead;
    loader.eof = assetEof;
    loader.close = assetClose;

    whisper_context_params params =
        whisper_context_default_params();

    params.use_gpu = false;

    LOGI("Initializing whisper.cpp model");

    whisper_context* ctx =
        whisper_init_with_params(&loader, params);

    if (!ctx) {
        LOGE("whisper_init_with_params failed");
        assetClose(ac);
        return nullptr;
    }

    LOGI("whisper.cpp model initialized");
    return ctx;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_androidassistant_voice_LocalAsrEngine_nativeInit(
    JNIEnv* env,
    jobject,
    jobject assetManager,
    jstring assetPath
) {
    const char* path =
        env->GetStringUTFChars(assetPath, nullptr);

    whisper_context* ctx =
        loadModel(env, assetManager, path);

    env->ReleaseStringUTFChars(assetPath, path);

    return reinterpret_cast<jlong>(ctx);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_androidassistant_voice_LocalAsrEngine_nativeTranscribe(
    JNIEnv* env,
    jobject,
    jlong contextPtr,
    jfloatArray audio,
    jint sampleRate,
    jint threads
) {
    auto* ctx =
        reinterpret_cast<whisper_context*>(contextPtr);

    if (!ctx || sampleRate != WHISPER_SAMPLE_RATE) {
        return env->NewStringUTF("");
    }

    const jsize length =
        env->GetArrayLength(audio);

    jfloat* samples =
        env->GetFloatArrayElements(audio, nullptr);

    whisper_full_params params =
        whisper_full_default_params(
            WHISPER_SAMPLING_GREEDY
        );

    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;

    params.translate = false;
    params.language = "uk";

    params.n_threads = threads;
    params.offset_ms = 0;
    params.no_context = true;
    params.single_segment = false;

    params.temperature = 0.0f;
    params.temperature_inc = 0.0f;

    LOGI("Running whisper_full: samples=%d threads=%d",
         length, threads);

    int result =
        whisper_full(
            ctx,
            params,
            samples,
            length
        );

    env->ReleaseFloatArrayElements(
        audio,
        samples,
        JNI_ABORT
    );

    if (result != 0) {
        LOGE("whisper_full failed: %d", result);
        return env->NewStringUTF("");
    }

    std::string text;

    const int segments =
        whisper_full_n_segments(ctx);

    for (int i = 0; i < segments; ++i) {
        const char* segment =
            whisper_full_get_segment_text(ctx, i);

        if (segment) {
            text += segment;
        }
    }

    return env->NewStringUTF(text.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_androidassistant_voice_LocalAsrEngine_nativeFree(
    JNIEnv*,
    jobject,
    jlong contextPtr
) {
    auto* ctx =
        reinterpret_cast<whisper_context*>(contextPtr);

    if (ctx) {
        whisper_free(ctx);
    }
}
