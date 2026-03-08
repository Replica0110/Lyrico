cmake_minimum_required (VERSION 3.0.2)
project (ftest VERSION 0.0.1 LANGUAGES CXX)

enable_testing()

add_executable(smoke smoke.cpp)
target_include_directories (smoke PUBLIC
    ../
)

set_target_properties(smoke
                      PROPERTIES
                      CXX_STANDARD 98
                      CXX_STANDARD_REQUIRED YES
                      CXX_EXTENSIONS NO)

target_compile_options(smoke PRIVATE
    $<$<CXX_COMPILER_ID:MSVC>:/W4>
    $<$<NOT:$<CXX_COMPILER_ID:MSVC>>:-Wall -Wextra -Wpedantic>)
                    


add_test(smoke_test smoke)
