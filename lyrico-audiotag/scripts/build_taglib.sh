#!/bin/bash
set -e

# --------------------------------------
# 参数处理
# --------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT=${1:-"$SCRIPT_DIR/../.."}   # 默认项目根目录为脚本上两级
NDK_PATH=${2:-${ANDROID_NDK_HOME:-""}}

if [ -z "$NDK_PATH" ]; then
    echo "Error: NDK path not specified and ANDROID_NDK_HOME is not set."
    exit 1
fi

echo "Project root: $PROJECT_ROOT"
echo "NDK path: $NDK_PATH"

# --------------------------------------
# 目录设置
# --------------------------------------
TAGLIB_SRC_DIR="$PROJECT_ROOT/src/main/cpp/taglib"
TAGLIB_BUILD_DIR="$PROJECT_ROOT/build/taglib"
TAGLIB_PKG_DIR="$PROJECT_ROOT/pkg/taglib"
NDK_TOOLCHAIN="$PROJECT_ROOT/src/main/cpp/android.toolchain.cmake"

echo "Taglib source: $TAGLIB_SRC_DIR"
echo "Build dir: $TAGLIB_BUILD_DIR"
echo "Install dir: $TAGLIB_PKG_DIR"
echo "NDK toolchain: $NDK_TOOLCHAIN"

[ -d "$TAGLIB_SRC_DIR" ] || { echo "Error: Taglib source directory not found"; exit 1; }
[ -f "$NDK_TOOLCHAIN" ] || { echo "Error: NDK toolchain not found"; exit 1; }

# --------------------------------------
# 创建必要目录
# --------------------------------------
mkdir -p "$TAGLIB_BUILD_DIR"
mkdir -p "$TAGLIB_PKG_DIR"

# --------------------------------------
# 架构定义
# --------------------------------------
X86_ARCH=x86
X86_64_ARCH=x86_64
ARMV7_ARCH=armeabi-v7a
ARMV8_ARCH=arm64-v8a

# --------------------------------------
# 生成器检查
# --------------------------------------
if ! command -v ninja &> /dev/null; then
    echo "Warning: Ninja not found, trying make or mingw32-make"
    if command -v mingw32-make &> /dev/null; then
        GENERATOR="MinGW Makefiles"
    elif command -v make &> /dev/null; then
        GENERATOR="Unix Makefiles"
    else
        echo "Error: No suitable build generator found (ninja, mingw32-make, make)"
        exit 1
    fi
else
    GENERATOR="Ninja"
fi

echo "Using CMake generator: $GENERATOR"

# --------------------------------------
# 构建函数
# --------------------------------------
build_for_arch() {
    local ARCH=$1
    local DST_DIR="$TAGLIB_BUILD_DIR/$ARCH"
    local PKG_DIR="$TAGLIB_PKG_DIR/$ARCH"

    echo "=========================================="
    echo "Building ABI: $ARCH"
    echo "Build dir: $DST_DIR"
    echo "Install dir: $PKG_DIR"
    echo "=========================================="

    rm -rf "$DST_DIR"
    mkdir -p "$DST_DIR"
    mkdir -p "$PKG_DIR"

    cmake -B "$DST_DIR" \
        -G "$GENERATOR" \
        -DCMAKE_TOOLCHAIN_FILE="$NDK_TOOLCHAIN" \
        -DANDROID_NDK="$NDK_PATH" \
        -DANDROID_ABI="$ARCH" \
        -DANDROID_PLATFORM=android-21 \
        -DBUILD_SHARED_LIBS=OFF \
        -DVISIBILITY_HIDDEN=ON \
        -DBUILD_TESTING=OFF \
        -DBUILD_EXAMPLES=OFF \
        -DBUILD_BINDINGS=OFF \
        -DWITH_ZLIB=OFF \
        -DCMAKE_BUILD_TYPE=Release \
        -DWITH_APE=OFF \
        -DWITH_ASF=OFF \
        -DWITH_MOD=OFF \
        -DWITH_SHORTEN=OFF \
        -DWITH_TRUEAUDIO=OFF \
        -DCMAKE_CXX_FLAGS="-fPIC" \
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
        -DCMAKE_SYSTEM_NAME=Android \
        "$TAGLIB_SRC_DIR"

    # 编译
    echo "Building for $ARCH..."
    if [ "$GENERATOR" = "Ninja" ]; then
        cmake --build "$DST_DIR" --config Release -j$(nproc 2>/dev/null || echo 4)
    else
        cd "$DST_DIR"
        make -j$(nproc 2>/dev/null || echo 4)
    fi

    # 安装
    echo "Installing to $PKG_DIR"
    cmake --install "$DST_DIR" --config Release --prefix "$PKG_DIR" --strip

    echo "Finished building $ARCH"
    echo ""
}

# --------------------------------------
# 构建所有架构
# --------------------------------------
echo "Building all architectures..."
build_for_arch "$X86_ARCH"
build_for_arch "$X86_64_ARCH"
build_for_arch "$ARMV7_ARCH"
build_for_arch "$ARMV8_ARCH"

echo "=========================================="
echo "All builds completed successfully!"
echo "Libraries installed at: $TAGLIB_PKG_DIR"
echo "Build results:"
ls -la "$TAGLIB_PKG_DIR"/*/lib/*.a 2>/dev/null || echo "No static libraries found"