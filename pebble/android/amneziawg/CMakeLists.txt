cmake_minimum_required(VERSION 3.22.1)

project("amneziawg" C)

# Runs amneziawg-android's own Makefile (it fetches its pinned Go and patches
# the runtime like upstream), then installs the result as libawg-go.so so it
# can sit next to the official WireGuard library's libwg-go.so.
set(LIBWG_GO_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../vendor/amneziawg-android/tunnel/tools/libwg-go")
set(AWG_OUT "${CMAKE_CURRENT_BINARY_DIR}/awg-out")
set(AWG_LDFLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

add_custom_target(libawg-go.so
    WORKING_DIRECTORY "${LIBWG_GO_DIR}"
    COMMENT "Building amneziawg-go"
    VERBATIM
    # Upstream's go.mod asks for a newer Go than its Makefile fetches; like
    # upstream's own builds, let Go switch to the version go.mod names.
    COMMAND ${CMAKE_COMMAND} -E env GOTOOLCHAIN=auto "${ANDROID_HOST_PREBUILTS}/bin/make"
        ANDROID_ARCH_NAME=${ANDROID_ARCH_NAME}
        ANDROID_PACKAGE_NAME=${ANDROID_PACKAGE_NAME}
        GRADLE_USER_HOME=${GRADLE_USER_HOME}
        CC=${CMAKE_C_COMPILER}
        CFLAGS=${CMAKE_C_FLAGS}
        LDFLAGS=${AWG_LDFLAGS}
        SYSROOT=${CMAKE_SYSROOT}
        TARGET=${CMAKE_C_COMPILER_TARGET}
        DESTDIR=${AWG_OUT}
        BUILDDIR=${CMAKE_CURRENT_BINARY_DIR}/awg-build
        "CGO_LDFLAGS=--target=${CMAKE_C_COMPILER_TARGET} --sysroot=${CMAKE_SYSROOT} ${AWG_LDFLAGS} -Wl,--build-id=none -Wl,-soname=libawg-go.so"
    COMMAND ${CMAKE_COMMAND} -E copy "${AWG_OUT}/libwg-go.so" "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libawg-go.so"
)
