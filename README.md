# QNNResnet

QNNResnet is an Android project designed to perform some standard QNN-backed image classification on Snapdragon chipsets, in this case, specifically using the Quantized Resnet50 model from Qualcomm AI Hub.

## Table of Contents
- [About](#about)
- [Prerequisites](#prerequisites)
- [Usage](#usage)

## About
This project was created to to test and deploy various QNN models from Qualcomm AI Hub on Snapdragon chipsets (CPU/GPU/DSP). We verified floating-point standard Resnet initially, but switched to quantized model with quantized raw inputs for performance concerns.

The "Sample app" source code used in the native JNI (cpp) layer of the app, is picked from the Qualcomm AI Engine direct SDK (aka, QNN SDK) examples (QAIRT 2.26). The qnnresnet.cpp (main lib located in cpp root folder), is written by taking the main function / main src file from the SDK's sample app as reference.

### Features
- Decently fast performance with quantized Resnet50 model running on CPU.
- Future versions will support GPU and HTP backend, for significant performance improvements.
- App includes live camera feed, which allows user to capture an image to run the model on. With HTP enabled, can make this a more real-time processing UI.

## Prerequisites
- Android Studio or equivalent, with Android SDK 35+, and equivalent NDK.
- [QNN SDK](https://www.qualcomm.com/developer/software/qualcomm-ai-engine-direct-sdk) (v2.26+).
- Android device with snapdragon chipset, to test the app.

## Usage
On building, packaging into apk, and installing the apk, you will get an app named "QNN Resnet", which has a 
### Steps to build
1. Resolve all necessary prerequisite setups/installations - Android studio with SDK and NDK, QNN SDK.
2. Clone the repository:
   ```bash
   git clone https://github.com/Nova027/QNNResnet.git
3. Open the cloned folder in Android studio, and gradle sync the dependencies.
4. Open the root CMakeLists.txt file in the native code (app/src/main/cpp/) and edit the appropriate path name for your QNN SDK root location, use forward slashes only in pathname. Now, you can build the project, but it won't run without the model and runtime shared libraries.
5. Copy libQnnCpu.so from QNN SDK libs to assets folder (app/src/main/assets) folder, required for CPU backend.
6. Donwload [Quantized Resnet50](https://aihub.qualcomm.com/mobile/models/resnet50_quantized) QNN model from Qualcomm AI-hub, select runtime as "Qualcomm AI Engine direct" and Device as "8 Gen 2, Galaxy S23" from the Download model options (otherwise file renaming would be needed); and copy it to assets (without renaming).
7. Build and deploy the apk on your Snapdragon device of choice (preferably newer flagship android phones for high chance of support).
8. Run it, there's a default image (input raw) present initially, which is randomly generated. To use a different image, use the built-in camera capture feature (left button in camera preview UI) to update the "input raw". Other buttons in the app are self-explanatory.
