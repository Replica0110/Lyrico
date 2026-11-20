#include <jni.h>
#include <unistd.h>
#include <cerrno>
#include <algorithm>
#include <memory>
#include <string>
#include <vector>
extern "C" {
#include "libavformat/avformat.h"
#include "libavcodec/avcodec.h"
#include "libswresample/swresample.h"
}

namespace {
struct Decoder {
    int fd = -1;
    int64_t offset = 0, length = -1, position = 0;
    AVIOContext* io = nullptr;
    AVFormatContext* format = nullptr;
    AVCodecContext* codec = nullptr;
    SwrContext* resampler = nullptr;
    AVPacket* packet = nullptr;
    AVFrame* frame = nullptr;
    int stream = -1;
    bool draining = false;
    int rate = 0, channels = 0;
    std::vector<float> pcm;
    ~Decoder() {
        av_frame_free(&frame);
        av_packet_free(&packet);
        swr_free(&resampler);
        avcodec_free_context(&codec);
        avformat_close_input(&format);
        if (io) av_freep(&io->buffer);
        avio_context_free(&io);
        if (fd >= 0) close(fd);
    }
};
int readInput(void* opaque, uint8_t* data, int size) {
    auto* d = static_cast<Decoder*>(opaque);
    if (d->length >= 0) size = static_cast<int>(std::min<int64_t>(size, d->length - d->position));
    if (size <= 0) return AVERROR_EOF;
    ssize_t count;
    do { count = pread(d->fd, data, size, d->offset + d->position); } while (count < 0 && errno == EINTR);
    if (count < 0) return AVERROR(errno);
    if (count == 0) return AVERROR_EOF;
    d->position += count;
    return static_cast<int>(count);
}
int64_t seekInput(void* opaque, int64_t offset, int whence) {
    auto* d = static_cast<Decoder*>(opaque);
    if (whence == AVSEEK_SIZE) return d->length >= 0 ? d->length : AVERROR(ENOSYS);
    whence &= ~AVSEEK_FORCE;
    int64_t next;
    if (whence == SEEK_SET) next = offset;
    else if (whence == SEEK_CUR) next = d->position + offset;
    else if (whence == SEEK_END && d->length >= 0) next = d->length + offset;
    else return AVERROR(ENOSYS);
    if (next < 0 || (d->length >= 0 && next > d->length)) return AVERROR(EINVAL);
    d->position = next;
    return next;
}
void fail(JNIEnv* env, const char* operation, int error) {
    char detail[AV_ERROR_MAX_STRING_SIZE];
    av_strerror(error, detail, sizeof(detail));
    std::string message = std::string(operation) + ": " + detail;
    env->ThrowNew(env->FindClass("java/io/IOException"), message.c_str());
}
Decoder* get(jlong pointer) { return reinterpret_cast<Decoder*>(pointer); }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_lonx_lyrico_platform_audio_FfmpegAudioDecoder_openNative(JNIEnv* env, jobject, jint fd, jlong offset, jlong length) {
    auto d = std::make_unique<Decoder>();
    d->fd = dup(fd);
    if (d->fd < 0) { fail(env, "dup", AVERROR(errno)); return 0; }
    d->offset = offset;
    d->length = length;
    uint8_t* buffer = static_cast<uint8_t*>(av_malloc(32768));
    if (!buffer) { fail(env, "allocate input", AVERROR(ENOMEM)); return 0; }
    d->io = avio_alloc_context(buffer, 32768, 0, d.get(), readInput, nullptr, seekInput);
    if (!d->io) { av_free(buffer); fail(env, "allocate IO", AVERROR(ENOMEM)); return 0; }
    d->format = avformat_alloc_context();
    if (!d->format) { fail(env, "allocate format", AVERROR(ENOMEM)); return 0; }
    d->format->pb = d->io;
    d->format->flags |= AVFMT_FLAG_CUSTOM_IO;
    int result = avformat_open_input(&d->format, nullptr, nullptr, nullptr);
    if (result < 0) { fail(env, "open audio", result); return 0; }
    result = avformat_find_stream_info(d->format, nullptr);
    if (result < 0) { fail(env, "read audio format", result); return 0; }
    d->stream = av_find_best_stream(d->format, AVMEDIA_TYPE_AUDIO, -1, -1, nullptr, 0);
    if (d->stream < 0) { fail(env, "find audio track", d->stream); return 0; }
    auto* parameters = d->format->streams[d->stream]->codecpar;
    if (parameters->codec_id != AV_CODEC_ID_ALAC && parameters->codec_id != AV_CODEC_ID_APE) {
        env->ThrowNew(env->FindClass("java/lang/UnsupportedOperationException"), "FFmpeg fallback supports ALAC and APE");
        return 0;
    }
    d->codec = avcodec_alloc_context3(avcodec_find_decoder(parameters->codec_id));
    if (!d->codec) { fail(env, "allocate decoder", AVERROR(ENOMEM)); return 0; }
    result = avcodec_parameters_to_context(d->codec, parameters);
    if (result >= 0) result = avcodec_open2(d->codec, d->codec->codec, nullptr);
    if (result < 0) { fail(env, "open audio decoder", result); return 0; }
    d->rate = d->codec->sample_rate;
    d->channels = d->codec->ch_layout.nb_channels;
    if (d->rate <= 0 || d->channels <= 0) { fail(env, "invalid audio format", AVERROR_INVALIDDATA); return 0; }
    result = swr_alloc_set_opts2(&d->resampler, &d->codec->ch_layout, AV_SAMPLE_FMT_FLT, d->rate,
                               &d->codec->ch_layout, d->codec->sample_fmt, d->rate, 0, nullptr);
    if (result >= 0) result = swr_init(d->resampler);
    if (result < 0) { fail(env, "initialize PCM conversion", result); return 0; }
    d->packet = av_packet_alloc();
    d->frame = av_frame_alloc();
    if (!d->packet || !d->frame) { fail(env, "allocate decode buffers", AVERROR(ENOMEM)); return 0; }
    return reinterpret_cast<jlong>(d.release());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_lonx_lyrico_platform_audio_FfmpegAudioDecoder_mimeTypeNative(JNIEnv* env, jobject, jlong pointer) {
    return env->NewStringUTF(get(pointer)->codec->codec_id == AV_CODEC_ID_APE ? "audio/x-ape" : "audio/alac");
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_lonx_lyrico_platform_audio_FfmpegAudioDecoder_formatNative(JNIEnv* env, jobject, jlong pointer) {
    auto* d = get(pointer);
    jint values[] = { d->rate, d->channels };
    jintArray result = env->NewIntArray(2);
    if (result) env->SetIntArrayRegion(result, 0, 2, values);
    return result;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_lonx_lyrico_platform_audio_FfmpegAudioDecoder_durationNative(JNIEnv*, jobject, jlong pointer) {
    auto* d = get(pointer);
    auto* stream = d->format->streams[d->stream];
    if (stream->duration != AV_NOPTS_VALUE)
        return av_rescale_q(stream->duration, stream->time_base, AV_TIME_BASE_Q);
    return d->format->duration == AV_NOPTS_VALUE ? 0 : d->format->duration;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_lonx_lyrico_platform_audio_FfmpegAudioDecoder_readNative(JNIEnv* env, jobject, jlong pointer) {
    auto* d = get(pointer);
    while (true) {
        int result = avcodec_receive_frame(d->codec, d->frame);
        if (result == AVERROR_EOF) return nullptr;
        if (result == 0) {
            if (d->frame->sample_rate != d->rate || av_channel_layout_compare(&d->frame->ch_layout, &d->codec->ch_layout)) {
                fail(env, "audio format changed", AVERROR_INVALIDDATA); return nullptr;
            }
            d->pcm.resize(static_cast<size_t>(d->frame->nb_samples) * d->channels);
            uint8_t* output = reinterpret_cast<uint8_t*>(d->pcm.data());
            result = swr_convert(d->resampler, &output, d->frame->nb_samples,
                                 const_cast<const uint8_t**>(d->frame->extended_data), d->frame->nb_samples);
            av_frame_unref(d->frame);
            if (result < 0) { fail(env, "convert PCM", result); return nullptr; }
            if (result == 0) continue;
            return env->NewDirectByteBuffer(d->pcm.data(), static_cast<jlong>(result) * d->channels * sizeof(float));
        }
        if (result != AVERROR(EAGAIN) || d->draining) {
            fail(env, "decode audio", result); return nullptr;
        }
        do {
            av_packet_unref(d->packet);
            result = av_read_frame(d->format, d->packet);
        } while (result >= 0 && d->packet->stream_index != d->stream);
        if (result == AVERROR_EOF) {
            d->draining = true;
            result = avcodec_send_packet(d->codec, nullptr);
        } else if (result >= 0) {
            if (d->packet->flags & AV_PKT_FLAG_CORRUPT) { fail(env, "corrupt audio packet", AVERROR_INVALIDDATA); return nullptr; }
            result = avcodec_send_packet(d->codec, d->packet);
        }
        av_packet_unref(d->packet);
        if (result < 0) { fail(env, "read audio packet", result); return nullptr; }
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_lonx_lyrico_platform_audio_FfmpegAudioDecoder_closeNative(JNIEnv*, jobject, jlong pointer) { delete get(pointer); }
