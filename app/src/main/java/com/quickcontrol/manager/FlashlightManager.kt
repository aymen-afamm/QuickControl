package com.quickcontrol.manager

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.util.Log

class FlashlightManager(context: Context) {

    companion object {
        private const val TAG = "FlashlightManager"
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val hasFlashlight = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)

    private var cameraId: String? = null
    private var isOn = false
    private var listener: ((Boolean) -> Unit)? = null

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(camId: String, enabled: Boolean) {
            if (camId == cameraId) {
                isOn = enabled
                listener?.invoke(enabled)
            }
        }
    }

    init {
        if (hasFlashlight) {
            try {
                cameraId = cameraManager.cameraIdList.firstOrNull()
                cameraManager.registerTorchCallback(torchCallback, null)
            } catch (e: CameraAccessException) {
                Log.e(TAG, "Failed to initialize flashlight", e)
            }
        }
    }

    fun isAvailable(): Boolean = hasFlashlight && cameraId != null

    fun isFlashlightOn(): Boolean = isOn

    fun toggle(): Boolean {
        if (!isAvailable()) return false

        return try {
            val newState = !isOn
            cameraManager.setTorchMode(cameraId!!, newState)
            true
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to toggle flashlight", e)
            false
        }
    }

    fun setOnStateChangedListener(listener: (Boolean) -> Unit) {
        this.listener = listener
    }

    fun release() {
        try {
            cameraManager.unregisterTorchCallback(torchCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister torch callback", e)
        }
        listener = null
    }
}
