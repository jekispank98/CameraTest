package com.jmtgroup.cameratest.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.jmtgroup.cameratest.R
import com.jmtgroup.cameratest.databinding.FragmentSplashBinding

class SplashFragment: Fragment() {
    private var binding: FragmentSplashBinding? = null

    companion object {
        private var PERMISSIONS_REQUIRED = arrayOf(
            Manifest.permission.CAMERA,
//            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        fun hasPermissions(context: Context) = PERMISSIONS_REQUIRED.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        checkPermissions()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val permissionList = PERMISSIONS_REQUIRED.toMutableList()
            permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            PERMISSIONS_REQUIRED = permissionList.toTypedArray()
        }
        if (!hasPermissions(requireContext())) {
            activityResultLauncher.launch(PERMISSIONS_REQUIRED)
        } else { navigateToCamera() }
    }


    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in PERMISSIONS_REQUIRED && !it.value) permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(context, getString(R.string.permission_request_denied), Toast.LENGTH_SHORT).show()
            } else { navigateToCamera() }
        }

    private fun navigateToCamera() {
        findNavController().navigate(R.id.action_splashFragment_to_cameraFragment)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}