cmake_minimum_required(VERSION 3.22.1)

project("native_mmap_cache" LANGUAGES CXX)

add_library(
    native_mmap_cache
    SHARED
    native_mmap_cache.cpp
    kazview_native_core.cpp
)

target_compile_features(native_mmap_cache PRIVATE cxx_std_17)

find_library(
    log-lib
    log
)

target_link_libraries(
    native_mmap_cache
    ${log-lib}
)
