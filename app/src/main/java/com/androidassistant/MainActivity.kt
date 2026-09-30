package com.androidassistant

import android.Manifest
import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.androidassistant.core.CommandRouter
import com.androidassistant.voice.SpeechEngine
import com.androidassistant.voice.VoiceEngine
import com.androidassistant.voice.OrionLogger

class MainActivity : Activity() {

    private lateinit var voice: VoiceEngine
    private lateinit var speech: SpeechEngine
    private lateinit var router: CommandRouter

    private lateinit var status: TextView
    private lateinit var commandText: TextView
    private lateinit var responseText: TextView
    private lateinit var listenButton: Button

    private var micAnimator: AnimatorSet? = null
    private var wakeModeActive = false
    private var listening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        OrionLogger.init(this)
        OrionLogger.log("MainActivity created")

        status = findViewById(R.id.status)
        commandText = findViewById(R.id.command)
        responseText = findViewById(R.id.response)
        listenButton = findViewById(R.id.listen)

        val copyLogButton = Button(this).apply {
            text = "COPY LOG"
            setOnClickListener {
                try {
                    val logFile = java.io.File(filesDir, "orion.log")

                    val log = if (logFile.exists()) {
                        logFile.readText(Charsets.UTF_8)
                    } else {
                        "ORION LOG EMPTY"
                    }

                    val clipboard =
                        getSystemService(CLIPBOARD_SERVICE)
                            as ClipboardManager

                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(
                            "ORION LOG",
                            log
                        )
                    )

                    responseText.text =
                        "LOG COPIED\n${log.takeLast(1000)}"

                } catch (e: Exception) {
                    responseText.text =
                        "LOG ERROR: ${e.message}"
                }
            }
        }

        addContentView(
            copyLogButton,
            android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        try {
            val logFile = java.io.File(filesDir, "orion.log")
            if (logFile.exists()) {
                val lines = logFile.readLines()
                val lastLines = lines.takeLast(25).joinToString("\n")
                responseText.text = lastLines
            }
        } catch (_: Exception) {
        }

        router = CommandRouter(this)
        speech = SpeechEngine(this)

        voice = VoiceEngine(
            context = this,

            onResult = { command ->
                runOnUiThread {
                    listening = false
                    stopMicAnimation()

                    commandText.text = command
                    status.text = "Обробляю..."

                    val response = router.execute(command)

                    responseText.text = response
                    status.text = "Чекаю «Оріон»"

                    speech.speak(response)

                    startWakeWord()
                }
            },

            onError = { error ->
                runOnUiThread {
                    listening = false
                    stopMicAnimation()

                    responseText.text = error
                    status.text = "Чекаю «Оріон»"

                    speech.speak(error)

                    startWakeWord()
                }
            },

            onListeningChanged = { isListening ->
                runOnUiThread {
                    listening = isListening

                    if (isListening) {
                        status.text =
                            if (isWakeMode()) {
                                "Чекаю «Оріон»..."
                            } else {
                                "Слухаю..."
                            }

                        listenButton.text = "🎙️"
                        startMicAnimation()
                    } else {
                        listenButton.text = "🎙"
                        stopMicAnimation()
                    }
                }
            },

            onWakeWord = {
                runOnUiThread {
                    wakeModeActive = false

                    stopMicAnimation()

                    status.text = "Слухаю..."
                    responseText.text = "Так, слухаю."

                    speech.speak("Так, слухаю.")

                    listenButton.text = "🎙️"

                    voice.start("uk-UA")
                }
            }
        )

        listenButton.setOnClickListener {
            if (listening && !wakeModeActive) {
                voice.stop()

                listening = false
                stopMicAnimation()

                status.text = "Чекаю «Оріон»"

                startWakeWord()
            } else {
                startVoiceRecognition()
            }
        }

        if (
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startWakeWord()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
        }
    }

    private fun isWakeMode(): Boolean {
        return wakeModeActive
    }

    private fun startWakeWord() {
        wakeModeActive = true
        status.text = "Чекаю «Оріон»"

        try {
            voice.startWakeWord("uk-UA")
        } catch (_: Exception) {
            status.text = "Помилка запуску"
        }
    }

    private fun startVoiceRecognition() {
        wakeModeActive = false

        if (
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
            return
        }

        status.text = "Слухаю..."
        startMicAnimation()

        voice.start("uk-UA")
    }

    private fun startMicAnimation() {
        if (micAnimator != null) return

        micAnimator =
            AnimatorInflater.loadAnimator(
                this,
                R.animator.mic_pulse
            ) as AnimatorSet

        micAnimator?.setTarget(listenButton)
        micAnimator?.start()
    }

    private fun stopMicAnimation() {
        micAnimator?.cancel()
        micAnimator = null

        listenButton.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(150)
            .start()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            requestCode == REQUEST_RECORD_AUDIO &&
            grantResults.isNotEmpty() &&
            grantResults[0] ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startWakeWord()
        } else {
            status.text = "Потрібен дозвіл на мікрофон"
        }
    }

    override fun onDestroy() {
        stopMicAnimation()
        voice.destroy()
        speech.destroy()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_RECORD_AUDIO = 100
    }
}
