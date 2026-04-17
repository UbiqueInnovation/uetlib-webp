cmake_minimum_required(VERSION 3.20)
project(uetlib_webp_native C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

find_package(PkgConfig REQUIRED)
pkg_check_modules(WEBP REQUIRED IMPORTED_TARGET libwebp)
pkg_check_modules(WEBPMUX REQUIRED IMPORTED_TARGET libwebpmux)
pkg_check_modules(TURBOJPEG REQUIRED IMPORTED_TARGET libturbojpeg)
pkg_check_modules(PNG REQUIRED IMPORTED_TARGET libpng)

add_library(uetlib_webp_native SHARED
    src/uetlib_webp.c
)

target_include_directories(uetlib_webp_native
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_link_libraries(uetlib_webp_native
    PRIVATE
        PkgConfig::WEBP
        PkgConfig::WEBPMUX
        PkgConfig::TURBOJPEG
        PkgConfig::PNG
)

if(APPLE)
    set_target_properties(uetlib_webp_native PROPERTIES
        OUTPUT_NAME "uetlib_webp_native"
        SUFFIX ".dylib"
    )
elseif(UNIX)
    set_target_properties(uetlib_webp_native PROPERTIES
        OUTPUT_NAME "uetlib_webp_native"
        SUFFIX ".so"
    )
endif()
