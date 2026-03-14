#ifndef TAGLIB_UTILS_H
#define TAGLIB_UTILS_H

#include <jni.h>
#include <unistd.h>
#include <android/log.h>
#include "fileref_ext.h"
#include "tpropertymap.h"


#define LOG_TAG "taglib_jni"
#define LOGE(...) \
  ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))
#define LOGD(...) \
  ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))
jclass stringClass = nullptr;

jclass hashMapClass = nullptr;
jmethodID hashMapInit = nullptr;
jmethodID hashMapPut = nullptr;

jclass metadataClass = nullptr;
jmethodID metadataConstructor = nullptr;

jclass audioPropertiesClass = nullptr;
jmethodID audioPropertiesConstructor = nullptr;

jclass pictureClass = nullptr;
jmethodID pictureConstructor = nullptr;
jmethodID pictureGetData = nullptr;
jmethodID pictureGetDescription = nullptr;
jmethodID pictureGetPictureType = nullptr;
jmethodID pictureGetMimeType = nullptr;

jclass entrySetClass = nullptr;
jmethodID iteratorMethod = nullptr;
jmethodID entrySetMethod = nullptr;

jclass iteratorClass = nullptr;
jmethodID hasNextMethod = nullptr;
jmethodID nextMethod = nullptr;

jclass mapEntryClass = nullptr;
jmethodID getKeyMethod = nullptr;
jmethodID getValueMethod = nullptr;

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass _stringClass = env->FindClass("java/lang/String");
    stringClass = reinterpret_cast<jclass>(env->NewGlobalRef(_stringClass));
    env->DeleteLocalRef(_stringClass);

    jclass _hashMapClass = env->FindClass("java/util/HashMap");
    hashMapClass = reinterpret_cast<jclass>(env->NewGlobalRef(_hashMapClass));
    env->DeleteLocalRef(_hashMapClass);
    hashMapInit = env->GetMethodID(hashMapClass, "<init>", "(I)V");
    hashMapPut = env->GetMethodID(hashMapClass, "put",
                                  "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

    jclass _metadataClass = env->FindClass("com/lonx/audiotag/model/Metadata");
    metadataClass = reinterpret_cast<jclass>(env->NewGlobalRef(_metadataClass));
    env->DeleteLocalRef(_metadataClass);
    metadataConstructor = env->GetMethodID(metadataClass, "<init>",
                                           "(Ljava/util/HashMap;[Lcom/lonx/audiotag/model/Picture;)V");

    jclass _audioPropertiesClass = env->FindClass("com/lonx/audiotag/model/AudioProperties");
    audioPropertiesClass = reinterpret_cast<jclass>(env->NewGlobalRef(_audioPropertiesClass));
    env->DeleteLocalRef(_audioPropertiesClass);
    audioPropertiesConstructor = env->GetMethodID(audioPropertiesClass, "<init>", "(IIII)V");

    jclass _pictureClass = env->FindClass("com/lonx/audiotag/model/Picture");
    pictureClass = reinterpret_cast<jclass>(env->NewGlobalRef(_pictureClass));
    env->DeleteLocalRef(_pictureClass);
    pictureConstructor = env->GetMethodID(pictureClass, "<init>",
                                          "([BLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    pictureGetData = env->GetMethodID(pictureClass, "getData", "()[B");
    pictureGetDescription = env->GetMethodID(pictureClass, "getDescription", "()Ljava/lang/String;");
    pictureGetPictureType = env->GetMethodID(pictureClass, "getPictureType", "()Ljava/lang/String;");
    pictureGetMimeType = env->GetMethodID(pictureClass, "getMimeType", "()Ljava/lang/String;");

    jclass _entrySetClass = env->FindClass("java/util/Set");
    entrySetClass = reinterpret_cast<jclass>(env->NewGlobalRef(_entrySetClass));
    env->DeleteLocalRef(_entrySetClass);
    iteratorMethod = env->GetMethodID(entrySetClass, "iterator", "()Ljava/util/Iterator;");
    entrySetMethod = env->GetMethodID(hashMapClass, "entrySet", "()Ljava/util/Set;");

    jclass _iteratorClass = env->FindClass("java/util/Iterator");
    iteratorClass = reinterpret_cast<jclass>(env->NewGlobalRef(_iteratorClass));
    env->DeleteLocalRef(_iteratorClass);
    hasNextMethod = env->GetMethodID(iteratorClass, "hasNext", "()Z");
    nextMethod = env->GetMethodID(iteratorClass, "next", "()Ljava/lang/Object;");

    jclass _mapEntryClass = env->FindClass("java/util/Map$Entry");
    mapEntryClass = reinterpret_cast<jclass>(env->NewGlobalRef(_mapEntryClass));
    env->DeleteLocalRef(_mapEntryClass);
    getKeyMethod = env->GetMethodID(mapEntryClass, "getKey", "()Ljava/lang/Object;");
    getValueMethod = env->GetMethodID(mapEntryClass, "getValue", "()Ljava/lang/Object;");

    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return;
    }

    env->DeleteGlobalRef(stringClass);
    env->DeleteGlobalRef(hashMapClass);
    env->DeleteGlobalRef(metadataClass);
    env->DeleteGlobalRef(audioPropertiesClass);
    env->DeleteGlobalRef(pictureClass);
    env->DeleteGlobalRef(entrySetClass);
    env->DeleteGlobalRef(iteratorClass);
    env->DeleteGlobalRef(mapEntryClass);

    stringClass = nullptr;
    hashMapClass = nullptr;
    hashMapInit = nullptr;
    hashMapPut = nullptr;
    metadataClass = nullptr;
    metadataConstructor = nullptr;
    audioPropertiesClass = nullptr;
    audioPropertiesConstructor = nullptr;
    pictureClass = nullptr;
    pictureConstructor = nullptr;
    pictureGetData = nullptr;
    pictureGetDescription = nullptr;
    pictureGetPictureType = nullptr;
    pictureGetMimeType = nullptr;
    entrySetClass = nullptr;
    iteratorMethod = nullptr;
    entrySetMethod = nullptr;
    iteratorClass = nullptr;
    hasNextMethod = nullptr;
    nextMethod = nullptr;
    mapEntryClass = nullptr;
    getKeyMethod = nullptr;
    getValueMethod = nullptr;
}

// Helper function to convert C++ StringList to JNI String array
jobjectArray StringListToJniStringArray(JNIEnv *env, const TagLib::StringList &stringList) {
    jobjectArray array = env->NewObjectArray(static_cast<jsize>(stringList.size()),
                                             stringClass, nullptr);
    int i = 0;
    for (const auto &str: stringList) {
        jstring jStr = env->NewStringUTF(str.toCString(true));
        env->SetObjectArrayElement(array, i, jStr);
        env->DeleteLocalRef(jStr);
        i++;
    }
    return array;
}

// Helper function to convert C++ PropertyMap to JNI HashMap
jobject PropertyMapToJniHashMap(JNIEnv *env, const TagLib::PropertyMap &propertyMap) {
    jobject hashMap = env->NewObject(hashMapClass, hashMapInit, static_cast<jint>(propertyMap.size()));

    for (const auto &property: propertyMap) {
        const char *key = property.first.toCString(true);
        const TagLib::StringList &valueList = property.second;

        jobjectArray valueArray = StringListToJniStringArray(env, valueList);

        jstring jKey = env->NewStringUTF(key);
        env->CallObjectMethod(hashMap, hashMapPut, jKey, valueArray);

        env->DeleteLocalRef(jKey);
        env->DeleteLocalRef(valueArray);
    }

    return hashMap;
}

// Helper function to convert JNI String array to C++ StringList
TagLib::StringList JniStringArrayToStringList(JNIEnv *env, jobjectArray stringArray) {
    TagLib::StringList stringList;

    const jsize arrayLength = env->GetArrayLength(stringArray);
    for (int i = 0; i < arrayLength; ++i) {
        auto jStr = reinterpret_cast<jstring>(env->GetObjectArrayElement(stringArray, i));
        const char *cStr = env->GetStringUTFChars(jStr, nullptr);
        stringList.append(TagLib::String(cStr, TagLib::String::UTF8));
        env->ReleaseStringUTFChars(jStr, cStr);
        env->DeleteLocalRef(jStr);
    }

    return stringList;
}

// Helper function to convert JNI HashMap to C++ PropertyMap
TagLib::PropertyMap JniHashMapToPropertyMap(JNIEnv *env, jobject hashMap) {
    TagLib::PropertyMap propertyMap;

    jobject entrySet = env->CallObjectMethod(hashMap, entrySetMethod);
    jobject iterator = env->CallObjectMethod(entrySet, iteratorMethod);

    while (env->CallBooleanMethod(iterator, hasNextMethod)) {
        jobject entry = env->CallObjectMethod(iterator, nextMethod);
        jobject key = env->CallObjectMethod(entry, getKeyMethod);
        jobject value = env->CallObjectMethod(entry, getValueMethod);

        const char *keyStr = env->GetStringUTFChars(reinterpret_cast<jstring>(key), nullptr);
        const StringList valueList = JniStringArrayToStringList(env, reinterpret_cast<jobjectArray>(value));

        propertyMap[TagLib::String(keyStr, TagLib::String::UTF8)] = valueList;

        env->ReleaseStringUTFChars(reinterpret_cast<jstring>(key), keyStr);
        env->DeleteLocalRef(entry);
        env->DeleteLocalRef(key);
        env->DeleteLocalRef(value);
    }

    return propertyMap;
}

// Helper function to convert C++ PictureList to JNI Picture array
jobjectArray PictureListToJniPictureArray(
        JNIEnv *env,
        const TagLib::List<TagLib::Map<TagLib::String, TagLib::Variant>> &pictureList
) {
    jobjectArray array = env->NewObjectArray(static_cast<jsize>(pictureList.size()),
                                             pictureClass, nullptr);
    int i = 0;
    for (const auto &picture: pictureList) {
        const ByteVector pictureData = picture["data"].toByteVector();
        if (pictureData.isEmpty()) {
            continue;
        }

        jbyteArray bytes = env->NewByteArray(static_cast<jint>(pictureData.size()));
        const String description = picture["description"].toString();
        jstring jDescription = env->NewStringUTF(description.toCString(true));
        const String pictureType = picture["pictureType"].toString();
        jstring jPictureType = env->NewStringUTF(pictureType.toCString(true));
        const String mimeType = picture["mimeType"].toString();
        jstring jMimeType = env->NewStringUTF(mimeType.toCString(true));

        env->SetByteArrayRegion(
                bytes,
                0,
                static_cast<jint>(pictureData.size()),
                reinterpret_cast<const jbyte *>(pictureData.data())
        );
        jobject pictureObject = env->NewObject(
                pictureClass, pictureConstructor,
                bytes, jDescription, jPictureType, jMimeType);
        env->DeleteLocalRef(bytes);
        env->DeleteLocalRef(jDescription);
        env->DeleteLocalRef(jPictureType);
        env->DeleteLocalRef(jMimeType);
        env->SetObjectArrayElement(array, i, pictureObject);
        env->DeleteLocalRef(pictureObject);
        i++;
    }
    return array;
}

// Helper function to convert JNI Picture array to C++ PictureList
TagLib::List<TagLib::Map<TagLib::String, TagLib::Variant>>
JniPictureArrayToPictureList(JNIEnv *env, jobjectArray pictures) {
    TagLib::List<TagLib::Map<TagLib::String, TagLib::Variant>> pictureList;

    const jsize pictureCount = env->GetArrayLength(pictures);
    for (int i = 0; i < pictureCount; i++) {
        jobject pictureObject = env->GetObjectArrayElement(pictures, i);
        const auto bytes = reinterpret_cast<jbyteArray>(env->CallObjectMethod(pictureObject,
                                                                              pictureGetData));
        const auto description = reinterpret_cast<jstring>(env->CallObjectMethod(pictureObject,
                                                                                 pictureGetDescription));
        const auto pictureType = reinterpret_cast<jstring>(env->CallObjectMethod(pictureObject,
                                                                                 pictureGetPictureType));
        const auto mimeType = reinterpret_cast<jstring>(env->CallObjectMethod(pictureObject,
                                                                              pictureGetMimeType));

        jbyte *pictureData = env->GetByteArrayElements(bytes, nullptr);
        const jsize pictureDataSize = env->GetArrayLength(bytes);
        TagLib::ByteVector pictureDataVector(
                reinterpret_cast<const char *>(pictureData),
                static_cast<uint>(pictureDataSize)
        );
        env->ReleaseByteArrayElements(bytes, pictureData, JNI_ABORT);

        TagLib::Map<TagLib::String, TagLib::Variant> picture;
        picture["data"] = pictureDataVector;
        picture["description"] = TagLib::String(env->GetStringUTFChars(description, nullptr));
        picture["pictureType"] = TagLib::String(env->GetStringUTFChars(pictureType, nullptr));
        picture["mimeType"] = TagLib::String(env->GetStringUTFChars(mimeType, nullptr));
        pictureList.append(picture);
    }

    return pictureList;
}

inline jobject getAudioProperties(JNIEnv *env, TagLib::File *f) {
    const TagLib::AudioProperties *audioProperties = f->audioProperties();
    if (audioProperties) {
        const jint duration = static_cast<jint>(audioProperties->lengthInMilliseconds());
        const jint bitrate = static_cast<jint>(audioProperties->bitrate());
        const jint sampleRate = static_cast<jint>(audioProperties->sampleRate());
        const jint channels = static_cast<jint>(audioProperties->channels());
        return env->NewObject(
                audioPropertiesClass, audioPropertiesConstructor,
                duration, bitrate, sampleRate, channels);
    }
    return env->NewObject(audioPropertiesClass, audioPropertiesConstructor, 0, 0, 0, 0);
}
inline jobject getPropertyMap(JNIEnv *env, TagLib::File *f) {
    return PropertyMapToJniHashMap(env, f->properties());
}

inline jobjectArray getPictures(JNIEnv *env, TagLib::File *f) {
    return PictureListToJniPictureArray(env, f->complexProperties("PICTURE"));
}

inline jobjectArray emptyPictureArray(JNIEnv *env) {
    return env->NewObjectArray(0, pictureClass, nullptr);
}



#endif //TAGLIB_UTILS_H
