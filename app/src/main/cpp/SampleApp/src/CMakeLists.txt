#==============================================================================
#
#  Copyright (c) 2022 Qualcomm Technologies, Inc.
#  All Rights Reserved.
#  Confidential and Proprietary - Qualcomm Technologies, Inc.
#
#==============================================================================

add_library(qnn_sample_app_lib STATIC
        "QnnSampleApp.cpp"
        "Log/Logger.cpp"
        "Log/LogUtils.cpp"
        "PAL/src/linux/Directory.cpp"
        "PAL/src/linux/DynamicLoading.cpp"
        "PAL/src/linux/FileOp.cpp"
        "PAL/src/linux/Path.cpp"
        "PAL/src/common/GetOpt.cpp"
        "PAL/src/common/StringOp.cpp"
        "Utils/DataUtil.cpp"
        "Utils/DynamicLoadUtil.cpp"
        "Utils/IOTensor.cpp"
        "Utils/QnnSampleAppUtils.cpp"
        "WrapperUtils/QnnWrapperUtils.cpp")

target_compile_definitions(qnn_sample_app_lib PUBLIC "-DNOMINMAX")

target_include_directories(qnn_sample_app_lib PUBLIC
        CachingUtil
        Log
        PAL/include
        Utils
        WrapperUtils
        ./
        ../
        ../../
        ../make
        ${QNN_SDK_ROOT}/include/QNN)

#set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG} /MTd")
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} /MT /O2 /Ob3")
