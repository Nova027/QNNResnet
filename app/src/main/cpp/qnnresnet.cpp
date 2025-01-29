#include <jni.h>
#include <iostream>
#include <memory>
#include <string>
#include "BuildId.hpp"
#include "DynamicLoadUtil.hpp"
#include "Logger.hpp"
#include "PAL/DynamicLoading.hpp"
#include "PAL/GetOpt.hpp"
#include "QnnSampleApp.hpp"
#include "QnnSampleAppUtils.hpp"
#include <android/log.h>

#define LOG_D(...) __android_log_print(ANDROID_LOG_DEBUG, "QNNResnet", __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_qnnresnet_MainActivity_stringFromJNI(JNIEnv* env, jobject /* this */) {
    std::string hello = "Hello from Native++";
    return env->NewStringUTF(hello.c_str());
}

using app_t = qnn::tools::sample_app::QnnSampleApp;
using app_ptr_t = std::unique_ptr<app_t>;
using qnn_status_t = qnn::tools::sample_app::StatusCode;

std::string g_working_dir;
std::string g_model_path;
std::string g_backend_path;
std::string g_input_list_path;

static void* sg_backend_handle = nullptr;
static void* sg_model_handle = nullptr;

app_ptr_t app;
qnn_status_t device_property_support_status;

static const qnn_status_t appSUCCESS = qnn_status_t::SUCCESS;
static const qnn_status_t appFAILURE = qnn_status_t::FAILURE;

static bool is_qnn_success(qnn_status_t status) {
    return (status == appSUCCESS);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_qnnresnet_MainActivity_setPaths(JNIEnv* env, jobject /* this */, jstring working_dir,
                                                 jstring model_path, jstring runtime_path, jstring input_list_path)
{
    // Get bytes from Java string into C-style allocated char*
    const char* temp_working_dir = env->GetStringUTFChars(working_dir, nullptr);
    const char* temp_model_path = env->GetStringUTFChars(model_path, nullptr);
    const char* temp_runtime_path = env->GetStringUTFChars(runtime_path, nullptr);
    const char* temp_input_list_path = env->GetStringUTFChars(input_list_path, nullptr);
    // Set global variables
    g_working_dir = temp_working_dir;
    g_model_path = g_working_dir + "/" + temp_model_path;
    g_backend_path = g_working_dir + "/" + temp_runtime_path;
    g_input_list_path = g_working_dir + "/" + temp_input_list_path;
    // Release allocated memory for C-style char arrays
    env->ReleaseStringUTFChars(working_dir, temp_working_dir);
    env->ReleaseStringUTFChars(model_path, temp_model_path);
    env->ReleaseStringUTFChars(runtime_path, temp_runtime_path);
    env->ReleaseStringUTFChars(input_list_path, temp_input_list_path);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_qnnresnet_MainActivity_qnnLoadLibsAndCreateApp([[maybe_unused]] JNIEnv* env, jobject /* this */) {
    using namespace qnn::tools;

    if (!qnn::log::initializeLogging()) {
        std::cerr << "ERROR: Unable to initialize logging!\n";
        return EXIT_FAILURE;
    }
    // Load backend and model .so and validate all the required function symbols are resolved
    sample_app::QnnFunctionPointers qnn_func_ptrs;
    auto status_code = dynamicloadutil::getQnnFunctionPointers(g_backend_path, g_model_path,
                                                              &qnn_func_ptrs, &sg_backend_handle,
                                                              true, &sg_model_handle);
    if (status_code!= dynamicloadutil::StatusCode::SUCCESS) {
        if (status_code == dynamicloadutil::StatusCode::FAIL_LOAD_BACKEND)
            sample_app::exitWithMessage("Error initializing QNN Function Pointers: could not load backend: " + g_backend_path,
                                        EXIT_FAILURE);
        else if (status_code == dynamicloadutil::StatusCode::FAIL_LOAD_MODEL)
            sample_app::exitWithMessage("Error initializing QNN Function Pointers: could not load model: " + g_model_path,
                                        EXIT_FAILURE);
        else
            sample_app::exitWithMessage("Error initializing QNN Function Pointers",
                                        EXIT_FAILURE);
        // Avoid exit as it is a library, modified exitWithMessage (removed exit(code))
        return EXIT_FAILURE;
    }
    auto parsed_out_type = iotensor::OutputDataType::FLOAT_ONLY;
    auto parsed_in_type = iotensor::InputDataType::FLOAT;
    auto parsed_profile_level = sample_app::ProfilingLevel::DETAILED;
    app = std::make_unique<app_t> (qnn_func_ptrs, g_input_list_path, "",
                                   sg_backend_handle,"", true, parsed_out_type,
                                   parsed_in_type, parsed_profile_level,true,
                                   "", "");
    if (app == nullptr)
        return EXIT_FAILURE;

    return EXIT_SUCCESS;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_qnnresnet_MainActivity_qnnInitialize([[maybe_unused]] JNIEnv* env, jobject /* this */) {
    // Initialize QnnSampleApp - Creates output directory; Reads all input paths provided during creation.
    if (!is_qnn_success(app->initialize()))
        return app->reportError("Initialization failure");
    // Initialize backend provided during creation.
    if (!is_qnn_success(app->initializeBackend()))
        return app->reportError("Backend Initialization failure");
    // Get device based on provided runtime backend, if available.
    device_property_support_status = app->isDevicePropertySupported();
    if (device_property_support_status != appFAILURE && app->createDevice() != appSUCCESS)
        return app->reportError("Device Creation failure");
    // Profiling options - off | basic | detailed - based on what was provided during creation.
    if (!is_qnn_success(app->initializeProfiling()))
        return app->reportError("Profiling Initialization failure");
    // Create a Context in a backend.
    if (!is_qnn_success(app->createContext()))
        return app->reportError("Context Creation failure");
    LOG_D("Context created\n");
    // Calls QnnModel_composeGraphs function from the resnet.so used.
    // composeGraphs is supposed to populate graph related information in m_graphsInfo and m_graphsCount.
    // m_debug (??) is the option supplied to composeGraphs to say that all intermediate tensors are to be read.
    // ??? Should we disable m_debug? Is it enabled by default??
    if (!is_qnn_success(app->composeGraphs()))
        return app->reportError("Graph Compose failure");
    LOG_D("Graph composed\n");
    // Prepare graph in backend
    if (!is_qnn_success(app->finalizeGraphs()))
        return app->reportError("Graph Finalize failure");
    LOG_D("Graph finalized\n");

    // SHIFT BELOW STUFF TO EXECUTE Function & accept change in inputlist somehow

    // // execute graph in backend
    if (!is_qnn_success(app->executeGraphs()))
        return app->reportError("Graph Execution failure");
    LOG_D("Graph Execution successful\n");

    // Free context after done.
    if (!is_qnn_success(app->freeContext()))
        return app->reportError("Context Free failure");

    if (device_property_support_status != appFAILURE && app->freeDevice() != appSUCCESS)
        return app->reportError("Device Free failure");

    return EXIT_SUCCESS;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_qnnresnet_MainActivity_qnnExecute([[maybe_unused]] JNIEnv* env, jobject /* this */) {
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_qnnresnet_MainActivity_getOutput([[maybe_unused]] JNIEnv* env, jobject /* this */) {
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_qnnresnet_MainActivity_Close([[maybe_unused]] JNIEnv* env, jobject /* this */) {
    if (sg_backend_handle)
        pal::dynamicloading::dlClose(sg_backend_handle);
    if (sg_model_handle)
        pal::dynamicloading::dlClose(sg_model_handle);
    return 0;
}