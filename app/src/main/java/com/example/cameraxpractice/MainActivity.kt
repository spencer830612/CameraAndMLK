package com.example.cameraxpractice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.cameraxpractice.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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
    
    private fun takePhoto() {}
    
    private fun captureVideo() {}
    
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
            
            // Initialize our Preview object, call build on it,
            // get a surface provider from viewfinder, and then set it on the preview.
            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = viewBinding.viewFinder.surfaceProvider
                }
            
            // Create a CameraSelector object and select DEFAULT_BACK_CAMERA.
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                // Create a try block. Inside that block, make sure nothing is bound to the cameraProvider,
                // and then bind our cameraSelector and preview object to the cameraProvider.
                cameraProvider.unbindAll()
                
                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview
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
}