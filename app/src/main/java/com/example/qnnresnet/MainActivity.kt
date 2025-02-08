package com.example.qnnresnet

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.qnnresnet.ui.theme.QNNResnetTheme
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : ComponentActivity() {
    // ----------------------- Static init and Member variables ---------------------
    companion object {
        const val MODEL_NAME = "resnet50-snapdragon_8_gen_2.so"
        const val CPU_BACKEND_NAME = "libQnnCpu.so"
        // const val GPU_BACKEND_NAME = "libQnnGpu.so"
        // const val HTP_BACKEND_NAME = "libQnnHtp.so"
        const val INPUT_RAW_NAME = "resnet_f32.raw"
        const val INPUT_LIST_NAME = "input_list.txt"
        const val LABEL_LIST_NAME = "imagenet-classes.txt"

        const val NATIVE_SUCCESS = 0

        init {
            System.loadLibrary("qnnresnet")
        }
    }

    private var retJNI = NATIVE_SUCCESS

    // ----------------------- Activity-lifecycle management functions ---------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // val abi = Build.SUPPORTED_ABIS[0] Check if arch supports models and runtimes
        // Add returns to copyAsset?
        // Might change assets to jniLibs or vice versa.

        // Add a native func to determine runtime type
        val selectedBackend = CPU_BACKEND_NAME

        // if (selectedBackend == HTP_BACKEND_NAME) {
        //     copyAssetToInternalStorage(this, "libQnnHtpPrepare.so")
        //     copyAssetToInternalStorage(this, "libQnnHtpV73Skel.so")
        //     copyAssetToInternalStorage(this, "libQnnHtpV73Stub.so")
        // }
        copyAssetToInternalStorage(this, MODEL_NAME)
        copyAssetToInternalStorage(this, selectedBackend)
        copyAssetToInternalStorage(this, INPUT_RAW_NAME)
        copyAssetToInternalStorage(this, LABEL_LIST_NAME)
        createInputLists(this)

        setPaths(this.filesDir.absolutePath, MODEL_NAME, selectedBackend, INPUT_LIST_NAME)
        retJNI = setLabels()
        Log.d("QNNResnet", "setLabels returned $retJNI")

        retJNI = qnnLoadLibsAndCreateApp()
        Log.d("QNNResnet", "qnnLoadLibsAndCreateApp returned $retJNI")

        if (retJNI == NATIVE_SUCCESS) {
            retJNI = qnnInitialize()
            Log.d("QNNResnet", "qnnInit returned $retJNI")
        }

        setContent { HomeScreen() }
    }

    override fun onDestroy() {
        super.onDestroy()
        retJNI = qnnClose()
        Log.d("QNNResnet", "qnnClose returned $retJNI")
        // Use retJNI??
    }

    // ------------------------- Composable functions to show UI ----------------------------
    @Composable
    fun HomeScreen() {
        val hasCameraPermission = remember { mutableStateOf(checkPermission(this)) }
        val requestPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { granted -> hasCameraPermission.value = granted }
        )
        val showCamera = remember { mutableStateOf(false) }

        QNNResnetTheme {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPad ->
                Column(modifier = Modifier.padding(innerPad).padding(16.dp))
                {
                    when (retJNI) {
                        696969 -> Text("Error initializing QNN backend")
                        0 -> Text("QNN app initialized")
                        else -> Text("Error initializing QNN app")
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    ExecutionUI()

                    if (showCamera.value) {
                        Box(contentAlignment = Alignment.TopCenter,
                            modifier = Modifier.height(200.dp).width(150.dp).padding(80.dp))
                        {
                            if (hasCameraPermission.value)
                                CameraUI()
                            else
                                Text("Grant camera access", color = Color.White,
                                    modifier = Modifier.fillMaxSize().background(Color.Black))
                        }
                    }

                    Spacer(modifier = Modifier.fillMaxHeight())
                    // Check if layout and paddings are in correct order!

                    // Add camera enable button


                }
            }
        }
    }

    @Composable
    fun ExecutionUI() {
        val predictedOutput = remember { mutableStateOf("") }
        val ranAlready = remember { mutableStateOf(false) }      // temp, to remove when changing input added!!

        Spacer(modifier = Modifier.height(20.dp))
        // Button row
        Row {
            Button(onClick = {
                predictedOutput.value = "Result - "
                if (!ranAlready.value && retJNI == NATIVE_SUCCESS) {
                    val ret2 = qnnExecute()
                    predictedOutput.value += "Ran to find output $ret2 : "
                }
                // Use ret2, get output only if success
                predictedOutput.value += qnnGetOutput()
            }) {
                Text("Run")
            }
            Spacer(modifier = Modifier.width(20.dp))
            Button(onClick = {
                predictedOutput.value = ""
            }) {
                Text("Clear")
            }
            Spacer(modifier = Modifier.width(20.dp))
            Button(onClick = { ranAlready.value = true }) {
                Text("No more")
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        // Output
        Text(predictedOutput.value)
    }

    @Composable
    fun CameraUI() {
        val context = LocalContext.current
        val cameraController = remember {
            LifecycleCameraController(context).apply {
                setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        CameraPreview(modifier = Modifier.fillMaxHeight(), cameraController)
    }

    @Composable
    fun CameraPreview(modifier: Modifier = Modifier, cameraController: CameraController) {
        val context = LocalContext.current


        AndroidView(
            modifier = modifier,
            factory = { _ ->    // Since we are in MainActivity, the context passed to this lambda is LocalContext.current
                PreviewView(context).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER // Choose your preferred scale type
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    controller = cameraController
                }
            }
        )
    }

    // ------------------------- Private member functions ----------------------------

    private fun checkPermission(context: Context) : Boolean {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun createInputLists(context: Context) {
        val fileHandle = File(context.filesDir, INPUT_LIST_NAME)
        val writeString = "input:=" + context.filesDir.absolutePath + "/" + INPUT_RAW_NAME
        fileHandle.writeText(writeString)
    }

    private fun copyAssetToInternalStorage(context: Context, fileName: String) {
        val inputStream = context.assets.open(fileName)
        val outputFileHandle = File(context.filesDir, fileName)
        try {
            val outputStream = FileOutputStream(outputFileHandle)
            val buffer = ByteArray(1024)

            var readBytes = inputStream.read(buffer)
            while (readBytes != -1) {
                outputStream.write(buffer, 0, readBytes)
                readBytes = inputStream.read(buffer)
            }
            outputStream.flush()
            outputStream.close()
            inputStream.close()
        }
        catch (e: IOException) {
            Log.e("AssetCopy", "Failed to copy asset file: $fileName", e)
        }
    }

    // ------------------------- JNI Native function references ----------------------------
    private external fun setPaths(workingDir: String, modelPath: String,
                                  backendPath: String, inputListPath: String)

    private external fun setLabels() : Int

    private external fun qnnLoadLibsAndCreateApp() : Int

    private external fun qnnInitialize() : Int

    private external fun qnnExecute() : Int

    private external fun qnnGetOutput() : String

    private external fun qnnClose() : Int
}