package com.androidassistant.tools

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper

class FlashlightTool(context: Context) : Tool {
    override val name = "flashlight"
    override val description = "Увімкнути або вимкнути ліхтарик. Аргумент state: on | off | toggle."

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    // Не кожна камера має спалах: беремо першу, де він є
    private val cameraId: String? = runCatching {
        cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()

    @Volatile
    private var isOn = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) isOn = enabled
        }
    }

    init {
        cameraManager.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
    }

    override fun execute(args: Map<String, String>): ToolResult {
        val id = cameraId ?: return ToolResult.fail("На цьому пристрої немає ліхтарика")
        val enable = when (args["state"]) {
            "on" -> true
            "off" -> false
            else -> !isOn
        }
        return try {
            cameraManager.setTorchMode(id, enable)
            isOn = enable
            ToolResult.ok(if (enable) "Ліхтарик увімкнено" else "Ліхтарик вимкнено")
        } catch (e: Exception) {
            ToolResult.fail("Не вдалося керувати ліхтариком. Можливо, камера зайнята.")
        }
    }

    override fun release() {
        cameraManager.unregisterTorchCallback(torchCallback)
    }
}
