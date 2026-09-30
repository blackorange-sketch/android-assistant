package com.androidassistant.voice

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object OrionLogger {

    private const val TAG = "ORION"

    private var logFile: File? = null

    fun init(context: Context) {
        logFile = File(context.filesDir, "orion.log")

        try {
            if (logFile!!.length() > 512 * 1024) {
                logFile!!.writeText("")
            }
        } catch (_: Exception) {
        }

        write("=== ORION START ===")
    }

    fun log(message: String) {
        write(message)
    }

    private fun write(message: String) {
        val time = SimpleDateFormat(
            "HH:mm:ss.SSS",
            Locale.US
        ).format(Date())

        val line = "[$time] $message"

        Log.d(TAG, line)

        try {
            logFile?.appendText(
                "$line\n",
                Charsets.UTF_8
            )
        } catch (_: Exception) {
        }
    }

    fun error(message: String, throwable: Throwable? = null) {
        val text = if (throwable != null) {
            "$message: ${throwable.stackTraceToString()}"
        } else {
            message
        }

        Log.e(TAG, text)

        try {
            logFile?.appendText(
                "[ERROR] $text\n",
                Charsets.UTF_8
            )
        } catch (_: Exception) {
        }
    }
}
