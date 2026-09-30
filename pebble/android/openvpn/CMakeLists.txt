cmake_minimum_required(VERSION 3.22.1)

project("openvpn" C CXX)

# OpenVPN 2 as OpenVPN for Android (ics-openvpn, pinned in vendor/) builds it:
# libopenvpn.so plus libovpnexec.so, the executable the service starts. Its
# CMakeLists is used unchanged.
set(ICS_CPP "${CMAKE_CURRENT_SOURCE_DIR}/../vendor/ics-openvpn/main/src/main/cpp")
set(PEBBLE_LIB_DIR "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}")

add_link_options("LINKER:-z,max-page-size=16384")

# ics-openvpn also builds OpenVPN 3 (which needs SWIG) unless the output path
# looks like its "skeleton" flavour. Pebble only runs OpenVPN 2, so point the
# path there while its CMakeLists is read, then put the two targets back into
# the directory Gradle packages.
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/build/intermediates/cmake/skeleton/")
add_subdirectory("${ICS_CPP}" ics-openvpn)

set_target_properties(openvpn PROPERTIES LIBRARY_OUTPUT_DIRECTORY "${PEBBLE_LIB_DIR}")
set_target_properties(libovpnexec.so PROPERTIES RUNTIME_OUTPUT_DIRECTORY "${PEBBLE_LIB_DIR}")
