cmake_minimum_required(VERSION 3.22.1)
project("llama-android")

# Set to ON once the llama.cpp submodule is initialized AND you want the real
# llama_decode loop linked in (longer builds, needs full NDK toolchain).
# Default OFF: builds the lightweight JNI stub so the APK compiles everywhere;
# the Kotlin layer (LlamaContext) detects the unlinked decoder and serves the
# managed factual engine instead of emitting empty bubbles.
option(EDGELLM_LINK_LLAMA_CPP "Link real llama.cpp decode loop" OFF)

# Add the llama-android JNI shared library
add_library(llama-android SHARED
    llama-android.cpp
)

# Link standard Android NDK logging libraries
target_link_libraries(llama-android
    android
    log
)

if(EDGELLM_LINK_LLAMA_CPP)
    # Expects the submodule at <repo-root>/llama.cpp (see scripts/setup-llama-cpp.sh)
    set(LLAMA_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../llama.cpp")
    if(EXISTS "${LLAMA_CPP_DIR}/CMakeLists.txt")
        add_subdirectory("${LLAMA_CPP_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/llama.cpp-build")
        target_link_libraries(llama-android llama common)
        target_compile_definitions(llama-android PRIVATE EDGELLM_REAL_DECODE=1)
    else()
        message(WARNING "EDGELLM_LINK_LLAMA_CPP=ON but ${LLAMA_CPP_DIR}/CMakeLists.txt not found; building stub")
    endif()
endif()
