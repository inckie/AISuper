cmake_minimum_required(VERSION 3.20)
project(aisuper_cui LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 99)
set(CMAKE_C_STANDARD_REQUIRED ON)

include(FetchContent)

# 1. CUI (MIT, immediate-mode native UI library)
set(CUI_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(CUI_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(CUI_IMAGE_ALL_FORMATS ON CACHE BOOL "" FORCE)
FetchContent_Declare(
    cui
    GIT_REPOSITORY https://github.com/yappologistic/CUI.git
    GIT_TAG v1.5.0
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(cui)

if(EXISTS "${cui_SOURCE_DIR}/cmake/CuiApp.cmake")
    include("${cui_SOURCE_DIR}/cmake/CuiApp.cmake")
endif()

# 2. nlohmann/json
set(JSON_BuildTests OFF CACHE INTERNAL "")
FetchContent_Declare(
    nlohmann_json
    GIT_REPOSITORY https://github.com/nlohmann/json.git
    GIT_TAG v3.11.3
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(nlohmann_json)

# 3. miniz (for .zip applet archives)
set(BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_FUZZERS OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(INSTALL_PROJECT OFF CACHE BOOL "" FORCE)
set(CMAKE_POLICY_VERSION_MINIMUM 3.5)
FetchContent_Declare(
    miniz
    GIT_REPOSITORY https://github.com/richgel999/miniz.git
    GIT_TAG 3.0.2
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(miniz)

# 4. LunaSVG (MIT, full SVG + CSS + currentColor rendering)
set(LUNASVG_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(USE_SYSTEM_PLUTOVG OFF CACHE BOOL "" FORCE)
FetchContent_Declare(
    lunasvg
    GIT_REPOSITORY https://github.com/sammycage/lunasvg.git
    GIT_TAG v3.5.0
    GIT_SHALLOW TRUE
    GIT_SUBMODULES_RECURSE TRUE
)
FetchContent_MakeAvailable(lunasvg)

# Shared core library (used by both GUI app and headless smoke test)
add_library(aisuper_cui_lib STATIC
    src/core_bridge.cpp
    src/applet_loader.cpp
    src/style_resolver.cpp
    src/stb_mem.c
    src/image_cache.cpp
    src/widget_renderer.cpp
)

target_include_directories(aisuper_cui_lib
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
        ${CMAKE_CURRENT_SOURCE_DIR}/src
    PRIVATE
        ${cui_SOURCE_DIR}/third_party
)

target_link_libraries(aisuper_cui_lib
    PUBLIC
        cui::cui
        nlohmann_json::nlohmann_json
        miniz
        lunasvg::lunasvg
)

if(WIN32)
    target_compile_definitions(aisuper_cui_lib PUBLIC WIN32_LEAN_AND_MEAN NOMINMAX)
    target_link_libraries(aisuper_cui_lib PUBLIC winhttp)
elseif(UNIX AND NOT APPLE)
    target_link_libraries(aisuper_cui_lib PUBLIC dl pthread)
endif()

# GUI Application
add_executable(aisuper_cui src/main.cpp)
target_link_libraries(aisuper_cui PRIVATE aisuper_cui_lib)
if(COMMAND cui_app_target)
    cui_app_target(aisuper_cui)
endif()

# Automated End-to-End Smoke Test (console app)
add_executable(aisuper_cui_smoke tests/smoke_test.cpp)
target_link_libraries(aisuper_cui_smoke PRIVATE aisuper_cui_lib)

# Copy aisuper_core.dll next to executables if available
set(_default_dll_rel "${CMAKE_CURRENT_SOURCE_DIR}/../kotlin/native-bridge/build/bin/mingwX64/releaseShared/aisuper_core.dll")
set(_default_dll_dbg "${CMAKE_CURRENT_SOURCE_DIR}/../kotlin/native-bridge/build/bin/mingwX64/debugShared/aisuper_core.dll")
if(EXISTS "${_default_dll_rel}")
    set(_default_dll "${_default_dll_rel}")
else()
    set(_default_dll "${_default_dll_dbg}")
endif()

set(AISUPER_CORE_DLL "${_default_dll}" CACHE FILEPATH "Path to aisuper_core shared library")

if(EXISTS "${AISUPER_CORE_DLL}")
    foreach(_tgt aisuper_cui aisuper_cui_smoke)
        add_custom_command(TARGET ${_tgt} POST_BUILD
            COMMAND ${CMAKE_COMMAND} -E copy_if_different
                "${AISUPER_CORE_DLL}"
                "$<TARGET_FILE_DIR:${_tgt}>"
            COMMENT "Copying aisuper_core library to $<TARGET_FILE_DIR:${_tgt}>"
        )
    endforeach()
endif()
