# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name. The project name can be accessed via ${ PROJECT_NAME},
# Since this is the top level CMakeLists.txt, the project name is also accessible
# with ${CMAKE_PROJECT_NAME} (both CMake variables are in-sync within the top level
# build script scope).
project("tagJNI") # becomes "libtagJNI.so"

message(STATUS "TagLib lib path: ${TAGLIB_FILE_PATH}")
message(STATUS "TagLib include path: ${TAGLIB_INCLUDE_DIR}")

# -----------------------------
# 导入静态库
# -----------------------------
add_library(taglib STATIC IMPORTED)
set_target_properties(taglib PROPERTIES
    IMPORTED_LOCATION "${TAGLIB_FILE_PATH}"
    INTERFACE_INCLUDE_DIRECTORIES "${TAGLIB_INCLUDE_DIR}"
)

# -----------------------------
# 创建 JNI Shared Library
# -----------------------------
add_library(${PROJECT_NAME} SHARED
    taglib_jni.cpp
    JTagMap.cpp
    JMetadataBuilder.cpp
    JClassRef.cpp
    JObjectRef.cpp
    JStringRef.cpp
    JByteArrayRef.cpp
)

target_link_options(${PROJECT_NAME} PRIVATE
    "-Wl,--exclude-libs,ALL,-z,max-page-size=16384"
)

# -----------------------------
# 链接库
# -----------------------------
target_link_libraries(${PROJECT_NAME}
    PRIVATE android
    PRIVATE log
    PRIVATE taglib
)