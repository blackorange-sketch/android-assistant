package com.androidassistant.tools

import android.content.Context
import android.content.Intent

class AppTool(private val context: Context) {

    fun open(packageName: String): Boolean {
        return try {
            val intent =
                context.packageManager.getLaunchIntentForPackage(packageName)
                    ?: return false

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            true
        } catch (_: Exception) {
            false
        }
    }

    fun openSettings() {
        val intent = Intent(
            android.provider.Settings.ACTION_SETTINGS
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }
}
