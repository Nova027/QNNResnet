QNNResnet is an android project, which can be opened and built via Android studio (or other equivalent android projects). On building, packaging, and installing the package, you get an app named "QNN Resnet". Android SDK 35+ (with its corresponding NDK) is required to build the project.

This project was made to test out and deploy various QNN-based image classification models from Qualcomm AI Hub, on snapdragon chipsets (CPU/GPU/DSP).

Currently, it works decently fast with a quantized Resnet50 model running on CPU, but will add future versions to support it on HTP backend as well.

The "Sample app" source code used in the native JNI (cpp) layer of the app, is picked from the Qualcomm AI Stack SDK (aka QNN SDK) examples (QAIRT 2.26). The qnnresnet.cpp (main lib), is written by taking the main function / main src file from the SDK's sample app, as reference.

To build the app, required assets -
- Copy libQnnCpu.so from QNN SDK to assets (app/src/main/assets) folder, for CPU backend.
- Donwload quantized resnet50 QNN model from Qualcomm AI-hub, and copy it to assets (without renaming).
