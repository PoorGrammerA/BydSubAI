cmake_minimum_required(VERSION 3.22.1)

project("ggwave-jni")

# Add the shared library
add_library(ggwave-jni SHARED
        ggwave/ggwave.cpp
        ggwave-jni.cpp
)

# Specify include directories
target_include_directories(ggwave-jni PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/ggwave
)

# Find and link log library for Android logging
find_library(log-lib log)
target_link_libraries(ggwave-jni ${log-lib})
