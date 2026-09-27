cmake_minimum_required(VERSION 3.22.1)

project("native_mmap_cache")

add_library(
    native_mmap_cache
    SHARED
    native_mmap_cache.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    native_mmap_cache
    ${log-lib}
)
