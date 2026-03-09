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
#include <taglib/id3v2tag.h>
#include <taglib/attachedpictureframe.h>
#include <taglib/flacpicture.h>
#include <taglib/xiphcomment.h>

const size_t IO_BUFFER_SIZE = 256 * 1024;

class FdIOStream : public TagLib::IOStream {
public:
    explicit FdIOStream(int fd)
            : m_fd(dup(fd)), m_position(0), m_size(0) {
        if (m_fd == -1) throw std::runtime_error("dup failed");
        struct stat st{};
        if (fstat(m_fd, &st) == 0) m_size = st.st_size;
    }

    ~FdIOStream() override {
        if (m_fd != -1) close(m_fd);
    }

    [[nodiscard]] TagLib::FileName name() const override { return "fd_stream"; }

    TagLib::ByteVector readBlock(size_t length) override {
        TagLib::ByteVector data((unsigned int)length);
        ssize_t bytes = pread(m_fd, data.data(), length, m_position);
        if (bytes <= 0) return {};
        m_position += bytes;
        data.resize(bytes);
        return data;
    }

    void writeBlock(const TagLib::ByteVector &data) override {
        ssize_t written = pwrite(m_fd, data.data(), data.size(), m_position);
        if (written > 0) m_position += written;
        if (m_position > m_size) m_size = m_position;
    }

    // 分块移动数据，防止大尺寸 WAV 文件导致 OOM
    void insert(const TagLib::ByteVector &data, TagLib::offset_t start, size_t replace) override {
        TagLib::offset_t tailStart = start + replace;
        TagLib::offset_t tailSize = m_size - tailStart;
        TagLib::offset_t dataSize = data.size();

        if (dataSize > replace) {
            // 需要扩容文件：从后往前分块移动，防止覆盖未读取的数据
            TagLib::offset_t shiftAmount = dataSize - replace;
            TagLib::offset_t readPos = tailStart + tailSize;
            std::vector<char> buffer(IO_BUFFER_SIZE);

            TagLib::offset_t bytesToMove = tailSize;
            while (bytesToMove > 0) {
                size_t chunk = std::min((size_t)bytesToMove, IO_BUFFER_SIZE);
                readPos -= chunk;
                pread(m_fd, buffer.data(), chunk, readPos);
                pwrite(m_fd, buffer.data(), chunk, readPos + shiftAmount);
                bytesToMove -= chunk;
            }
        } else if (dataSize < replace) {
            // 需要缩减文件：从前往后分块移动
            TagLib::offset_t shiftAmount = replace - dataSize;
            TagLib::offset_t readPos = tailStart;
            std::vector<char> buffer(IO_BUFFER_SIZE);

            TagLib::offset_t bytesToMove = tailSize;
            while (bytesToMove > 0) {
                size_t chunk = std::min((size_t)bytesToMove, IO_BUFFER_SIZE);
                pread(m_fd, buffer.data(), chunk, readPos);
                pwrite(m_fd, buffer.data(), chunk, readPos - shiftAmount);
                readPos += chunk;
                bytesToMove -= chunk;
            }
        }

        // 写入新数据
        if (dataSize > 0) {
            pwrite(m_fd, data.data(), dataSize, start);
        }

        m_size = start + dataSize + tailSize;
        ftruncate(m_fd, m_size);
    }

    // 分块移动数据，防止 OOM
    void removeBlock(TagLib::offset_t start, size_t length) override {
        TagLib::offset_t tailStart = start + length;
        TagLib::offset_t tailSize = m_size - tailStart;

        std::vector<char> buffer(IO_BUFFER_SIZE);
        TagLib::offset_t readPos = tailStart;
        TagLib::offset_t writePos = start;

        TagLib::offset_t bytesToMove = tailSize;
        while (bytesToMove > 0) {
            size_t chunk = std::min((size_t)bytesToMove, IO_BUFFER_SIZE);
            pread(m_fd, buffer.data(), chunk, readPos);
            pwrite(m_fd, buffer.data(), chunk, writePos);
            readPos += chunk;
            writePos += chunk;
            bytesToMove -= chunk;
        }

        m_size -= length;
        ftruncate(m_fd, m_size);
    }

    [[nodiscard]] bool readOnly() const override { return false; }
    [[nodiscard]] bool isOpen() const override { return m_fd != -1; }

    void seek(TagLib::offset_t offset, TagLib::IOStream::Position p) override {
        switch (p) {
            case Beginning: m_position = offset; break;
            case Current: m_position += offset; break;
            case End: m_position = m_size + offset; break;
        }
        if (m_position < 0) m_position = 0;
        if (m_position > m_size) m_position = m_size;
    }

    [[nodiscard]] TagLib::offset_t tell() const override { return m_position; }
    TagLib::offset_t length() override { return m_size; }
    void truncate(TagLib::offset_t length) override {
        ftruncate(m_fd, length);
        m_size = length;
        if (m_position > m_size) m_position = m_size;
    }

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
Java_com_lonx_audiotag_internal_TagLibJNI_readNative(
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
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_lonx_audiotag_internal_TagLibJNI_writeNative(
        JNIEnv *env,
        jobject /* this */,
        jint fd,
        jobject jMap) {

    FdIOStream *fdStream = nullptr;
    TagLib::File *file = nullptr;

    try {
        fdStream = new FdIOStream(fd);
        // 写模式，不需要读取深入的 AudioProperties
        file = createFileFromContent(fdStream, false, TagLib::AudioProperties::Average);

        if (!file || !file->isValid()) {
            if (file) delete file;
            if (fdStream) delete fdStream;
            return JNI_FALSE;
        }

        TagLib::PropertyMap properties;

        jclass mapClass = env->GetObjectClass(jMap);
        jmethodID entrySetMethod = env->GetMethodID(mapClass, "entrySet", "()Ljava/util/Set;");
        jobject entrySet = env->CallObjectMethod(jMap, entrySetMethod);

        jclass setClass = env->GetObjectClass(entrySet);
        jmethodID iteratorMethod = env->GetMethodID(setClass, "iterator", "()Ljava/util/Iterator;");
        jobject iterator = env->CallObjectMethod(entrySet, iteratorMethod);

        jclass iteratorClass = env->GetObjectClass(iterator);
        jmethodID hasNextMethod = env->GetMethodID(iteratorClass, "hasNext", "()Z");
        jmethodID nextMethod = env->GetMethodID(iteratorClass, "next", "()Ljava/lang/Object;");

        jclass entryClass = env->FindClass("java/util/Map$Entry");
        jmethodID getKeyMethod = env->GetMethodID(entryClass, "getKey", "()Ljava/lang/Object;");
        jmethodID getValueMethod = env->GetMethodID(entryClass, "getValue", "()Ljava/lang/Object;");

        jclass listClass = env->FindClass("java/util/List");
        jmethodID sizeMethod = env->GetMethodID(listClass, "size", "()I");
        jmethodID getMethod = env->GetMethodID(listClass, "get", "(I)Ljava/lang/Object;");

        while (env->CallBooleanMethod(iterator, hasNextMethod)) {
            jobject entry = env->CallObjectMethod(iterator, nextMethod);
            jstring jKey = (jstring) env->CallObjectMethod(entry, getKeyMethod);
            jobject jList = env->CallObjectMethod(entry, getValueMethod);

            if (!env->IsInstanceOf(jList, listClass)) {
                env->DeleteLocalRef(jKey);
                env->DeleteLocalRef(jList);
                env->DeleteLocalRef(entry);
                continue;
            }

            const char *keyStr = env->GetStringUTFChars(jKey, nullptr);
            TagLib::String key(keyStr, TagLib::String::UTF8);
            env->ReleaseStringUTFChars(jKey, keyStr);

            TagLib::StringList values;
            int size = env->CallIntMethod(jList, sizeMethod);

            for (int i = 0; i < size; i++) {
                jstring jVal = (jstring) env->CallObjectMethod(jList, getMethod, i);
                if (jVal != nullptr) {
                    const char *valStr = env->GetStringUTFChars(jVal, nullptr);
                    values.append(TagLib::String(valStr, TagLib::String::UTF8));
                    env->ReleaseStringUTFChars(jVal, valStr);
                    env->DeleteLocalRef(jVal);
                }
            }

            properties.insert(key, values);

            env->DeleteLocalRef(jKey);
            env->DeleteLocalRef(jList);
            env->DeleteLocalRef(entry);
        }


        file->setProperties(properties);


        bool ok = file->save();

        delete file;
        delete fdStream;

        return ok ? JNI_TRUE : JNI_FALSE;

    } catch (...) {
        if (file) delete file;
        if (fdStream) delete fdStream;
        return JNI_FALSE;
    }
}

TagLib::String detectMimeType(const TagLib::ByteVector& data) {
    if (data.size() >= 3 && (unsigned char)data[0] == 0xFF && (unsigned char)data[1] == 0xD8 && (unsigned char)data[2] == 0xFF) {
        return "image/jpeg";
    } else if (data.size() >= 4 && (unsigned char)data[0] == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
        return "image/png";
    }
    return "image/jpeg"; // 默认 Fallback
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_lonx_audiotag_internal_TagLibJNI_writePicturesNative(
        JNIEnv *env,
        jobject /* this */,
        jint fd,
        jobjectArray jByteArrays) {

    // 如果没有传入图片，直接返回 true
    int arrayLen = env->GetArrayLength(jByteArrays);
    if (arrayLen == 0) return JNI_TRUE;

    // 提取第一张图片的字节数据 (绝大多数播放器只认 FrontCover)
    auto jPicArray = (jbyteArray) env->GetObjectArrayElement(jByteArrays, 0);
    if (jPicArray == nullptr) return JNI_FALSE;

    jsize picLength = env->GetArrayLength(jPicArray);
    jbyte *picBytes = env->GetByteArrayElements(jPicArray, nullptr);

    // 构造 TagLib 需要的 ByteVector
    TagLib::ByteVector picData((const char *)picBytes, picLength);
    TagLib::String mimeType = detectMimeType(picData);

    // 释放 JNI 字节数组引用 (JNI_ABORT 表示我们没有修改数组内容)
    env->ReleaseByteArrayElements(jPicArray, picBytes, JNI_ABORT);
    env->DeleteLocalRef(jPicArray);

    FdIOStream *fdStream = nullptr;
    TagLib::File *file = nullptr;

    try {
        fdStream = new FdIOStream(fd);
        // 写模式，不需要加载 AudioProperties
        file = createFileFromContent(fdStream, false, TagLib::AudioProperties::Average);

        if (!file || !file->isValid()) {
            if (file) delete file;
            if (fdStream) delete fdStream;
            return JNI_FALSE;
        }

        bool success = false;


        // MP3 / MPEG (使用 ID3v2)
        if (auto *mpegFile = dynamic_cast<TagLib::MPEG::File*>(file)) {
            auto *tag = mpegFile->ID3v2Tag(true);
            tag->removeFrames("APIC"); // 移除旧封面

            auto *frame = new TagLib::ID3v2::AttachedPictureFrame();
            frame->setPicture(picData);
            frame->setMimeType(mimeType);
            frame->setType(TagLib::ID3v2::AttachedPictureFrame::FrontCover);
            tag->addFrame(frame);
            success = true;
        }
            // WAV (使用 ID3v2)
        else if (auto *wavFile = dynamic_cast<TagLib::RIFF::WAV::File*>(file)) {
            auto *tag = wavFile->ID3v2Tag(); // 无参调用，保证内存中有 ID3v2
            tag->removeFrames("APIC");

            auto *frame = new TagLib::ID3v2::AttachedPictureFrame();
            frame->setPicture(picData);
            frame->setMimeType(mimeType);
            frame->setType(TagLib::ID3v2::AttachedPictureFrame::FrontCover);
            tag->addFrame(frame);
            success = true;
        }
            // FLAC
        else if (auto *flacFile = dynamic_cast<TagLib::FLAC::File*>(file)) {
            flacFile->removePictures();

            auto *pic = new TagLib::FLAC::Picture();
            pic->setData(picData);
            pic->setMimeType(mimeType);
            pic->setType(TagLib::FLAC::Picture::FrontCover);
            flacFile->addPicture(pic);
            success = true;
        }
            // MP4 / M4A / ALAC
        else if (auto *mp4File = dynamic_cast<TagLib::MP4::File*>(file)) {
            if (auto *mp4Tag = mp4File->tag()) {
                TagLib::MP4::CoverArtList coverArtList;
                TagLib::MP4::CoverArt::Format format = (mimeType == "image/png") ?
                                                       TagLib::MP4::CoverArt::PNG :
                                                       TagLib::MP4::CoverArt::JPEG;
                coverArtList.append(TagLib::MP4::CoverArt(format, picData));

                TagLib::MP4::Item item(coverArtList);

                mp4Tag->setItem("covr", item);

                success = true;
            }
        }
            // Ogg Vorbis
        else if (auto *vorbisFile = dynamic_cast<TagLib::Ogg::Vorbis::File*>(file)) {
            if (auto *tag = vorbisFile->tag()) {
                // Vorbis 规定图片必须作为 Base64 编码的 FLAC Picture 块存入 METADATA_BLOCK_PICTURE
                TagLib::FLAC::Picture pic;
                pic.setData(picData);
                pic.setMimeType(mimeType);
                pic.setType(TagLib::FLAC::Picture::FrontCover);

                TagLib::ByteVector base64Data = pic.render().toBase64();
                tag->addField("METADATA_BLOCK_PICTURE", TagLib::String(base64Data, TagLib::String::UTF8));
                success = true;
            }
        }
            // Ogg Opus
        else if (auto *opusFile = dynamic_cast<TagLib::Ogg::Opus::File*>(file)) {
            if (auto *tag = opusFile->tag()) {
                TagLib::FLAC::Picture pic;
                pic.setData(picData);
                pic.setMimeType(mimeType);
                pic.setType(TagLib::FLAC::Picture::FrontCover);

                TagLib::ByteVector base64Data = pic.render().toBase64();
                tag->addField("METADATA_BLOCK_PICTURE", TagLib::String(base64Data, TagLib::String::UTF8));
                success = true;
            }
        }


        if (success) {
            success = file->save();
        }

        delete file;
        delete fdStream;

        return success ? JNI_TRUE : JNI_FALSE;

    } catch (...) {
        if (file) delete file;
        if (fdStream) delete fdStream;
        return JNI_FALSE;
    }
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_lonx_audiotag_internal_TagLibJNI_readPictureNative(
        JNIEnv *env,
        jobject /* this */,
        jint fd) {

    FdIOStream *fdStream = nullptr;
    TagLib::File *file = nullptr;

    try {
        fdStream = new FdIOStream(fd);
        // 只读模式，传入 true 解析完整标签
        file = createFileFromContent(fdStream, true, TagLib::AudioProperties::Average);

        if (!file || !file->isValid()) {
            if (file) delete file;
            if (fdStream) delete fdStream;
            return nullptr;
        }

        TagLib::ByteVector picData;

        if (auto *mpegFile = dynamic_cast<TagLib::MPEG::File*>(file)) {
            if (auto *id3v2 = mpegFile->ID3v2Tag()) {
                auto frames = id3v2->frameListMap()["APIC"];
                if (!frames.isEmpty()) {
                    auto *apic = dynamic_cast<TagLib::ID3v2::AttachedPictureFrame*>(frames.front());
                    if (apic) picData = apic->picture();
                }
            }
        }
        else if (auto *wavFile = dynamic_cast<TagLib::RIFF::WAV::File*>(file)) {
            if (wavFile->hasID3v2Tag()) {
                if (auto *id3v2 = wavFile->ID3v2Tag()) {
                    auto frames = id3v2->frameListMap()["APIC"];
                    if (!frames.isEmpty()) {
                        auto *apic = dynamic_cast<TagLib::ID3v2::AttachedPictureFrame*>(frames.front());
                        if (apic) picData = apic->picture();
                    }
                }
            }
        }
        else if (auto *flacFile = dynamic_cast<TagLib::FLAC::File*>(file)) {
            auto pics = flacFile->pictureList();
            if (!pics.isEmpty()) {
                picData = pics.front()->data();
            }
        }
        else if (auto *mp4File = dynamic_cast<TagLib::MP4::File*>(file)) {
            if (auto *mp4Tag = mp4File->tag()) {
                if (mp4Tag->contains("covr")) {
                    auto coverList = mp4Tag->item("covr").toCoverArtList();
                    if (!coverList.isEmpty()) {
                        picData = coverList.front().data();
                    }
                }
            }
        }
        else if (auto *vorbisFile = dynamic_cast<TagLib::Ogg::Vorbis::File*>(file)) {
            if (auto *tag = vorbisFile->tag()) {
                if (tag->contains("METADATA_BLOCK_PICTURE")) {
                    TagLib::String base64Str = tag->fieldListMap()["METADATA_BLOCK_PICTURE"].front();
                    TagLib::ByteVector decoded = TagLib::ByteVector::fromBase64(base64Str.data(TagLib::String::UTF8));
                    TagLib::FLAC::Picture flacPic(decoded);
                    picData = flacPic.data();
                }
            }
        }
        else if (auto *opusFile = dynamic_cast<TagLib::Ogg::Opus::File*>(file)) {
            if (auto *tag = opusFile->tag()) {
                if (tag->contains("METADATA_BLOCK_PICTURE")) {
                    TagLib::String base64Str = tag->fieldListMap()["METADATA_BLOCK_PICTURE"].front();
                    TagLib::ByteVector decoded = TagLib::ByteVector::fromBase64(base64Str.data(TagLib::String::UTF8));
                    TagLib::FLAC::Picture flacPic(decoded);
                    picData = flacPic.data();
                }
            }
        }

        // 释放文件相关内存
        delete file;
        delete fdStream;

        // 如果没有提取到任何图片数据，返回 null
        if (picData.isEmpty()) {
            return nullptr;
        }


        jbyteArray result = env->NewByteArray(picData.size());
        if (result != nullptr) {
            env->SetByteArrayRegion(result, 0, picData.size(), (const jbyte *)picData.data());
        }
        return result;

    } catch (...) {
        if (file) delete file;
        if (fdStream) delete fdStream;
        return nullptr;
    }
}
