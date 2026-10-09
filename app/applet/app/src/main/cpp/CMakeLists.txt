cmake_minimum_required(VERSION 3.22.1)
project(edgellm LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Dummy/stub native library for EdgeLLM core inference interface
add_library(edgellm SHARED
    native-lib.cpp
)

target_include_directories(edgellm PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(edgellm
    ${log-lib}
    ${android-lib}
)
