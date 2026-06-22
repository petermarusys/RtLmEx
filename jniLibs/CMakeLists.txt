cmake_minimum_required(VERSION 3.22.1)
project("llmsdk_demo")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(CMAKE_CXX_FLAGS_RELEASE "-O3 -DNDEBUG")
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -fuse-ld=ld")

set(SOURCE_FILES
        src/llm.cpp
        jni_native_llm.cpp
)

add_library(llm SHARED ${SOURCE_FILES})

# add_library 이후에 와야 함
target_compile_options(llm PRIVATE -stdlib=libc++)
target_link_options(llm PRIVATE -stdlib=libc++)

set(EXTERNAL_LIB_PATH "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/arm64-v8a")

include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)

target_link_libraries(llm
        -L${EXTERNAL_LIB_PATH}
        llmsdk
        adla
        teec
        tokenizers_cpp
        log dl m
)