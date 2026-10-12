package com.androidassistant.tools

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import com.androidassistant.nlu.AppNameMatcher

class OpenAppTool(private val context: Context) : Tool {
    override val name = "open_app"
    override val description = "Відкрити застосунок за назвою. Аргумент query — назва, як її сказали."

    /** Системні речі, які зручніше відкривати напряму, ніж шукати за підписом. */
    private data class Shortcut(val stems: List<String>, val intent: () -> Intent)

    private val shortcuts = listOf(
        Shortcut(listOf("камер")) { Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA) },
        Shortcut(listOf("лаштуван", "настройк")) { Intent(Settings.ACTION_SETTINGS) },
        Shortcut(listOf("телефон", "дзвінк", "дзвонилк", "набір")) { Intent(Intent.ACTION_DIAL) },
        Shortcut(listOf("будильник", "годинник")) { Intent(AlarmClock.ACTION_SHOW_ALARMS) },
    )

    override fun execute(args: Map<String, String>): ToolResult {
        val query = args["query"]?.trim().orEmpty()
        if (query.isEmpty()) return ToolResult.fail("Який застосунок відкрити?")

        shortcuts.firstOrNull { (stems, _) -> stems.any { query.contains(it) } }?.let { (_, intent) ->
            if (launch(intent())) return ToolResult.ok("Відкриваю")
        }

        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val candidates = pm.queryIntentActivities(launcher, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { AppNameMatcher.Candidate(it.loadLabel(pm).toString(), it.activityInfo.packageName) }

        val match = AppNameMatcher.bestMatch(query, candidates)
            ?: return ToolResult.fail("Не знайшов застосунок «$query»")

        val intent = pm.getLaunchIntentForPackage(match.payload)
            ?: return ToolResult.fail("Не вдалося запустити ${match.label}")
        return if (launch(intent)) ToolResult.ok("Відкриваю ${match.label}")
        else ToolResult.fail("Не вдалося запустити ${match.label}")
    }

    private fun launch(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
