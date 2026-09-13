cmake_minimum_required(VERSION 3.22.1)
project(reelbot_whisper)

set(CMAKE_CXX_STANDARD 17)

# Speech inference must remain optimized in debug APKs too; retain debug symbols.
add_compile_options(-O3)

# whisper.cpp is vendored as a git submodule at app/src/main/cpp/whisper.cpp/ :
#   git submodule add https://github.com/ggerganov/whisper.cpp app/src/main/cpp/whisper.cpp
# Pin to a specific tagged release rather than tracking main, so builds are reproducible.
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)

if(NOT EXISTS ${WHISPER_DIR}/CMakeLists.txt)
    message(FATAL_ERROR
        "whisper.cpp not found at ${WHISPER_DIR}. Run:\n"
        "  git submodule add https://github.com/ggerganov/whisper.cpp app/src/main/cpp/whisper.cpp\n"
        "  git submodule update --init --recursive\n"
        "before building. See app/src/main/cpp/README.md.")
endif()

# whisper.cpp's own CMakeLists builds libwhisper (+ ggml). We link against it rather than
# reimplementing anything — ReelBot's native layer is only the thin JNI bridge below.
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
add_subdirectory(${WHISPER_DIR} whisper_build)

add_library(reelbot_whisper SHARED whisper_jni.cpp)
target_link_options(reelbot_whisper PRIVATE "-Wl,-z,max-page-size=16384")

target_include_directories(reelbot_whisper PRIVATE
    ${WHISPER_DIR}/include
    ${WHISPER_DIR}/ggml/include
)

find_library(log-lib log)

target_link_libraries(reelbot_whisper
    whisper
    ${log-lib}
    android
)
