cmake_minimum_required(VERSION 3.20)
project(kazview-webkitgtk CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(PkgConfig REQUIRED)
pkg_check_modules(GTK3 REQUIRED gtk+-3.0)
pkg_check_modules(WEBKIT REQUIRED webkit2gtk-4.1)

add_executable(kazview-webkitgtk
    src/main.cpp
)

target_include_directories(kazview-webkitgtk PRIVATE
    ${GTK3_INCLUDE_DIRS}
    ${WEBKIT_INCLUDE_DIRS}
)

target_link_libraries(kazview-webkitgtk PRIVATE
    ${GTK3_LIBRARIES}
    ${WEBKIT_LIBRARIES}
)

target_compile_options(kazview-webkitgtk PRIVATE
    ${GTK3_CFLAGS_OTHER}
    ${WEBKIT_CFLAGS_OTHER}
)
