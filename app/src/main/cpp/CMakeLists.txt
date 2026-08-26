cmake_minimum_required(VERSION 3.22.1)
project(vokie_whisper LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
# Android Gradle uses Debug for debug APKs and RelWithDebInfo for release APKs.
# Keep GGML/whisper compute kernels optimized in both variants; JNI-only flags are insufficient.
set(CMAKE_C_FLAGS_DEBUG "-O2 -g -DNDEBUG" CACHE STRING "" FORCE)
set(CMAKE_CXX_FLAGS_DEBUG "-O2 -g -DNDEBUG" CACHE STRING "" FORCE)
set(CMAKE_C_FLAGS_RELWITHDEBINFO "-O3 -g -DNDEBUG" CACHE STRING "" FORCE)
set(CMAKE_CXX_FLAGS_RELWITHDEBINFO "-O3 -g -DNDEBUG" CACHE STRING "" FORCE)
set(CMAKE_C_FLAGS_RELEASE "-O3 -DNDEBUG" CACHE STRING "" FORCE)
set(CMAKE_CXX_FLAGS_RELEASE "-O3 -DNDEBUG" CACHE STRING "" FORCE)
# Enable ARMv8.2 NEON/dot-product/FP16 only for arm64. Applying those flags to
# armeabi-v7a would produce an incompatible binary, so the 32-bit ABI remains portable.
if (ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)
endif()
# whisper.cpp has several large translation units; cap native compile concurrency for low-memory CI/dev hosts.
set_property(GLOBAL PROPERTY JOB_POOLS vokie_compile_pool=2)
set(CMAKE_JOB_POOL_COMPILE vokie_compile_pool)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
set(GGML_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)

add_subdirectory(third_party/whisper.cpp whisper-build EXCLUDE_FROM_ALL)

add_library(vokie_whisper SHARED vokie_whisper_jni.cpp)
target_link_libraries(vokie_whisper PRIVATE whisper log android)
target_compile_options(vokie_whisper PRIVATE -O3 -fvisibility=hidden -fvisibility-inlines-hidden)
target_link_options(vokie_whisper PRIVATE -Wl,--gc-sections)
