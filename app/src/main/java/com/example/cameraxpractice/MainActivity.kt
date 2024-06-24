package com.example.cameraxpractice

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.cameraxpractice.databinding.ActivityMainBinding
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

typealias LumaListener = (luma: Double) -> Unit

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    
    private var imageCapture: ImageCapture? = null
    
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    
    private lateinit var cameraExecutor: ExecutorService
    
    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        )
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    permissionGranted = false
            }
            //Loop through each permissions.entries and if any REQUIRED_PERMISSIONS are not granted, set permissionGranted to false.
            //If permissions are not granted, present a toast to notify the user that the permissions were not granted.
            if (!permissionGranted) {
                Toast.makeText(
                    baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                startCamera()
            }
        }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        
        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }
        
        // Set up the listeners for take photo and video capture buttons
        viewBinding.imageCaptureButton.setOnClickListener { takePhoto() }
        viewBinding.videoCaptureButton.setOnClickListener { captureVideo() }
        
        cameraExecutor = Executors.newSingleThreadExecutor()
    }
    
    private fun takePhoto() {
        // First, get a reference to the ImageCapture use case. If the use case is null, exit out of the function.
        // This will be null If we tap the photo button before image capture is set up. Without the return statement, the app would crash if it was null.
        val imageCapture = imageCapture ?: return
        
        // Next, create a MediaStore content value to hold the image.
        // Use a timestamp so the display name in MediaStore will be unique.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }
        
        // Create an OutputFileOptions object. This object is where we can specify things about how we want our output to be.
        // We want the output saved in the MediaStore so other apps could display it, so add our MediaStore entry.
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()
        
        // Call takePicture() on the imageCapture object. Pass in outputOptions, the executor,
        // and a callback for when the image is saved. You'll fill out the callback next.
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                // In the case that the image capture fails or saving the image capture fails,
                // add in an error case to log that it failed.
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }
                
                // If the capture doesn't fail, the photo was taken successfully!
                // Save the photo to the file we created earlier, present a toast to let the user know it was successful, and print a log statement.
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            }
        )
    }
    
    // Implements VideoCapture use case, including start and stop capturing.
    private fun captureVideo() {
        // Check if the VideoCapture use case has been created: if not, do nothing.
        val videoCapture = this.videoCapture ?: return
        
        // Disable the UI until the request action is completed by CameraX;
        // it is re-enabled inside our registered VideoRecordListener in later steps.
        viewBinding.videoCaptureButton.isEnabled = false
        
        // If there is an active recording in progress, stop it and release the current recording.
        // We will be notified when the captured video file is ready to be used by our application.
        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop()
            recording = null
            return
        }
        
        // To start recording, we create a new recording session.
        // First we create our intended MediaStore video content object,
        // with system timestamp as the display name(so we could capture multiple videos).
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            }
        }
        
        // Create a MediaStoreOutputOptions.Builder with the external content option.
        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            // Set the created video contentValues to the MediaStoreOutputOptions.Builder,
            // and build our MediaStoreOutputOptions instance.
            .setContentValues(contentValues)
            .build()
        
        // Configure the output option to the Recorder of VideoCapture<Recorder> and enable audio recording:
        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                // Enable Audio in this recording.
                if (PermissionChecker.checkSelfPermission(this@MainActivity,
                        Manifest.permission.RECORD_AUDIO) ==
                    PermissionChecker.PERMISSION_GRANTED)
                {
                    withAudioEnabled()
                }
            }
            
            // Start this new recording, and register a lambda VideoRecordEvent listener.
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        // When the request recording is started by the camera device,
                        // toggle the "Start Capture" button text to say "Stop Capture".
                        viewBinding.videoCaptureButton.apply {
                            text = getString(R.string.stop_capture)
                            isEnabled = true
                        }
                    }
                    is VideoRecordEvent.Finalize -> {
                        // When the active recording is complete, notify the user with a toast,
                        // and toggle the "Stop Capture" button back to "Start Capture", and re-enable it:
                        if (!recordEvent.hasError()) {
                            val msg = "Video capture succeeded: " +
                                    "${recordEvent.outputResults.outputUri}"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT)
                                .show()
                            Log.d(TAG, msg)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Video capture ends with error: " +
                                    "${recordEvent.error}")
                        }
                        viewBinding.videoCaptureButton.apply {
                            text = getString(R.string.start_capture)
                            isEnabled = true
                        }
                    }
                }
            }
    }
    
    private fun startCamera() {
        // Create an instance of the ProcessCameraProvider.
        // This is used to bind the lifecycle of cameras to the lifecycle owner.
        // This eliminates the task of opening and closing the camera since CameraX is lifecycle-aware.
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        // Add a listener to the cameraProviderFuture.
        // Add a Runnable as one argument.
        // We will fill it in later. Add ContextCompat.getMainExecutor() as the second argument.
        // This returns an Executor that runs on the main thread.
        cameraProviderFuture.addListener({
            // In the Runnable, add a ProcessCameraProvider.
            // This is used to bind the lifecycle of our camera to the LifecycleOwner within the application's process.
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            
            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
                        Log.d(TAG, "Average luminosity: $luma")
                    })
                }
            // Initialize our Preview object, call build on it,
            // get a surface provider from viewfinder, and then set it on the preview.
            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = viewBinding.viewFinder.surfaceProvider
                }
            imageCapture = ImageCapture.Builder().build()
            // Create a CameraSelector object and select DEFAULT_BACK_CAMERA.
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                // Create a try block. Inside that block, make sure nothing is bound to the cameraProvider,
                // and then bind our cameraSelector and preview object to the cameraProvider.
                cameraProvider.unbindAll()
                
                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview , imageCapture, imageAnalyzer
                )
                
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
            
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }
    
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
        // import android.Manifest
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
    
    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO // import android.Manifest
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE) // import android.Manifest
                }
            }.toTypedArray()
    }
    
    // Add this analyzer in as an inner class in MainActivity.kt. The analyzer logs the average luminosity of the image.
    // To create an analyzer, we override the analyze function in a class that implements the ImageAnalysis.Analyzer interface.
    // With our class implementing the ImageAnalysis.Analyzer interface,
    // all we need to do is instantiate an instance of LuminosityAnalyzer in the ImageAnalysis,
    // similar to other use cases, and update the startCamera() function once again,
    // before the call to CameraX.bindToLifecycle():
    private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {
        
        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }
        
        override fun analyze(image: ImageProxy) {
            
            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map { it.toInt() and 0xFF }
            val luma = pixels.average()
            
            listener(luma)
            
            image.close()
        }
    }
}