package com.example.qnnresnet

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.qnnresnet.ui.theme.QNNResnetTheme
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : ComponentActivity() {
    companion object {
        const val MODEL_NAME = "resnet50-snapdragon_8_gen_2.so"
        const val CPU_BACKEND_NAME = "libQnnCpu.so"
        init {
            System.loadLibrary("qnnresnet")
        }
    }

    private external fun stringFromJNI(): String

    private external fun setPaths(workingDir: String, modelPath: String, backendPath: String)

    private external fun qnnLoadLibsAndCreateApp() : Int

    private external fun qnnInitialize() : Int

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // val abi = Build.SUPPORTED_ABIS[0] Check if arch supports models and runtimes
        setPaths(this.filesDir.absolutePath, MODEL_NAME, CPU_BACKEND_NAME)
        copyAssetToInternalStorage(this, MODEL_NAME)
        copyAssetToInternalStorage(this, CPU_BACKEND_NAME)
        var ret = qnnLoadLibsAndCreateApp()
        // Use ret
        ret = qnnInitialize()
        // Use ret
        // Add a native func to determine runtime type
        setContent {
            QNNResnetTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = stringFromJNI(),
                        modifier = Modifier.padding(innerPadding)
                    )
                    // Use exec function here.
                }
            }
        }
    }

    private fun copyAssetToInternalStorage(context: Context, fileName: String): String {
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

            return context.filesDir.absolutePath
        } catch (e: IOException) {
            Log.e("AssetCopy", "Failed to copy asset file: $fileName", e)
        }
        return ""
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