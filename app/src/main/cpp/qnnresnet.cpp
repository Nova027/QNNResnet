#include <jni.h>
#include "BuildId.hpp"
#include "DynamicLoadUtil.hpp"
#include "Logger.hpp"
#include "PAL/DynamicLoading.hpp"
#include "PAL/GetOpt.hpp"
#include "QnnSampleApp.hpp"
#include "QnnSampleAppUtils.hpp"
#include "qnnresnet.h"

// -------------------------------- Global types & variables ----------------------------------
using app_t = qnn::tools::sample_app::QnnSampleApp;
using app_ptr_t = std::unique_ptr<app_t>;
using qnn_status_t = qnn::tools::sample_app::StatusCode;

app_ptr_t app;
qnn_status_t device_property_support_status;

static void* sg_backend_handle = nullptr;
static void* sg_model_handle = nullptr;

static const qnn_status_t appSUCCESS = qnn_status_t::SUCCESS;
static const qnn_status_t appFAILURE = qnn_status_t::FAILURE;

// ------------------------------------ JNI functions ------------------------------------------

extern "C" JNIEXPORT void JNICALL
Java_com_example_qnnresnet_MainActivity_setPaths(JNIEnv* env, jobject /* this */, jstring working_dir,
                      jstring model_path, jstring runtime_path, jstring input_list_path) {
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
    g_output_logits_path = g_working_dir + "/" + "Result_0/class_logits.raw";   // Hardcoding output path for now partly
    g_output_label_list_path = g_working_dir + "/" + "imagenet-classes.txt";    // Hardcoding classnames-list path for now partly
    // Release allocated memory for C-style char arrays
    env->ReleaseStringUTFChars(working_dir, temp_working_dir);
    env->ReleaseStringUTFChars(model_path, temp_model_path);
    env->ReleaseStringUTFChars(runtime_path, temp_runtime_path);
    env->ReleaseStringUTFChars(input_list_path, temp_input_list_path);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_qnnresnet_MainActivity_setLabels([[maybe_unused]] JNIEnv* env, jobject /* this */) {
    using namespace std;
    if (!path_status(g_output_label_list_path).empty())
        return EXIT_FAILURE;
    ifstream label_filestream(g_output_label_list_path);        // Automatically closes file when local scope ends
    if (!label_filestream.is_open())
        return EXIT_FAILURE;
    LOG_D("Label file opened\n");
    string line;
    for (auto& out_label : g_out_labels) {
        if (!getline(label_filestream,line)) {
            LOG_D("All labels not found in file\n");
            return EXIT_FAILURE;
        }
        out_label = line.substr(0, line.find(','));     // Only keep 1st word of label
    }
    if (getline(label_filestream,line)) {
        LOG_D("More labels than expected\n");
        return EXIT_FAILURE;
    }
    return EXIT_SUCCESS;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_qnnresnet_MainActivity_qnnLoadLibsAndCreateApp([[maybe_unused]] JNIEnv* env, jobject /* this */) {
    using namespace qnn::tools;

    if (setenv("ADSP_LIBRARY_PATH",g_working_dir.c_str(),0) != 0 || getenv("ADSP_LIBRARY_PATH") != g_working_dir) {
        LOG_D("Failed to set ADSP_LIBRARY_PATH env variable\n");
        return EXIT_FAILURE;
    }
    if (setenv("LD_LIBRARY_PATH",g_working_dir.c_str(),1) != 0 || getenv("LD_LIBRARY_PATH") != g_working_dir) {
        LOG_D("Failed to set LD_LIBRARY_PATH env variable\n");
        return EXIT_FAILURE;
    }

    if (!qnn::log::initializeLogging()) {
        LOG_D("ERROR: Unable to initialize logging!\n");
        return EXIT_FAILURE;
    }
    // Load backend and model .so and validate all the required function symbols are resolved
    sample_app::QnnFunctionPointers qnn_func_ptrs;
    auto status_code = dynamicloadutil::getQnnFunctionPointers(g_backend_path, g_model_path,
                                                              &qnn_func_ptrs, &sg_backend_handle,
                                                              true, &sg_model_handle);
    if (status_code != dynamicloadutil::StatusCode::SUCCESS) {
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
    auto parsed_in_type = iotensor::InputDataType::NATIVE;
    auto parsed_profile_level = sample_app::ProfilingLevel::OFF;
    app = std::make_unique<app_t> (qnn_func_ptrs, g_input_list_path, "",
                                   sg_backend_handle,g_working_dir, false, parsed_out_type,
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
    if (!is_qnn_success(app->initializeBackend())) {
        app->reportError("Backend Initialization failure");
        return 696969;
    }
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

    return EXIT_SUCCESS;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_qnnresnet_MainActivity_qnnExecute([[maybe_unused]] JNIEnv* env, jobject /* this */) {
    // Accept change in input list somehow

    // Execute graph in backend
    if (!is_qnn_success(app->executeGraphs()))
        return app->reportError("Graph Execution failure");
    LOG_D("Graph Execution successful\n");

    return EXIT_SUCCESS;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_qnnresnet_MainActivity_qnnGetOutput(JNIEnv* env, jobject /* this */) {
    // if (sizeof(float) != sizeof(uint32_t)) -- clang tells me it's always false
    std::string file_status = path_status(g_output_logits_path, true,
                                          NUM_CLASSES * sizeof(float));
    if (!file_status.empty())
        return env->NewStringUTF(file_status.c_str());
    // Automatically closes file when local scope ends
    std::ifstream logit_filestream(g_output_logits_path, std::ios::binary);
    if (!logit_filestream.is_open())
        return env->NewStringUTF("Could not open logits output file");
    LOG_D("Logits file opened\n");

    std::vector<float> logits(NUM_CLASSES, -1.0f);
    logit_filestream.read(reinterpret_cast<char*>(logits.data()), NUM_CLASSES * sizeof(float));
    size_t max_probable_class = std::max_element(logits.begin(), logits.end()) - logits.begin();

    if (max_probable_class >= 0 && max_probable_class < NUM_CLASSES)
        return env->NewStringUTF(g_out_labels[max_probable_class].c_str());
    return env->NewStringUTF("Oh no! Invalid output Llama!");
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_qnnresnet_MainActivity_qnnClose([[maybe_unused]] JNIEnv* env, jobject /* this */) {
    // Free graphs (?)
    if (!is_qnn_success(app->freeGraphs()))
        return app->reportError("Graph Free failure");

    // Free context after done.
    if (!is_qnn_success(app->freeContext()))
        return app->reportError("Context Free failure");

    if (device_property_support_status != appFAILURE && app->freeDevice() != appSUCCESS)
        return app->reportError("Device Free failure");

    if (sg_backend_handle)
        pal::dynamicloading::dlClose(sg_backend_handle);
    if (sg_model_handle)
        pal::dynamicloading::dlClose(sg_model_handle);

    return EXIT_SUCCESS;
}