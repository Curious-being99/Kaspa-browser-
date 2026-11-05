cmake_minimum_required(VERSION 3.21)
project(kaspabrowser_cef LANGUAGES C CXX)

set(CEF_ROOT "" CACHE PATH "Extracted CEF binary distribution root")
if(NOT CEF_ROOT OR NOT EXISTS "${CEF_ROOT}/include/cef_app.h")
  message(FATAL_ERROR "Set CEF_ROOT to an extracted Linux CEF binary distribution")
endif()

set(CMAKE_MODULE_PATH ${CMAKE_MODULE_PATH} "${CEF_ROOT}/cmake")
find_package(CEF REQUIRED)

# Build the CEF C++ wrapper and link the official CEF runtime target.
add_subdirectory(${CEF_LIBCEF_DLL_WRAPPER_PATH} libcef_dll_wrapper)
ADD_LOGICAL_TARGET("libcef_lib" "${CEF_LIB_DEBUG}" "${CEF_LIB_RELEASE}")

set(CEF_TARGET "kaspabrowser-cef")
SET_CEF_TARGET_OUT_DIR()

add_executable(${CEF_TARGET} src/main.cc src/browser_host.cc)
SET_EXECUTABLE_TARGET_PROPERTIES(${CEF_TARGET})
add_dependencies(${CEF_TARGET} libcef_dll_wrapper)
target_include_directories(${CEF_TARGET} PRIVATE "${CEF_ROOT}" "${CMAKE_CURRENT_SOURCE_DIR}/src")
target_link_libraries(${CEF_TARGET} libcef_lib libcef_dll_wrapper ${CEF_STANDARD_LIBS})
set_target_properties(${CEF_TARGET} PROPERTIES
  INSTALL_RPATH "$ORIGIN"
  BUILD_WITH_INSTALL_RPATH TRUE
  RUNTIME_OUTPUT_DIRECTORY ${CEF_TARGET_OUT_DIR}
)
COPY_FILES("${CEF_TARGET}" "${CEF_BINARY_FILES}" "${CEF_BINARY_DIR}" "${CEF_TARGET_OUT_DIR}")
COPY_FILES("${CEF_TARGET}" "${CEF_RESOURCE_FILES}" "${CEF_RESOURCE_DIR}" "${CEF_TARGET_OUT_DIR}")
if(EXISTS "${CEF_BINARY_DIR}/libminigbm.so")
  COPY_FILES("${CEF_TARGET}" "libminigbm.so" "${CEF_BINARY_DIR}" "${CEF_TARGET_OUT_DIR}")
endif()
SET_LINUX_SUID_PERMISSIONS("${CEF_TARGET}" "${CEF_TARGET_OUT_DIR}/chrome-sandbox")
