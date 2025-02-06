#ifndef QNN_RESNET_QNNRESNET_H
#define QNN_RESNET_QNNRESNET_H

#include <android/log.h>
#include <filesystem>
#include <fstream>
#include <cstdlib>
#include <iostream>
#include <memory>
#include <string>

#define LOG_D(...) __android_log_print(ANDROID_LOG_DEBUG, "QNNResnet", __VA_ARGS__)

#define NUM_CLASSES 1000

#define is_qnn_success(status) (status == appSUCCESS)

std::string g_working_dir;
std::string g_model_path;
std::string g_backend_path;
std::string g_input_list_path;
std::string g_output_logits_path;
std::string g_output_label_list_path;

std::string g_out_labels[NUM_CLASSES];

std::string path_status(std::string& filename, bool check_size = false, int size = 0) {
    using namespace std::filesystem;
    path out_path(filename);
    if (!exists(out_path)) return "Output not found";
    if (!is_regular_file(out_path)) return "Output not in readable file format";
    if (check_size && file_size(out_path) != size) return "Output not in expected format (size mismatch)";
    return "";
}

#endif //QNN_RESNET_QNNRESNET_H
