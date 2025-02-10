package com.example.qnnresnet

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory.decodeByteArray
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import com.example.qnnresnet.ui.theme.QNNResnetTheme
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    // ----------------------- Static init and Member variables ---------------------
    companion object {
        // const val MODEL_NAME = "resnet50-snapdragon_8_gen_2.so"
        const val MODEL_NAME = "resnet50_quantized-snapdragon_8_gen_2.so"
        const val CPU_BACKEND_NAME = "libQnnCpu.so"
        // const val GPU_BACKEND_NAME = "libQnnGpu.so"
        // const val HTP_BACKEND_NAME = "libQnnHtp.so"
        const val INPUT_RAW_NAME = "resnet_uint8.raw"
        const val INPUT_LIST_NAME = "input_list.txt"
        const val LABEL_LIST_NAME = "imagenet-classes.txt"

        const val INPUT_HEIGHT = 224
        const val INPUT_WIDTH = 224

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
        val showCamera = remember { mutableStateOf(false) }
        val hasCameraPermission = remember { mutableStateOf(checkPermission(this)) }
        val requestPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { granted ->
                hasCameraPermission.value = granted
                if (granted) showCamera.value = true
            }
        )

        QNNResnetTheme {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPad ->
                Column(modifier = Modifier.fillMaxSize().padding(innerPad).padding(16.dp))
                {
                    when (retJNI) {
                        696969 -> Text("Error initializing QNN backend")
                        0 -> Text("QNN app initialized")
                        else -> Text("Error initializing QNN app")
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    ExecutionUI()

                    if (showCamera.value) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Box(contentAlignment = Alignment.Center,
                            modifier = Modifier.height(400.dp).aspectRatio(3f / 4f))
                        {
                            if (hasCameraPermission.value)
                                CameraUI()
                            else
                                Text("Grant camera access", color = Color.White,
                                    modifier = Modifier.fillMaxSize().background(Color.Black))
                        }
                    }
                    else if (hasCameraPermission.value)     // If showCamera is false, but this is true => Disabled camera with button, so must turn off cam
                        CameraUI(enableCamera = false)

                    Spacer(modifier = Modifier.weight(1f))

                    // Camera on/off button
                    Button(onClick = {
                        showCamera.value = !showCamera.value
                        if (showCamera.value && !hasCameraPermission.value)
                            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }) {
                        Text(if (showCamera.value) "Disable camera" else "Enable camera")
                    }
                    Spacer(modifier = Modifier.height(20.dp))
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
        Row(modifier = Modifier.height(60.dp))
        {
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
    fun CameraUI(enableCamera: Boolean = true) {
        val context = LocalContext.current
        val cameraController = remember {
            LifecycleCameraController(context).apply {
                setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            }
        }
        val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

        LaunchedEffect(enableCamera) {
            if (enableCamera)
                cameraController.bindToLifecycle(context as ComponentActivity)
            else
                cameraController.unbind()
        }

        if (enableCamera) {
            CameraPreview(cameraController)
            Column(verticalArrangement = Arrangement.Bottom, modifier = Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.height(100.dp).fillMaxWidth())
                {
                    CameraButtonRow(cameraController, cameraExecutor)
                }
            }
        }
    }

    @Composable
    fun CameraPreview(cameraController: CameraController) {
        AndroidView(factory = { context ->    // Since we are in MainActivity, the context passed to this lambda is LocalContext.current
                PreviewView(context).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER // Choose your preferred scale type
                    layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    controller = cameraController
                }
            } )
    }

    @Composable
    fun CameraButtonRow(cameraController: LifecycleCameraController, cameraExecutor: ExecutorService) {
        val context = this@MainActivity

        Button(shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(Color(0x77FFFFFF)),
            onClick = {
                cameraController.takePicture(
                    cameraExecutor,
                    object : ImageCapture.OnImageCapturedCallback() {        // In-place class definition & object creation
                        override fun onCaptureSuccess(image: ImageProxy) {
                            saveJPGToResizedRGB(context, image)
                            runOnUiThread {
                                Toast.makeText(context, "Input raw file updated", Toast.LENGTH_SHORT).show()
                            }
                            image.close()
                        }
                        override fun onError(exception: ImageCaptureException) {
                            runOnUiThread {
                                Toast.makeText(context, "Capture Failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        ) { }

        Button(shape = RoundedCornerShape(100),
            colors = ButtonDefaults.buttonColors(Color(0x77FFFFFF)),
            onClick = { }
        ) { }
    }

    // ------------------------- Private member functions ----------------------------

    private fun checkPermission(context: Context) : Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
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

    private fun saveJPGToResizedRGB(context: Context, image: ImageProxy, targetWidth: Int = INPUT_WIDTH, targetHeight: Int = INPUT_HEIGHT) {
        // Decode JPEG imageproxy to bitmap, and resize to target size
        val bytes = ByteArray(image.planes[0].buffer.capacity())
        image.planes[0].buffer.get(bytes)
        val bitmap = decodeByteArray(bytes,0, bytes.size)
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight,true)
        // Copy RGB data from bitmap to buffer
        val rgbBuffer = ByteArray(targetWidth * targetHeight * 3)
        var rgbIndex = 0
        for (y in 0 until targetHeight) {
            for (x in 0 until targetWidth) {
                val pixel = resizedBitmap.getPixel(x, y)
                rgbBuffer[rgbIndex++] = pixel.red.toByte()
                rgbBuffer[rgbIndex++] = pixel.green.toByte()
                rgbBuffer[rgbIndex++] = pixel.blue.toByte()
            }
        }
        // Write buffer contents to raw file
        val rawFile = File(context.filesDir, INPUT_RAW_NAME)
        rawFile.outputStream().use {                        // Auto open/close stream
            it.write(rgbBuffer)
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