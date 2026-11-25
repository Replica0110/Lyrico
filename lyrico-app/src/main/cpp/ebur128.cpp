#include <jni.h>
#include <cmath>
#include <algorithm>
#include <vector>
#include "ebur128/ebur128.h"

extern "C" {

// 初始化状态
JNIEXPORT jlong JNICALL
Java_com_lonx_lyrico_platform_audio_LibEbuR128_initNative(JNIEnv *env, jobject thiz, jint channels,
                                                 jint sampleRate, jboolean useTruePeak) {
    const int peakMode = useTruePeak == JNI_TRUE
        ? EBUR128_MODE_TRUE_PEAK
        : EBUR128_MODE_SAMPLE_PEAK;
    ebur128_state* state = ebur128_init((size_t)channels, (size_t)sampleRate,
                                        EBUR128_MODE_I | peakMode);
    return reinterpret_cast<jlong>(state);
}

// 释放内存
JNIEXPORT void JNICALL
Java_com_lonx_lyrico_platform_audio_LibEbuR128_destroyNative(JNIEnv *env, jobject thiz, jlong statePtr) {
    auto* state = reinterpret_cast<ebur128_state*>(statePtr);
    if (state != nullptr) {
        ebur128_destroy(&state);
    }
}

// 处理音频帧 (直接内存，零拷贝)
JNIEXPORT void JNICALL
Java_com_lonx_lyrico_platform_audio_LibEbuR128_processDirectNative(JNIEnv *env, jobject thiz, jlong statePtr,
                                                          jobject directBuffer, jint format, jint frames) {
    auto* state = reinterpret_cast<ebur128_state*>(statePtr);
    if (!state || !directBuffer || frames <= 0) return;

    void* bufferAddress = env->GetDirectBufferAddress(directBuffer);
    if (!bufferAddress) return;

    if (format == 1) { // 1 = Short PCM
        ebur128_add_frames_short(state, static_cast<const short*>(bufferAddress), (size_t)frames);
    } else if (format == 2) { // 2 = Float (32-bit Float PCM)
        ebur128_add_frames_float(state, static_cast<const float*>(bufferAddress), (size_t)frames);
    }
}

// 获取最终的 LUFS 响度
JNIEXPORT jdouble JNICALL
Java_com_lonx_lyrico_platform_audio_LibEbuR128_getLoudnessNative(JNIEnv *env, jobject thiz, jlong statePtr) {
    auto* state = reinterpret_cast<ebur128_state*>(statePtr);
    if (!state) return -70.0;

    double loudness = -70.0; // 默认极静门限
    // 增加错误码校验，仅在 SUCCESS 时采纳结果
    if (ebur128_loudness_global(state, &loudness) != EBUR128_SUCCESS) {
        return -70.0;
    }
    return loudness;
}

// 获取选定类型的峰值
JNIEXPORT jdouble JNICALL
Java_com_lonx_lyrico_platform_audio_LibEbuR128_getPeakNative(JNIEnv *env, jobject thiz, jlong statePtr,
                                                    jint channels, jboolean useTruePeak) {
    auto* state = reinterpret_cast<ebur128_state*>(statePtr);
    if (!state || channels <= 0) return 0.0;
    double maxPeak = 0.0;
    for (int c = 0; c < channels; ++c) {
        double channelPeak = 0.0;
        const int result = useTruePeak == JNI_TRUE
            ? ebur128_true_peak(state, (unsigned int)c, &channelPeak)
            : ebur128_sample_peak(state, (unsigned int)c, &channelPeak);
        if (result == EBUR128_SUCCESS) {
            maxPeak = std::max(maxPeak, channelPeak);
        }
    }
    return maxPeak;
}

// 获取多个状态合并后的全局 LUFS 响度，用于专辑 ReplayGain
JNIEXPORT jdouble JNICALL
Java_com_lonx_lyrico_platform_audio_LibEbuR128_getMultipleLoudnessNative(JNIEnv *env, jobject thiz, jlongArray statePtrs) {
    if (!statePtrs) return -70.0;

    const jsize length = env->GetArrayLength(statePtrs);
    if (length <= 0) return -70.0;

    std::vector<jlong> ptrValues(static_cast<size_t>(length));
    env->GetLongArrayRegion(statePtrs, 0, length, ptrValues.data());

    std::vector<ebur128_state*> states;
    states.reserve(static_cast<size_t>(length));
    for (jlong ptr : ptrValues) {
        auto* state = reinterpret_cast<ebur128_state*>(ptr);
        if (state != nullptr) {
            states.push_back(state);
        }
    }

    if (states.empty()) return -70.0;

    double loudness = -70.0;
    if (ebur128_loudness_global_multiple(states.data(), states.size(), &loudness) != EBUR128_SUCCESS) {
        return -70.0;
    }
    return loudness;
}

}
