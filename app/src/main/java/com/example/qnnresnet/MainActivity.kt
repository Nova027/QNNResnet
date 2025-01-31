package com.example.qnnresnet

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.unit.dp
import com.example.qnnresnet.ui.theme.QNNResnetTheme
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : ComponentActivity() {
    companion object {
        const val MODEL_NAME = "resnet50-snapdragon_8_gen_2.so"
        const val CPU_BACKEND_NAME = "libQnnCpu.so"
        const val INPUT_RAW_NAME = "resnet_f32.raw"
        const val INPUT_LIST_NAME = "input_list.txt"
        init {
            System.loadLibrary("qnnresnet")
        }
    }

    private external fun stringFromJNI(): String

    private external fun setPaths(workingDir: String, modelPath: String,
                                  backendPath: String, inputListPath: String)

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
        createInputLists(this)
        copyAssetToInternalStorage(this, MODEL_NAME)
        copyAssetToInternalStorage(this, CPU_BACKEND_NAME)
        copyAssetToInternalStorage(this, INPUT_RAW_NAME)
        setPaths(this.filesDir.absolutePath, MODEL_NAME, CPU_BACKEND_NAME, INPUT_LIST_NAME)
        // Add a native func to determine runtime type
        var ret = qnnLoadLibsAndCreateApp()
        Log.d("Resnet", "qnnLoadLibsAndCreateApp returned $ret")
        // Use ret
        ret = qnnInitialize()
        Log.d("Resnet", "qnnInit returned $ret")
        // Use ret
        setContent {
            QNNResnetTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding))
                    {
                        val changeText = remember { mutableStateOf(false) }
                        val predictedOutput = remember { mutableStateOf("") }
                        val ranAlready = remember { mutableStateOf(false) }      // temp, to remove when changing input added!!
                        // Header
                        Greeting(stringFromJNI())
                        Spacer(modifier = Modifier.height(20.dp))
                        // Button row
                        Row {
                            Button(onClick = {
                                val ret2: Int
                                if (!ranAlready.value) {
                                    ret2 = qnnExecute()
                                    Log.d("Resnet", "qnnExec returned $ret2")
                                    predictedOutput.value = "Ran to find output : "
                                }
                                else
                                    predictedOutput.value = ""
                                // Use ret2, get output only if success
                                predictedOutput.value += qnnGetOutput()
                                changeText.value = true
                            }) {
                                Text("Run")
                            }
                            Spacer(modifier = Modifier.width(20.dp))
                            Button(onClick = {
                                predictedOutput.value = ""
                                changeText.value = true
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
                        if (changeText.value) {
                            Text(predictedOutput.value)
                            changeText.value = false
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val ret = qnnClose()
        Log.d("Resnet", "qnnClose returned $ret")
        // Use ret??
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