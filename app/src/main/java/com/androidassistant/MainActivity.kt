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

class MainActivity : Activity() {

    private lateinit var voice: VoiceEngine
    private lateinit var speech: SpeechEngine
    private lateinit var router: CommandRouter

    private lateinit var status: TextView
    private lateinit var commandText: TextView
    private lateinit var responseText: TextView
    private lateinit var listenButton: Button

    private var micAnimator: AnimatorSet? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        commandText = findViewById(R.id.command)
        responseText = findViewById(R.id.response)
        listenButton = findViewById(R.id.listen)

        router = CommandRouter(this)
        speech = SpeechEngine(this)

        voice = VoiceEngine(
            context = this,

            onResult = { command ->
                runOnUiThread {
                    stopMicAnimation()

                    commandText.text = command
                    status.text = "Обробляю..."

                    val response = router.execute(command)

                    responseText.text = response
                    status.text = "Готовий"

                    speech.speak(response)
                }
            },

            onError = { error ->
                runOnUiThread {
                    stopMicAnimation()

                    status.text = "Помилка"
                    responseText.text = error

                    speech.speak(error)
                }
            },

            onListeningChanged = { listening ->
                runOnUiThread {
                    if (listening) {
                        status.text = "Слухаю..."
                        listenButton.text = "🎙️"
                        startMicAnimation()
                    } else {
                        listenButton.text = "🎙"
                        stopMicAnimation()
                    }
                }
            }
        )

        listenButton.setOnClickListener {
            startVoiceRecognition()
        }
    }

    private fun startVoiceRecognition() {
        if (
            checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
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

        micAnimator = AnimatorInflater.loadAnimator(
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
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            voice.start("uk-UA")
        } else {
            status.text = "Потрібен дозвіл"
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
