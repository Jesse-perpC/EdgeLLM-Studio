cmake_minimum_required(VERSION 3.22.1)
project("llama-android")

# Add the llama-android JNI shared library
add_library(llama-android SHARED
    llama-android.cpp
)

# Link standard Android NDK logging libraries
target_link_libraries(llama-android
    android
    log
)
