package com.example.qnnresnet

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.qnnresnet.ui.theme.QNNResnetTheme
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : ComponentActivity() {
    companion object {
        const val MODEL_NAME = "resnet50-snapdragon_8_gen_2.so"
        const val CPU_BACKEND_NAME = "libQnnCpu.so"
        const val GPU_BACKEND_NAME = "libQnnGpu.so"
        const val HTP_BACKEND_NAME = "libQnnHtp.so"
        const val INPUT_RAW_NAME = "resnet_f32.raw"
        const val INPUT_LIST_NAME = "input_list.txt"
        const val LABEL_LIST_NAME = "imagenet-classes.txt"
        init {
            System.loadLibrary("qnnresnet")
        }
    }

    private external fun stringFromJNI(): String

    private external fun setPaths(workingDir: String, modelPath: String,
                                  backendPath: String, inputListPath: String)

    private external fun setLabels() : Int

    private external fun qnnLoadLibsAndCreateApp() : Int

    private external fun qnnInitialize() : Int

    private external fun qnnExecute() : Int

    private external fun qnnGetOutput() : String

    private external fun qnnClose() : Int

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // val abi = Build.SUPPORTED_ABIS[0] Check if arch supports models and runtimes
        // Add returns to copyAsset?
        // Might change assets to jniLibs or vice versa.
        val selectedBackend = GPU_BACKEND_NAME
        createInputLists(this)
        copyAssetToInternalStorage(this, MODEL_NAME)
        copyAssetToInternalStorage(this, selectedBackend)
        if (selectedBackend == HTP_BACKEND_NAME) {
            copyAssetToInternalStorage(this, "libQnnHtpPrepare.so")
            copyAssetToInternalStorage(this, "libQnnHtpV73Skel.so")
            copyAssetToInternalStorage(this, "libQnnHtpV73Stub.so")
        }
        copyAssetToInternalStorage(this, INPUT_RAW_NAME)
        copyAssetToInternalStorage(this, LABEL_LIST_NAME)
        setPaths(this.filesDir.absolutePath, MODEL_NAME, selectedBackend, INPUT_LIST_NAME)
        var ret = setLabels()
        Log.d("QNNResnet", "setLabels returned $ret")
        // Use ret
        // Add a native func to determine runtime type
        ret = qnnLoadLibsAndCreateApp()
        Log.d("QNNResnet", "qnnLoadLibsAndCreateApp returned $ret")
        // Use ret
        ret = qnnInitialize()
        Log.d("QNNResnet", "qnnInit returned $ret")
        // Use ret
        setContent {
            QNNResnetTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier
                        .padding(innerPadding)
                        .padding(16.dp))
                    {
                        if (ret == 696969)
                            Text("Error initializing QNN backend")
                        else if (ret != 0)
                            Text("Error initializing QNN app")
                        else
                            Text("QNN app initialized")

                        Spacer(modifier = Modifier.height(20.dp))
                        MainContentColumn()

                        Spacer(modifier = Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .height(150.dp)
                                .padding(100.dp)) {
                            CameraPreview(modifier = Modifier.fillMaxHeight(),
                                context = LocalContext.current)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val ret = qnnClose()
        Log.d("QNNResnet", "qnnClose returned $ret")
        // Use ret??
    }

    @Composable
    fun MainContentColumn() {
        val predictedOutput = remember { mutableStateOf("") }
        val ranAlready = remember { mutableStateOf(false) }      // temp, to remove when changing input added!!
        // Header
        Greeting(stringFromJNI())
        Spacer(modifier = Modifier.height(20.dp))
        // Button row
        Row {
            Button(onClick = {
                predictedOutput.value = "Result - "
                if (!ranAlready.value) {
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
    fun CameraPreview(modifier: Modifier = Modifier, context: Context) {
        val cameraController = remember { LifecycleCameraController(context) }
        cameraController.setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        AndroidView(
            modifier = modifier,
            factory = { _ ->    // Since we are in MainActivity, the passed context will be always LocalContext.current
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
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

/*
Open Android Studio and navigate to View > Tool Windows > Device File Explorer.
In the Device File Explorer, you can navigate to /data/data/<pkg_name> and browse the files and directories within your app's data directory.
You can pull files from the device to your local machine for inspection or push files from your local machine to the device.
 */