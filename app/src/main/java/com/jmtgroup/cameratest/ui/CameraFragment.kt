package com.jmtgroup.cameratest.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.jmtgroup.cameratest.R
import com.jmtgroup.cameratest.databinding.FragmentCameraBinding
import com.jmtgroup.cameratest.presentation.MediaStoreUtil
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment() {
    private var binding: FragmentCameraBinding? = null
    private var currentPhotoPath: String? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var imageCapture: ImageCapture? = null
    private var cameraExecutor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var rotation: Int? = null
    private var displayId: Int = -1
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private lateinit var windowManager: WindowManager

    private lateinit var broadcastManager: LocalBroadcastManager

    private lateinit var mediaStoreUtils: MediaStoreUtil

    private val displayManager by lazy {
        requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) = view?.let { view ->
            if (displayId == this@CameraFragment.displayId) {
                imageCapture?.targetRotation = view.display.rotation
                imageAnalyzer?.targetRotation = view.display.rotation
            }
        } ?: Unit
    }

    companion object {
        const val KEY_EVENT_ACTION = "key_event_action"
        const val KEY_EVENT_EXTRA = "key_event_extra"
        private const val IMMERSIVE_FLAG_TIMEOUT = 500L
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configureCameraController()
        configureCamera()
        initCameraAdditional()
        startCamera()
//        initExecutor()
//        initImageCapture()
        setListeners()
    }

    private fun configureCamera() {
        cameraProvider = ProcessCameraProvider.getInstance(requireContext()).get()
    }

    private fun initCameraAdditional() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        broadcastManager = LocalBroadcastManager.getInstance(requireContext())
        val filter = IntentFilter().apply { addAction(KEY_EVENT_ACTION) }
        displayManager.registerDisplayListener(displayListener, null)
        mediaStoreUtils = MediaStoreUtil(requireContext())
    }

    private fun startCamera() {

            val cameraProvider = cameraProvider
                ?: throw IllegalStateException("Camera initialization failed.")

            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            preview = Preview.Builder()
                .build()

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            imageAnalyzer = ImageAnalysis.Builder()
                .build()

            cameraProvider.unbindAll()

            if (camera != null) {
                removeCameraStateObservers(camera!!.cameraInfo)
            }

            try {
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer)

                preview?.setSurfaceProvider(binding?.cameraPreview?.surfaceProvider)
                observeCameraState(camera?.cameraInfo!!)
            } catch (exc: Exception) {
                Log.e("Camera error", "Use case binding failed", exc)
            }
        }
    private fun removeCameraStateObservers(cameraInfo: CameraInfo) {
        cameraInfo.cameraState.removeObservers(viewLifecycleOwner)
    }

    private fun observeCameraState(cameraInfo: CameraInfo) {
        cameraInfo.cameraState.observe(viewLifecycleOwner) { cameraState ->
            run {
                when (cameraState.type) {
                    CameraState.Type.PENDING_OPEN -> {
                        // Ask the user to close other camera apps
                        Toast.makeText(context,
                            "CameraState: Pending Open",
                            Toast.LENGTH_SHORT).show()
                    }
                    CameraState.Type.OPENING -> {
                        // Show the Camera UI
                        Toast.makeText(context,
                            "CameraState: Opening",
                            Toast.LENGTH_SHORT).show()
                    }
                    CameraState.Type.OPEN -> {
                        // Setup Camera resources and begin processing
                        Toast.makeText(context,
                            "CameraState: Open",
                            Toast.LENGTH_SHORT).show()
                    }
                    CameraState.Type.CLOSING -> {
                        // Close camera UI
                        Toast.makeText(context,
                            "CameraState: Closing",
                            Toast.LENGTH_SHORT).show()
                    }
                    CameraState.Type.CLOSED -> {
                        // Free camera resources
                        Toast.makeText(context,
                            "CameraState: Closed",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }

            cameraState.error?.let { error ->
                when (error.code) {
                    // Open errors
                    CameraState.ERROR_STREAM_CONFIG -> {
                        // Make sure to setup the use cases properly
                        Toast.makeText(context,
                            "Stream config error",
                            Toast.LENGTH_SHORT).show()
                    }
                    // Opening errors
                    CameraState.ERROR_CAMERA_IN_USE -> {
                        // Close the camera or ask user to close another camera app that's using the
                        // camera
                        Toast.makeText(context,
                            "Camera in use",
                            Toast.LENGTH_SHORT).show()
                    }
                    CameraState.ERROR_MAX_CAMERAS_IN_USE -> {
                        // Close another open camera in the app, or ask the user to close another
                        // camera app that's using the camera
                        Toast.makeText(context,
                            "Max cameras in use",
                            Toast.LENGTH_SHORT).show()
                    }
                    CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> {
                        Toast.makeText(context,
                            "Other recoverable error",
                            Toast.LENGTH_SHORT).show()
                    }
                    // Closing errors
                    CameraState.ERROR_CAMERA_DISABLED -> {
                        // Ask the user to enable the device's cameras
                        Toast.makeText(context,
                            "Camera disabled",
                            Toast.LENGTH_SHORT).show()
                    }
                    CameraState.ERROR_CAMERA_FATAL_ERROR -> {
                        // Ask the user to reboot the device to restore camera function
                        Toast.makeText(context,
                            "Fatal error",
                            Toast.LENGTH_SHORT).show()
                    }
                    // Closed errors
                    CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> {
                        // Ask the user to disable the "Do Not Disturb" mode, then reopen the camera
                        Toast.makeText(context,
                            "Do not disturb mode enabled",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }


    private fun configureCameraController() {
        binding?.let {
            val previewView: PreviewView = binding!!.cameraPreview
            val cameraController = LifecycleCameraController(requireContext())
            cameraController.bindToLifecycle(this)
            cameraController.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            previewView.controller = cameraController
        }
    }

    private fun initExecutor() {
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun initImageCapture() {
        rotation = binding?.cameraPreview?.display?.rotation
        imageCapture =
            ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
//            .setTargetRotation(it)
            .build()
    }

    private fun setListeners() {
        binding?.cameraCaptureButton?.setOnClickListener {
            takePhoto()
        }
    }

    private fun takePhoto() {
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(createImageFile()).build()
        cameraExecutor?.let { executor ->
            imageCapture?.takePicture(outputFileOptions, executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(error: ImageCaptureException) {
                        Log.d("Photo error", "Saved photo is error: ${error.message}")

                    }
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        Log.d("check_photo", "Saved photo is started")
                        val savedUri = outputFileResults.savedUri
                        Log.d("check_photo", "Photo capture succeeded: $savedUri")

                        // We can only change the foreground Drawable using API level 23+ API
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            // Update the gallery thumbnail with latest picture taken
                            setGalleryThumbnail(savedUri.toString())
                        }

                        // Implicit broadcasts will be ignored for devices running API level >= 24
                        // so if you only target API level 24+ you can remove this statement
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                            // Suppress deprecated Camera usage needed for API level 23 and below
                            @Suppress("DEPRECATION")
                            requireActivity().sendBroadcast(
                                Intent(android.hardware.Camera.ACTION_NEW_PICTURE, savedUri)
                            )
                        }
                    }
                })
        }
    }
    private fun setGalleryThumbnail(filename: String) {
        // Run the operations in the view's thread
        binding?.cameraCaptureButton?.let { photoViewButton ->
            photoViewButton.post {
                // Remove thumbnail padding
                photoViewButton.setPadding(resources.getDimension(R.dimen.size_medium).toInt())

                // Load thumbnail into circular button using Glide
                Glide.with(photoViewButton)
                    .load(filename)
                    .apply(RequestOptions.circleCropTransform())
                    .into(photoViewButton)
            }
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File =
            requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
        cameraExecutor?.shutdown()
        displayManager.unregisterDisplayListener(displayListener)
    }
}