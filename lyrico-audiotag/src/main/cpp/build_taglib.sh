#!/bin/bash
set -e  # 遇到错误立即退出

# 获取脚本所在目录的绝对路径
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKING_DIR=${1:-$SCRIPT_DIR}
NDK_PATH=${2:-"D:/Android/Sdk/ndk/27.0.12077973"}

echo "Working directory is at $WORKING_DIR"
echo "NDK path is at $NDK_PATH"

# 切换到工作目录
cd "$WORKING_DIR"

# 设置路径
TAGLIB_SRC_DIR="${WORKING_DIR}/taglib"
TAGLIB_DST_DIR="${WORKING_DIR}/taglib/build"
TAGLIB_PKG_DIR="${WORKING_DIR}/taglib/pkg"
NDK_TOOLCHAIN="${WORKING_DIR}/android.toolchain.cmake"

echo "Taglib source is at $TAGLIB_SRC_DIR"
echo "Taglib build is at $TAGLIB_DST_DIR"
echo "Taglib package is at $TAGLIB_PKG_DIR"
echo "NDK toolchain is at $NDK_TOOLCHAIN"

# 检查必要的路径
if [ ! -d "$TAGLIB_SRC_DIR" ]; then
    echo "Error: Taglib source directory not found at $TAGLIB_SRC_DIR"
    exit 1
fi

if [ ! -f "$NDK_TOOLCHAIN" ]; then
    echo "Error: NDK toolchain not found at $NDK_TOOLCHAIN"
    exit 1
fi

# 创建必要的目录
mkdir -p "$TAGLIB_DST_DIR"
mkdir -p "$TAGLIB_PKG_DIR"

# 定义架构
X86_ARCH=x86
X86_64_ARCH=x86_64
ARMV7_ARCH=armeabi-v7a
ARMV8_ARCH=arm64-v8a

# 检查是否安装了Ninja
if ! command -v ninja &> /dev/null; then
    echo "Warning: Ninja not found, trying to use mingw32-make or make"
    # 尝试检测可用的生成器
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

build_for_arch() {
    local ARCH=$1
    local DST_DIR="$TAGLIB_DST_DIR/$ARCH"
    local PKG_DIR="$TAGLIB_PKG_DIR/$ARCH"
    
    echo "=========================================="
    echo "Building for ABI: $ARCH"
    echo "Build directory: $DST_DIR"
    echo "Install directory: $PKG_DIR"
    echo "=========================================="
    
    # 清理旧的构建目录
    rm -rf "$DST_DIR"
    mkdir -p "$DST_DIR"
    
    # 进入源目录
    cd "$TAGLIB_SRC_DIR"
    
    # CMake配置
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
        -DCMAKE_SYSTEM_NAME=Android
    
    # 检查CMake配置是否成功
    if [ $? -ne 0 ]; then
        echo "CMake configuration failed for $ARCH"
        exit 1
    fi
    
    # 编译 - 根据生成器选择并行编译参数
    echo "Building for $ARCH..."
    if [ "$GENERATOR" = "Ninja" ]; then
        cmake --build "$DST_DIR" --config Release -j$(nproc 2>/dev/null || echo 4)
    else
        # 对于Makefiles，直接调用make
        cd "$DST_DIR"
        make -j$(nproc 2>/dev/null || echo 4)
        cd "$TAGLIB_SRC_DIR"
    fi
    
    if [ $? -ne 0 ]; then
        echo "Build failed for $ARCH"
        exit 1
    fi
    
    # 安装
    echo "Installing for $ARCH to $PKG_DIR"
    cmake --install "$DST_DIR" --config Release --prefix "$PKG_DIR" --strip
    
    if [ $? -ne 0 ]; then
        echo "Install failed for $ARCH"
        exit 1
    fi
    
    echo "Finished building for $ARCH"
    echo ""
}

# 为所有架构构建
echo "Starting builds for all architectures..."
build_for_arch "$X86_ARCH"
build_for_arch "$X86_64_ARCH"
build_for_arch "$ARMV7_ARCH"
build_for_arch "$ARMV8_ARCH"

echo "=========================================="
echo "All builds completed successfully!"
echo "Libraries are installed in: $TAGLIB_PKG_DIR"

# 显示构建结果
echo "Build results:"
ls -la "$TAGLIB_PKG_DIR"/*/lib/*.a 2>/dev/null || echo "No static libraries found"