cmake_minimum_required(VERSION 3.22.1)

project("orion-whisper")

set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)

add_subdirectory(whisper.cpp)

add_library(orion_whisper SHARED
    orion_whisper.cpp
)

target_include_directories(orion_whisper PRIVATE
    whisper.cpp/include
    whisper.cpp/src
    whisper.cpp/ggml/include
    whisper.cpp/ggml/src
    whisper.cpp/ggml/src/ggml-cpu
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(orion_whisper
    whisper
    ${log-lib}
    ${android-lib}
)
