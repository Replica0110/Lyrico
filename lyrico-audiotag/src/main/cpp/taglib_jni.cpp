/*
 * Copyright (c) 2024 Auxio Project
 * taglib_jni.cpp is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#include <jni.h>
#include <string>
#include <taglib/unsynchronizedlyricsframe.h>
#include <unistd.h>
#include <sys/stat.h>
#include "JInputStream.h"
#include "JClassRef.h"
#include "JMetadataBuilder.h"
#include "JObjectRef.h"
#include "util.h"

#include "taglib/fileref.h"
#include "taglib/flacfile.h"
#include "taglib/mp4file.h"
#include "taglib/mp4properties.h"
#include "taglib/mpegfile.h"
#include "taglib/opusfile.h"
#include "taglib/vorbisfile.h"
#include "taglib/wavfile.h"
#include "taglib/tpropertymap.h"
class FdIOStream : public TagLib::IOStream {
public:
    explicit FdIOStream(int fd)
            : m_fd(dup(fd)), m_position(0), m_size(0) {

        if (m_fd == -1)
            throw std::runtime_error("dup failed");

        struct stat st{};
        if (fstat(m_fd, &st) == 0)
            m_size = st.st_size;
    }

    ~FdIOStream() override {
        if (m_fd != -1)
            close(m_fd);
    }

    [[nodiscard]] TagLib::FileName name() const override {
        return "fd_stream";
    }

    TagLib::ByteVector readBlock(size_t length) override {

        TagLib::ByteVector data((unsigned int)length);

        ssize_t bytes = pread(m_fd, data.data(), length, m_position);

        if (bytes <= 0)
            return TagLib::ByteVector();

        m_position += bytes;
        data.resize(bytes);

        return data;
    }

    void writeBlock(const TagLib::ByteVector &) override {}

    void insert(const TagLib::ByteVector &,
                TagLib::offset_t,
                size_t) override {}

    void removeBlock(TagLib::offset_t,
                     size_t) override {}

    bool readOnly() const override {
        return true;
    }

    bool isOpen() const override {
        return m_fd != -1;
    }

    void seek(TagLib::offset_t offset,
              TagLib::IOStream::Position p) override {

        switch (p) {
            case Beginning:
                m_position = offset;
                break;
            case Current:
                m_position += offset;
                break;
            case End:
                m_position = m_size + offset;
                break;
        }

        if (m_position < 0)
            m_position = 0;

        if (m_position > m_size)
            m_position = m_size;
    }

    TagLib::offset_t tell() const override {
        return m_position;
    }

    TagLib::offset_t length() override {
        return m_size;
    }

    void truncate(TagLib::offset_t) override {}

private:
    int m_fd;
    TagLib::offset_t m_position;
    TagLib::offset_t m_size;
};
bool parseMpeg(const std::string &name, TagLib::MPEG::File *mpegFile,
               JMetadataBuilder &jBuilder) {

    auto id3v1Tag = mpegFile->ID3v1Tag();
    if (id3v1Tag != nullptr) {
        try {
            jBuilder.setId3v1(*id3v1Tag);
        } catch (std::exception &e) {
            LOGE("Unable to parse ID3v1 tag in %s: %s", name.c_str(), e.what());
        }
    }

    auto id3v2Tag = mpegFile->ID3v2Tag();
    if (id3v2Tag != nullptr) {
        try {
            jBuilder.setId3v2(*id3v2Tag);
        } catch (std::exception &e) {
            LOGE("Unable to parse ID3v2 tag in %s: %s", name.c_str(), e.what());
        }
    }

    return true;
}

bool parseMp4(const std::string &name, TagLib::MP4::File *mp4File,
              JMetadataBuilder &jBuilder) {

    auto tag = mp4File->tag();
    if (tag != nullptr) {
        try {
            jBuilder.setMp4(*tag);
        } catch (std::exception &e) {
            LOGE("Unable to parse MP4 tag in %s: %s", name.c_str(), e.what());
        }
    }

    return true;
}

bool parseFlac(const std::string &name, TagLib::FLAC::File *flacFile,
               JMetadataBuilder &jBuilder) {

    auto id3v1Tag = flacFile->ID3v1Tag();
    if (id3v1Tag != nullptr) {
        try {
            jBuilder.setId3v1(*id3v1Tag);
        } catch (std::exception &e) {
            LOGE("Unable to parse ID3v1 tag in %s: %s", name.c_str(), e.what());
        }
    }

    auto id3v2Tag = flacFile->ID3v2Tag();
    if (id3v2Tag != nullptr) {
        try {
            jBuilder.setId3v2(*id3v2Tag);
        } catch (std::exception &e) {
            LOGE("Unable to parse ID3v2 tag in %s: %s", name.c_str(), e.what());
        }
    }

    auto xiphComment = flacFile->xiphComment();
    if (xiphComment != nullptr) {
        try {
            jBuilder.setXiph(*xiphComment);
        } catch (std::exception &e) {
            LOGE("Unable to parse Xiph comment in %s: %s", name.c_str(), e.what());
        }
    }

    auto pics = flacFile->pictureList();
    jBuilder.setFlacPictures(pics);

    return true;
}

bool parseOpus(const std::string &name, TagLib::Ogg::Opus::File *opusFile,
               JMetadataBuilder &jBuilder) {

    auto tag = opusFile->tag();
    if (tag != nullptr) {
        try {
            jBuilder.setXiph(*tag);
        } catch (std::exception &e) {
            LOGE("Unable to parse Xiph comment in %s: %s", name.c_str(), e.what());
        }
    }

    return true;
}

bool parseVorbis(const std::string &name, TagLib::Ogg::Vorbis::File *vorbisFile,
                 JMetadataBuilder &jBuilder) {

    auto tag = vorbisFile->tag();
    if (tag != nullptr) {
        try {
            jBuilder.setXiph(*tag);
        } catch (std::exception &e) {
            LOGE("Unable to parse Xiph comment %s: %s", name.c_str(), e.what());
        }
    }

    return true;
}

bool parseWav(const std::string &name, TagLib::RIFF::WAV::File *wavFile,
              JMetadataBuilder &jBuilder) {

    auto tag = wavFile->ID3v2Tag();
    if (tag != nullptr) {
        try {
            jBuilder.setId3v2(*tag);
        } catch (std::exception &e) {
            LOGE("Unable to parse ID3v2 tag in %s: %s", name.c_str(), e.what());
        }
    }

    return true;
}

TagLib::File* createFileFromContent(TagLib::IOStream *stream,
                                    bool readAudioProperties,
                                    TagLib::AudioProperties::ReadStyle audioPropertiesStyle) {

    TagLib::File *file = nullptr;

    if (TagLib::MPEG::File::isSupported(stream))
        file = new TagLib::MPEG::File(stream, readAudioProperties, audioPropertiesStyle);
    else if (TagLib::Ogg::Vorbis::File::isSupported(stream))
        file = new TagLib::Ogg::Vorbis::File(stream, readAudioProperties, audioPropertiesStyle);
    else if (TagLib::FLAC::File::isSupported(stream))
        file = new TagLib::FLAC::File(stream, readAudioProperties, audioPropertiesStyle);
    else if (TagLib::Ogg::Opus::File::isSupported(stream))
        file = new TagLib::Ogg::Opus::File(stream, readAudioProperties, audioPropertiesStyle);
    else if (TagLib::MP4::File::isSupported(stream))
        file = new TagLib::MP4::File(stream, readAudioProperties, audioPropertiesStyle);
    else if (TagLib::RIFF::WAV::File::isSupported(stream))
        file = new TagLib::RIFF::WAV::File(stream, readAudioProperties, audioPropertiesStyle);

    if (file) {
        if (file->isValid())
            return file;
        delete file;
    }

    return nullptr;
}

bool dispatchAndParse(const std::string &name, TagLib::File *file,
                      JMetadataBuilder &jBuilder) {

    if (auto *mpegFile = dynamic_cast<TagLib::MPEG::File*>(file)) {
        jBuilder.setMimeType("audio/mpeg");
        return parseMpeg(name, mpegFile, jBuilder);
    }

    if (auto *flacFile = dynamic_cast<TagLib::FLAC::File*>(file)) {
        jBuilder.setMimeType("audio/flac");
        return parseFlac(name, flacFile, jBuilder);
    }

    if (auto *opusFile = dynamic_cast<TagLib::Ogg::Opus::File*>(file)) {
        jBuilder.setMimeType("audio/opus");
        return parseOpus(name, opusFile, jBuilder);
    }

    if (auto *vorbisFile = dynamic_cast<TagLib::Ogg::Vorbis::File*>(file)) {
        jBuilder.setMimeType("audio/vorbis");
        return parseVorbis(name, vorbisFile, jBuilder);
    }

    if (auto *wavFile = dynamic_cast<TagLib::RIFF::WAV::File*>(file)) {
        jBuilder.setMimeType("audio/wav");
        return parseWav(name, wavFile, jBuilder);
    }

    if (auto *mp4File = dynamic_cast<TagLib::MP4::File*>(file)) {

        jBuilder.setMimeType("audio/mp4");

        if (auto *props =
                dynamic_cast<TagLib::MP4::Properties*>(mp4File->audioProperties())) {

            using Codec = TagLib::MP4::Properties::Codec;

            switch (props->codec()) {
                case Codec::AAC:
                    jBuilder.setMimeType("audio/aac");
                    break;
                case Codec::ALAC:
                    jBuilder.setMimeType("audio/alac");
                    break;
                default:
                    break;
            }
        }

        return parseMp4(name, mp4File, jBuilder);
    }

    return false;
}

static jobject metadataResultSuccess(JNIEnv *env, jobject metadata) {

    JClassRef jSuccessClass {
            env,
            "com/lonx/audiotag/internal/MetadataResult$Success"
    };

    jmethodID jInitMethod = jSuccessClass.method(
            "<init>",
            "(Lcom/lonx/audiotag/internal/Metadata;)V"
    );

    return env->NewObject(*jSuccessClass, jInitMethod, metadata);
}

static jobject metadataResultObject(JNIEnv *env, const char *classpath) {

    JClassRef jObjectClass { env, classpath };

    std::string signature = std::string("L") + classpath + ";";

    jfieldID jInstanceField = env->GetStaticFieldID(
            *jObjectClass,
            "INSTANCE",
            signature.c_str()
    );

    return env->GetStaticObjectField(*jObjectClass, jInstanceField);
}

static jobject metadataResultNoMetadata(JNIEnv *env) {
    return metadataResultObject(
            env,
            "com/lonx/audiotag/internal/MetadataResult$NoMetadata"
    );
}

static jobject metadataResultNotAudio(JNIEnv *env) {
    return metadataResultObject(
            env,
            "com/lonx/audiotag/internal/MetadataResult$NotAudio"
    );
}

static jobject metadataResultProviderFailed(JNIEnv *env) {
    return metadataResultObject(
            env,
            "com/lonx/audiotag/internal/MetadataResult$ProviderFailed"
    );
}
extern "C" JNIEXPORT jobject JNICALL
Java_com_lonx_audiotag_internal_TagLibJNI_openNative(
        JNIEnv *env,
        jobject /* this */,
        jint fd) {

    std::string name = "fd_stream";
    TagLib::File *fileToUse = nullptr;
    FdIOStream *fdStream = nullptr;

    try {

        // 创建文件描述符流
        fdStream = new FdIOStream(fd);

        // 根据文件内容创建 TagLib File
        fileToUse = createFileFromContent(
                fdStream,
                true,
                TagLib::AudioProperties::Average
        );

        if (fileToUse == nullptr) {

            LOGE("File format in %s is not supported.", name.c_str());

            delete fdStream;
            return metadataResultNotAudio(env);
        }

        if (!fileToUse->isValid()) {

            LOGE("File in %s is not valid.", name.c_str());

            delete fileToUse;
            delete fdStream;

            return metadataResultNotAudio(env);
        }

        if (fileToUse->audioProperties() == nullptr) {

            LOGE("No audio properties for %s", name.c_str());

            delete fileToUse;
            delete fdStream;

            return metadataResultNoMetadata(env);
        }

        JMetadataBuilder jBuilder{env};

        // 设置音频属性
        jBuilder.setProperties(fileToUse->audioProperties());

        // 分发到对应解析器
        if (!dispatchAndParse(name, fileToUse, jBuilder)) {

            LOGE("File format in %s is not supported by any parser.", name.c_str());

            delete fileToUse;
            delete fdStream;

            return metadataResultNotAudio(env);
        }

        JObjectRef jMetadata{env, jBuilder.build()};

        delete fileToUse;
        delete fdStream;

        return metadataResultSuccess(env, *jMetadata);

    } catch (std::exception &e) {

        LOGE("Unable to parse metadata in %s: %s", name.c_str(), e.what());

        if (fileToUse)
            delete fileToUse;

        if (fdStream)
            delete fdStream;

        return metadataResultProviderFailed(env);
    }
}