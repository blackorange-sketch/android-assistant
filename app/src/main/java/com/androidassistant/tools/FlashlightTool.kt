package com.androidassistant.tools

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

class FlashlightTool(context: Context) {

    private val cameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private fun cameraId(): String? {
        return cameraManager.cameraIdList.firstOrNull { id ->
            val characteristics =
                cameraManager.getCameraCharacteristics(id)

            characteristics.get(
                CameraCharacteristics.FLASH_INFO_AVAILABLE
            ) == true
        }
    }

    fun setEnabled(enabled: Boolean) {
        val id = cameraId() ?: return
        cameraManager.setTorchMode(id, enabled)
    }
}
