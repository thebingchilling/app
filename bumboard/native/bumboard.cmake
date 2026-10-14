# BumBoard native additions, included at the end of upstream's app/src/main/cpp/CMakeLists.txt
# (patches/0001-build-integration.patch).
#
# 1. Builds fcitx5-rime into the app, the same way upstream's Rime plugin does
#    (plugin/rime/src/main/cpp/CMakeLists.txt) — keep the two in sync when updating upstream.
# 2. Installs BumBoard's Rime data (built by bumboard/data/build_data.py) as prebuilt assets.

get_filename_component(BUMBOARD_DIR "${CMAKE_CURRENT_LIST_DIR}/.." ABSOLUTE)
get_filename_component(FCITX5_ANDROID_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../.." ABSOLUTE)

include(GNUInstallDirs)
find_package(ECM MODULE)

set(Rime_VERSION "1.12.0")
set(Rime_dependencies
        log # glog links to android log
        "${PREBUILT_DIR}/glog/${ANDROID_ABI}/lib/libglog.a"
        "${PREBUILT_DIR}/leveldb/${ANDROID_ABI}/lib/libleveldb.a"
        "${PREBUILT_DIR}/lua/${ANDROID_ABI}/lib/liblua_static.a"
        "${PREBUILT_DIR}/marisa/${ANDROID_ABI}/lib/libmarisa.a"
        "${PREBUILT_DIR}/opencc/${ANDROID_ABI}/lib/libopencc.a"
        "${PREBUILT_DIR}/yaml-cpp/${ANDROID_ABI}/lib/libyaml-cpp.a"
        )

add_library(Rime_static STATIC IMPORTED)
set_target_properties(Rime_static PROPERTIES
        IMPORTED_LOCATION "${PREBUILT_DIR}/librime/${ANDROID_ABI}/lib/librime.a"
        INTERFACE_INCLUDE_DIRECTORIES "${PREBUILT_DIR}/librime/${ANDROID_ABI}/include"
        INTERFACE_LINK_LIBRARIES "${Rime_dependencies}"
        )

# librime registers its modules with static initialisers, so link the whole archive
set(RIME_TARGET $<LINK_LIBRARY:WHOLE_ARCHIVE,Rime_static>)
set(RIME_DATA_DIR "${CMAKE_INSTALL_DATADIR}/rime-data")
add_subdirectory("${FCITX5_ANDROID_DIR}/plugin/rime/src/main/cpp/fcitx5-rime"
        "${CMAKE_CURRENT_BINARY_DIR}/fcitx5-rime")

set(BUMBOARD_RIME_DATA "${BUMBOARD_DIR}/data/out/rime-data")
if(NOT EXISTS "${BUMBOARD_RIME_DATA}/default.yaml")
    message(FATAL_ERROR "BumBoard Rime data is missing. Run: python3 bumboard/data/build_data.py")
endif()
install(DIRECTORY "${BUMBOARD_RIME_DATA}/"
        DESTINATION "${FCITX_INSTALL_DATADIR}/rime-data"
        COMPONENT prebuilt-assets)
