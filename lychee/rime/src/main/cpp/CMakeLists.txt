# Lychee: JNI bridge to librime, linked against fcitx5-android's prebuilt
# static librime (with the lua and octagram plugins built in).
cmake_minimum_required(VERSION 3.22)
project(lychee_rime CXX)

set(CMAKE_CXX_STANDARD 17)
set(PREBUILT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../prebuilt")

add_library(Rime_static STATIC IMPORTED)
set_target_properties(Rime_static PROPERTIES
        IMPORTED_LOCATION "${PREBUILT_DIR}/librime/${ANDROID_ABI}/lib/librime.a"
        INTERFACE_INCLUDE_DIRECTORIES "${PREBUILT_DIR}/librime/${ANDROID_ABI}/include")

add_library(lychee_rime SHARED rime_jni.cpp)
# Rime's modules (including lua and octagram) register themselves from static
# constructors, so the whole archive must be linked.
target_link_libraries(lychee_rime
        "$<LINK_LIBRARY:WHOLE_ARCHIVE,Rime_static>"
        "${PREBUILT_DIR}/glog/${ANDROID_ABI}/lib/libglog.a"
        "${PREBUILT_DIR}/leveldb/${ANDROID_ABI}/lib/libleveldb.a"
        "${PREBUILT_DIR}/lua/${ANDROID_ABI}/lib/liblua_static.a"
        "${PREBUILT_DIR}/marisa/${ANDROID_ABI}/lib/libmarisa.a"
        "${PREBUILT_DIR}/opencc/${ANDROID_ABI}/lib/libopencc.a"
        "${PREBUILT_DIR}/yaml-cpp/${ANDROID_ABI}/lib/libyaml-cpp.a"
        log)
target_link_options(lychee_rime PRIVATE "LINKER:--gc-sections,--build-id=none")
