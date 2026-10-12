package com.androidassistant

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.androidassistant.asr.AsrModel
import com.androidassistant.asr.AsrProvider
import com.androidassistant.asr.ModelCatalog
import com.androidassistant.asr.ModelManager
import com.androidassistant.asr.ModelManager.State

/** Список моделей розпізнавання: завантажити, видалити, обрати активну. */
class ModelsActivity : Activity() {

    private class Row(
        val model: AsrModel,
        val status: TextView,
        val progress: ProgressBar,
        val primary: Button,
        val secondary: Button,
    )

    private val rows = mutableListOf<Row>()
    private val listener = ModelManager.Listener { _, _ -> refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        list.addView(text("МОДЕЛІ РОЗПІЗНАВАННЯ", 20f, Color.WHITE, bold = true))
        list.addView(
            text(
                "Завантажуються один раз, далі все працює без інтернету. " +
                    "Можна тримати кілька й перемикатися між ними.",
                13f, Color.parseColor("#7C8799"),
            ).apply { setPadding(0, dp(6), 0, dp(16)) }
        )

        ModelCatalog.models.forEach { model -> list.addView(card(model)) }

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#080B12"))
            addView(list)
        })
    }

    override fun onResume() {
        super.onResume()
        ModelManager.addListener(listener)
        refresh()
    }

    override fun onPause() {
        ModelManager.removeListener(listener)
        super.onPause()
    }

    private fun card(model: AsrModel): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#121826"))
            }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
        }
        box.addView(text(model.title, 17f, Color.WHITE, bold = true))
        box.addView(text(model.description, 13f, Color.parseColor("#B5C0D0")).apply { setPadding(0, dp(4), 0, 0) })
        box.addView(text("Завантаження: ~${model.downloadMb} МБ", 12f, Color.parseColor("#7C8799")).apply {
            setPadding(0, dp(4), 0, 0)
        })

        val status = text("", 13f, Color.parseColor("#8FA8C7")).apply { setPadding(0, dp(8), 0, 0) }
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        val primary = Button(this)
        val secondary = Button(this)
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
            addView(primary)
            addView(secondary, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(8) })
        }

        box.addView(status)
        box.addView(progress)
        box.addView(buttons)
        rows += Row(model, status, progress, primary, secondary)
        return box
    }

    private fun refresh() {
        val activeId = ModelManager.activeModel(this)?.id
        for (row in rows) {
            val model = row.model
            val state = ModelManager.state(this, model)
            row.progress.visibility = View.GONE
            row.progress.isIndeterminate = false
            row.secondary.visibility = View.GONE
            row.primary.isEnabled = true

            when (state) {
                State.NotInstalled, is State.Failed -> {
                    row.status.text = if (state is State.Failed) "Помилка: ${state.message}" else "Не завантажена"
                    row.primary.text = "Завантажити"
                    row.primary.setOnClickListener { ModelManager.download(this, model) }
                }
                is State.Downloading -> {
                    val percent = (state.progress * 100).toInt()
                    row.status.text = "Завантаження… $percent%"
                    row.progress.visibility = View.VISIBLE
                    row.progress.progress = percent
                    row.primary.text = "Скасувати"
                    row.primary.setOnClickListener { ModelManager.cancel(model) }
                }
                State.Installing -> {
                    row.status.text = "Розпаковую…"
                    row.progress.visibility = View.VISIBLE
                    row.progress.isIndeterminate = true
                    row.primary.text = "Зачекай"
                    row.primary.isEnabled = false
                    row.primary.setOnClickListener(null)
                }
                State.Installed -> {
                    val isActive = model.id == activeId
                    row.status.text = if (isActive) "✓ Використовується" else "Завантажена"
                    row.primary.text = if (isActive) "Активна" else "Використовувати"
                    row.primary.isEnabled = !isActive
                    row.primary.setOnClickListener {
                        ModelManager.setActive(this, model)
                        AsrProvider.release() // нова модель підвантажиться при наступній команді
                        refresh()
                    }
                    row.secondary.visibility = View.VISIBLE
                    row.secondary.text = "Видалити"
                    row.secondary.setOnClickListener { confirmDelete(model) }
                }
            }
        }
    }

    private fun confirmDelete(model: AsrModel) {
        AlertDialog.Builder(this)
            .setTitle("Видалити ${model.title}?")
            .setMessage("Щоб користуватися нею знову, доведеться завантажити ~${model.downloadMb} МБ.")
            .setPositiveButton("Видалити") { _, _ ->
                Thread {
                    ModelManager.delete(applicationContext, model)
                }.start()
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun text(value: String, sizeSp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
