#include "tfilestream.h"
#include "utils.h"

#include <mpegfile.h>
#include <vorbisfile.h>
#include <flacfile.h>
#include <opusfile.h>
#include <mp4file.h>
#include <wavfile.h>

#include <memory>
#include <stdexcept>


TagLib::File* createFileFromContent(TagLib::IOStream *stream,
                                    bool readAudioProperties,
                                    TagLib::AudioProperties::ReadStyle audioPropertiesStyle) {
    stream->seek(0);
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
        if (file->isValid()) {
            return file;
        }
        delete file;
    }

    return nullptr;
}


extern "C" {

JNIEXPORT jobject JNICALL
Java_com_lonx_audiotag_TagLib_getAudioProperties(
        JNIEnv *env, jclass, jint fd, jint read_style) {
    try {
        // fd readOnly = true
        auto stream = std::make_unique<TagLib::FileStream>(fd, true);
        const auto style = static_cast<TagLib::AudioProperties::ReadStyle>(read_style);

        std::unique_ptr<TagLib::File> file(createFileFromContent(stream.get(), true, style));

        if (!file) {
            return nullptr;
        }

        return getAudioProperties(env, file.get());
    } catch (const std::exception &e) {
        LOGE("Error reading audio properties: %s", e.what());
        return nullptr;
    }
}

JNIEXPORT jobject JNICALL
Java_com_lonx_audiotag_TagLib_getMetadata(
        JNIEnv *env, jclass, jint fd, jboolean read_pictures) {
    try {
        auto stream = std::make_unique<TagLib::FileStream>(fd, true);
        std::unique_ptr<TagLib::File> file(createFileFromContent(stream.get(), false, TagLib::AudioProperties::Average));

        if (!file) {
            return nullptr;
        }

        jobject propertiesMap = getPropertyMap(env, file.get());
        jobjectArray pictures;
        if (read_pictures) {
            pictures = getPictures(env, file.get());
        } else {
            pictures = emptyPictureArray(env);
        }

        return env->NewObject(metadataClass, metadataConstructor, propertiesMap, pictures);
    } catch (const std::exception &e) {
        LOGE("Error reading metadata: %s", e.what());
        return nullptr;
    }
}

JNIEXPORT jobjectArray JNICALL
Java_com_lonx_audiotag_TagLib_getMetadataPropertyValues(
        JNIEnv *env, jclass, jint fd, jstring property_name) {

    const char *propertyName = env->GetStringUTFChars(property_name, nullptr);
    if (propertyName == nullptr) return nullptr;

    try {
        auto stream = std::make_unique<TagLib::FileStream>(fd, true);
        std::unique_ptr<TagLib::File> file(createFileFromContent(stream.get(), false, TagLib::AudioProperties::Average));

        if (!file) {
            env->ReleaseStringUTFChars(property_name, propertyName);
            return nullptr;
        }

        const auto propertyMap = file->properties();
        auto it = propertyMap.find(TagLib::String(propertyName));

        if (it == propertyMap.end()) {
            env->ReleaseStringUTFChars(property_name, propertyName);
            return nullptr;
        }

        const auto valueList = it->second;
        jobjectArray result = env->NewObjectArray(static_cast<jsize>(valueList.size()), stringClass, nullptr);

        int i = 0;
        for (const auto &value: valueList) {
            jstring jValue = env->NewStringUTF(value.toCString(true));
            env->SetObjectArrayElement(result, i, jValue);
            env->DeleteLocalRef(jValue);
            i++;
        }

        env->ReleaseStringUTFChars(property_name, propertyName);
        return result;

    } catch (const std::exception &e) {
        LOGE("Error reading property values: %s", e.what());
        env->ReleaseStringUTFChars(property_name, propertyName);
        return nullptr;
    }
}

JNIEXPORT jobjectArray JNICALL
Java_com_lonx_audiotag_TagLib_getPictures(
        JNIEnv *env, jclass, jint fd) {
    try {
        auto stream = std::make_unique<TagLib::FileStream>(fd, true);
        std::unique_ptr<TagLib::File> file(createFileFromContent(stream.get(), false, TagLib::AudioProperties::Average));

        if (!file) {
            return emptyPictureArray(env);
        }

        return getPictures(env, file.get());
    } catch (const std::exception &e) {
        LOGE("Error reading pictures: %s", e.what());
        return emptyPictureArray(env);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_lonx_audiotag_TagLib_savePropertyMap(
        JNIEnv *env, jclass, jint fd, jobject property_map) {
    try {
        // 写操作，fd readOnly = false
        auto stream = std::make_unique<TagLib::FileStream>(fd, false);
        std::unique_ptr<TagLib::File> file(createFileFromContent(stream.get(), false, TagLib::AudioProperties::Average));

        if (!file) {
            return false;
        }

        TagLib::PropertyMap props = file->properties();

        const PropertyMap updates = JniHashMapToPropertyMap(env, property_map);

        for (const auto & update : updates) {
            const TagLib::String &key = update.first;
            const TagLib::StringList &values = update.second;

            if (values.isEmpty() || (values.size() == 1 && values.front().isEmpty())) {
                props.erase(key);
            } else {
                props.replace(key, values);
            }
        }

        file->setProperties(props);
        return file->save();

    } catch (const std::exception &e) {
        LOGE("Error saving property map: %s", e.what());
        return false;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_lonx_audiotag_TagLib_savePictures(
        JNIEnv *env, jclass, jint fd, jobjectArray pictures) {
    try {
        // 写操作，fd readOnly = false
        auto stream = std::make_unique<TagLib::FileStream>(fd, false);
        std::unique_ptr<TagLib::File> file(createFileFromContent(stream.get(), false, TagLib::AudioProperties::Average));

        if (!file) {
            return false;
        }

        auto pictureList = JniPictureArrayToPictureList(env, pictures);
        file->setComplexProperties("PICTURE", pictureList);

        return file->save();
    } catch (const std::exception &e) {
        LOGE("Error saving pictures: %s", e.what());
        return false;
    }
}

} // extern "C"