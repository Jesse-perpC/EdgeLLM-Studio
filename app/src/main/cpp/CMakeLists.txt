cmake_minimum_required(VERSION 3.22.1)
project("llama-android" C CXX)

# ---------------------------------------------------------------------------
# REAL llama.cpp BUILD
#
# There is deliberately no stub fallback here. The previous version of this
# file defaulted EDGELLM_LINK_LLAMA_CPP to OFF, which compiled a JNI shim that
# never called llama_decode() and silently returned a hardcoded string. That
# made the app appear to do on-device inference while running a lookup table.
#
# A missing submodule is now a hard build error. If you see one, run:
#     git submodule update --init --recursive
#     bash scripts/setup-llama-cpp.sh
# ---------------------------------------------------------------------------

set(LLAMA_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../llama.cpp")

if(NOT EXISTS "${LLAMA_CPP_DIR}/CMakeLists.txt")
    message(FATAL_ERROR
        "llama.cpp not found at: ${LLAMA_CPP_DIR}\n"
        "The GGUF engine cannot be built without it. Run:\n"
        "    git submodule update --init --recursive\n"
        "or:\n"
        "    bash scripts/setup-llama-cpp.sh")
endif()

# --- GPU backends -----------------------------------------------------------
# Both are OFF by default because neither toolchain ships with the Android NDK:
#
#   GGML_OPENCL -> ggml/src/ggml-opencl/CMakeLists.txt runs
#                  find_package(OpenCL REQUIRED). The NDK provides no
#                  libOpenCL; you need an ICD + headers from the Adreno or
#                  Mali SDK, linked via ANDROID_STL/extra libs.
#
#   GGML_VULKAN  -> ggml/src/ggml-vulkan/CMakeLists.txt runs
#                  find_package(Vulkan COMPONENTS glslc REQUIRED) plus
#                  find_package(SPIRV-Headers REQUIRED). That needs the
#                  Vulkan SDK (glslc) and SPIRV-Headers on the build host.
#
# GGML_HEXAGON (Qualcomm HTP NPU) is worse still: it requires a licensed
# Hexagon SDK and an HTP signing certificate.
#
# The default CPU backend (NEON + dotprod/i8mm on ARM64) is the only
# configuration that builds from a stock NDK. It is also the most reliable
# path on real devices, so it stays the default. Flip these on only once you
# have provisioned the corresponding toolchain.
option(EDGELLM_GPU_OPENCL "Enable ggml OpenCL backend (requires an OpenCL ICD for the target GPU)" OFF)
option(EDGELLM_GPU_VULKAN "Enable ggml Vulkan backend (requires Vulkan SDK glslc + SPIRV-Headers)"   OFF)
option(EDGELLM_NPU_HEXAGON "Enable ggml Hexagon/HTP backend (requires licensed Hexagon SDK)"         OFF)

# --- llama.cpp / ggml build options ----------------------------------------
# These must be set BEFORE add_subdirectory() because llama.cpp caches them.
#
# BUILD_SHARED_LIBS=OFF keeps llama + ggml as static archives so the whole
# runtime links into the single libllama-android.so. Otherwise the APK has to
# ship several .so files that must agree on ABI.
set(BUILD_SHARED_LIBS          OFF CACHE BOOL "" FORCE)

# Android-specific: subprocess spawning is not sandbox friendly, and llama.cpp
# disables it for us, but be explicit.
set(LLAMA_SUBPROCESS           OFF CACHE BOOL "" FORCE)

# LLAMA_STANDALONE is OFF under add_subdirectory, so these already default
# OFF. Set them anyway so a stale CMake cache cannot re-enable them and drag
# curl/openssl/CLI tools into an Android build.
set(LLAMA_BUILD_TESTS          OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS          OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES       OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER         OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP            OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON         OFF CACHE BOOL "" FORCE)   # we build our own sampler chain
set(LLAMA_BUILD_MTMD           OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL                 OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL              OFF CACHE BOOL "" FORCE)
set(LLAMA_LLGUIDANCE           OFF CACHE BOOL "" FORCE)
set(LLAMA_CCACHE               OFF CACHE BOOL "" FORCE)

# GGML_NATIVE must be OFF: we are cross-compiling, so -march=native would
# target the build machine's CPU rather than the phone.
set(GGML_NATIVE                OFF CACHE BOOL "" FORCE)
set(GGML_LTO                   OFF CACHE BOOL "" FORCE)
set(GGML_CCACHE                OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL            OFF CACHE BOOL "" FORCE)

# Android has no libgomp in the NDK; ggml-cpu falls back to its own thread
# pool if OpenMP is unavailable, so keep this OFF.
set(GGML_OPENMP                OFF CACHE BOOL "" FORCE)
set(GGML_BLAS                  OFF CACHE BOOL "" FORCE)
set(GGML_ACCELERATE            OFF CACHE BOOL "" FORCE)   # Apple only
set(GGML_METAL                 OFF CACHE BOOL "" FORCE)   # Apple only
set(GGML_RPC                   OFF CACHE BOOL "" FORCE)

set(GGML_OPENCL                ${EDGELLM_GPU_OPENCL}  CACHE BOOL "" FORCE)
set(GGML_VULKAN                ${EDGELLM_GPU_VULKAN}  CACHE BOOL "" FORCE)
set(GGML_HEXAGON               ${EDGELLM_NPU_HEXAGON} CACHE BOOL "" FORCE)

# Quiet the build: llama.cpp's own -W warnings drown out ours.
set(LLAMA_ALL_WARNINGS         OFF CACHE BOOL "" FORCE)
set(GGML_ALL_WARNINGS          OFF CACHE BOOL "" FORCE)
set(GGML_FATAL_WARNINGS        OFF CACHE BOOL "" FORCE)
set(LLAMA_FATAL_WARNINGS       OFF CACHE BOOL "" FORCE)

add_subdirectory("${LLAMA_CPP_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/llama.cpp-build")

# ---------------------------------------------------------------------------
# Android Vulkan link fix (vulkan flavor only)
#
# ggml-vulkan links Vulkan::Vulkan, which the NDK toolchain resolves to the
# minSdk (API 24) libvulkan.so stub. That stub exports Vulkan 1.0 symbols
# only, while ggml unconditionally calls Vulkan 1.1 entry points
# (vkGetPhysicalDeviceFeatures2, ...), so the final .so fails to link with
# "undefined symbol". Fix: relink ggml-vulkan against the newest NDK stub >=
# API 28 (Vulkan 1.1 arrived in API 28). Bionic lazily binds function symbols,
# so pre-28 devices still load the library; Vulkan entry points are only ever
# called when a 1.1-capable driver is present (see
# LlamaCppEngine.resolveGpuLayers), and the CPU path never touches them.
# ---------------------------------------------------------------------------
# NOTE: no Vulkan link surgery here on purpose. The vulkan flavor sets
# minSdk 28 (see app/build.gradle.kts), so the NDK toolchain links the
# API-28 libvulkan stub, which already exports the Vulkan 1.1 entry points
# ggml calls. Devices below API 28 cannot install that APK flavor at all
# (and have no Vulkan 1.1 driver anyway); on API 28+ devices without a
# driver, LlamaCppEngine.resolveGpuLayers falls back to CPU at runtime.

# ---------------------------------------------------------------------------
# JNI bridge
# ---------------------------------------------------------------------------
add_library(llama-android SHARED
    llama-android.cpp
)

target_compile_features(llama-android PRIVATE cxx_std_17)

# llama is a static archive; PIC is required for it to link into a shared lib.
set_target_properties(llama-android PROPERTIES
    POSITION_INDEPENDENT_CODE ON
)

# llama.cpp sets CMAKE_LIBRARY_OUTPUT_DIRECTORY in its own scope. Pin the
# output explicitly or the Android Gradle Plugin fails to find the .so.
set_target_properties(llama-android PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY              "${CMAKE_BINARY_DIR}"
    LIBRARY_OUTPUT_DIRECTORY_DEBUG        "${CMAKE_BINARY_DIR}"
    LIBRARY_OUTPUT_DIRECTORY_RELEASE      "${CMAKE_BINARY_DIR}"
    LIBRARY_OUTPUT_DIRECTORY_RELOCATABLE  "${CMAKE_BINARY_DIR}"
    LIBRARY_OUTPUT_DIRECTORY_MINSIZEREL   "${CMAKE_BINARY_DIR}"
)

# PUBLIC on `llama` puts include/ and ggml/include/ on our path, so
# llama.h / ggml.h / ggml-backend.h resolve without manual include_directories.
# `llama` -> `ggml` -> `ggml-base`, so ggml_backend_load_all() comes along.
target_link_libraries(llama-android
    llama
    android
    log
)

message(STATUS "---------------------------------------------------------")
message(STATUS "llama.cpp      : ${LLAMA_CPP_DIR}")
message(STATUS "CPU backend    : ON (ARM NEON)")
message(STATUS "OpenCL backend : ${EDGELLM_GPU_OPENCL}")
message(STATUS "Vulkan backend : ${EDGELLM_GPU_VULKAN}")
message(STATUS "Hexagon backend: ${EDGELLM_NPU_HEXAGON}")
message(STATUS "---------------------------------------------------------")
