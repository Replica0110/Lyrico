cmake_minimum_required(VERSION 3.22.1)

project(taglib)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -fvisibility=hidden -flto -fdata-sections -ffunction-sections -fomit-frame-pointer")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -fvisibility=hidden -flto -fdata-sections -ffunction-sections -fomit-frame-pointer")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -flto -Wl,--exclude-libs,ALL -Wl,--gc-sections -s -Wl,--pack-dyn-relocs=android -Wl,--build-id=none")

set(CMAKE_C_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN YES)

set(VISIBILITY_HIDDEN ON)
set(BUILD_BINDINGS OFF)
set(BUILD_TESTING OFF)

add_subdirectory(taglib)

include_directories(
        taglib/taglib
        taglib/taglib/toolkit
        taglib/taglib/asf
        taglib/taglib/mpeg
        taglib/taglib/ogg
        taglib/taglib/ogg/flac
        taglib/taglib/flac
        taglib/taglib/mp4
        taglib/taglib/ogg/vorbis
        taglib/taglib/ogg/opus
        taglib/taglib/mpeg/id3v2
        taglib/taglib/mpeg/id3v2/frames
        taglib/taglib/mpeg/id3v1
        taglib/taglib/ape
        taglib/taglib/wavpack
        taglib/taglib/riff
        taglib/taglib/riff/aiff
        taglib/taglib/riff/wav
        taglib/taglib/dsf
        taglib/taglib/dsdiff)

add_library(${CMAKE_PROJECT_NAME} SHARED
        taglib.cpp
        fileref_ext.cpp)

target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log
        tag)

add_custom_command(TARGET ${CMAKE_PROJECT_NAME} POST_BUILD
        COMMAND ${CMAKE_OBJCOPY}
        --remove-section .comment
        --remove-section .note
        --strip-debug $<TARGET_FILE:${CMAKE_PROJECT_NAME}>)
