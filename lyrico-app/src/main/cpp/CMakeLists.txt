cmake_minimum_required(VERSION 4.1.2)
project("third_party")

# ============================================================================
# ebur128 库 - 音频响度测量 (EBU R128 标准)
# ============================================================================
add_library(ebur128 SHARED
    ebur128.cpp              # C++ JNI 桥接层
    ebur128/ebur128.c        # ebur128 核心 C 实现
)

# 设置包含目录
target_include_directories(ebur128 PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR})

# 编译选项配置
set(EBUR128_COMPILE_FLAGS
    -Wno-cpp                 # 禁用 C 预处理器警告
)

# x86/x86_64 架构优化：启用 SSE2 硬件 FTZ (Flush-To-Zero)
if(CMAKE_ANDROID_ARCH_ABI MATCHES "^x86")
    list(APPEND EBUR128_COMPILE_FLAGS -msse2 -mfpmath=sse)
endif()

target_compile_options(ebur128 PRIVATE
    ${EBUR128_COMPILE_FLAGS}
)

# 链接系统库
target_link_libraries(ebur128 PRIVATE
    android                  # Android NDK 支持
    log                      # Android 日志库
)

# ============================================================================
# QuickJS 引擎 - JavaScript 运行时
# ============================================================================

# --- QuickJS 核心静态库 ---
# 包含 JS 引擎核心、正则表达式、Unicode 支持和数字转换功能
add_library(quickjs STATIC
    quickjs-ng/quickjs.c     # QuickJS 核心引擎
    quickjs-ng/libregexp.c   # 正则表达式支持
    quickjs-ng/libunicode.c  # Unicode 处理
    quickjs-ng/dtoa.c        # 数字与字符串转换
)

# 设置包含目录（PUBLIC 使依赖此库的目标也能访问）
target_include_directories(quickjs PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/quickjs-ng)

# 编译定义：设置 QuickJS 版本号
target_compile_definitions(quickjs PRIVATE
    CONFIG_VERSION="0.14.0")

# 强制使用 C11 标准
set_target_properties(quickjs PROPERTIES
    C_STANDARD 11
    C_STANDARD_REQUIRED ON)

# --- QuickJS JNI 桥接动态库 ---
# 提供 Java/Kotlin 与 QuickJS 之间的交互接口
add_library(quickjs-ng SHARED
    quickjs_bridge.cpp       # JNI 桥接实现
)

# 链接依赖库
target_link_libraries(quickjs-ng PRIVATE
    quickjs                  # QuickJS 核心静态库
    android                  # Android NDK 支持
    log                      # Android 日志库
)
