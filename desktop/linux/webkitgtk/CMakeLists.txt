cmake_minimum_required(VERSION 3.20)
project(kazview-webkitgtk LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(KAZIUM_WITH_RUST_FFI "Link against compiled kaspasearch Rust wallet FFI library" OFF)
set(KAZIUM_WALLET_FFI_LIB "" CACHE FILEPATH "Path to libkaspasearch.so or libkaspasearch.a")

find_package(PkgConfig REQUIRED)
find_package(Threads REQUIRED)
pkg_check_modules(GTK3 REQUIRED gtk+-3.0)
pkg_check_modules(WEBKIT REQUIRED webkit2gtk-4.1)

set(KAZVIEW_CPP_SHARED_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../app/src/main/cpp")

add_executable(kazview-webkitgtk
    src/main.cpp
    "${KAZVIEW_CPP_SHARED_DIR}/kazview_native_core.cpp"
)

target_include_directories(kazview-webkitgtk PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/src
    "${KAZVIEW_CPP_SHARED_DIR}"
    ${GTK3_INCLUDE_DIRS}
    ${WEBKIT_INCLUDE_DIRS}
)

target_compile_definitions(kazview-webkitgtk PRIVATE
    KAZVIEW_SKIP_ANDROID_JNI=1
)

target_link_libraries(kazview-webkitgtk PRIVATE
    Threads::Threads
    ${GTK3_LIBRARIES}
    ${WEBKIT_LIBRARIES}
)

if(KAZIUM_WITH_RUST_FFI AND KAZIUM_WALLET_FFI_LIB)
    target_compile_definitions(kazview-webkitgtk PRIVATE KAZIUM_HAS_WALLET_FFI=1)
    target_link_libraries(kazview-webkitgtk PRIVATE "${KAZIUM_WALLET_FFI_LIB}")
endif()

target_compile_options(kazview-webkitgtk PRIVATE
    -Wall
    -Wextra
    ${GTK3_CFLAGS_OTHER}
    ${WEBKIT_CFLAGS_OTHER}
)

install(TARGETS kazview-webkitgtk RUNTIME DESTINATION bin)
