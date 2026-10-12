package com.androidassistant.asr

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.androidassistant.voice.OrionLogger
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

/**
 * Завантаження, розпакування, видалення моделей і вибір активної.
 * Один екземпляр на процес, тож завантаження не переривається при закритті екрана.
 */
object ModelManager {

    sealed interface State {
        data object NotInstalled : State
        data class Downloading(val progress: Float) : State
        data object Installing : State
        data object Installed : State
        data class Failed(val message: String) : State
    }

    fun interface Listener {
        fun onChanged(modelId: String, state: State)
    }

    private const val PREFS = "asr_models"
    private const val KEY_ACTIVE = "active_model"
    private const val MARKER = ".ready"

    private val main = Handler(Looper.getMainLooper())
    private val active = ConcurrentHashMap<String, State>()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()
    private val listeners = mutableSetOf<Listener>()

    fun addListener(listener: Listener) = synchronized(listeners) { listeners += listener }
    fun removeListener(listener: Listener) = synchronized(listeners) { listeners -= listener }

    private fun root(context: Context) = File(context.filesDir, "asr-models")
    fun modelDir(context: Context, model: AsrModel) = File(root(context), model.id)

    fun isInstalled(context: Context, model: AsrModel) = File(modelDir(context, model), MARKER).isFile

    fun state(context: Context, model: AsrModel): State =
        active[model.id] ?: if (isInstalled(context, model)) State.Installed else State.NotInstalled

    // ---------- Активна модель ----------

    fun activeModel(context: Context): AsrModel? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val chosen = ModelCatalog.byId(prefs.getString(KEY_ACTIVE, ModelCatalog.DEFAULT_ID))
        if (chosen != null && isInstalled(context, chosen)) return chosen
        // Обрана не встановлена — беремо будь-яку встановлену
        return ModelCatalog.models.firstOrNull { isInstalled(context, it) }
    }

    fun setActive(context: Context, model: AsrModel) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ACTIVE, model.id).apply()
    }

    // ---------- Завантаження ----------

    fun download(context: Context, model: AsrModel) {
        val app = context.applicationContext
        if (active[model.id] is State.Downloading || active[model.id] == State.Installing) return
        cancelled.remove(model.id)
        publish(model.id, State.Downloading(0f))

        Thread({
            val archive = File(app.cacheDir, "${model.id}.download")
            val unpackDir = File(root(app), "${model.id}.tmp")
            try {
                OrionLogger.log("Model ${model.id}: download ${model.url}")
                downloadTo(model, archive)

                publish(model.id, State.Installing)
                unpackDir.deleteRecursively()
                unpackDir.mkdirs()
                archive.inputStream().buffered().use { input ->
                    if (model.url.endsWith(".zip")) unzip(input, unpackDir) else untarBz2(input, unpackDir)
                }

                val target = modelDir(app, model)
                target.deleteRecursively()
                if (!unpackDir.renameTo(target)) throw IOException("Не вдалося перемістити модель")
                File(target, MARKER).writeText(model.url)
                OrionLogger.log("Model ${model.id}: installed")
                publish(model.id, State.Installed, finished = true)
            } catch (e: CancelledException) {
                OrionLogger.log("Model ${model.id}: cancelled")
                publish(model.id, State.NotInstalled, finished = true)
            } catch (e: Exception) {
                OrionLogger.error("Model ${model.id}: failed", e)
                publish(model.id, State.Failed(e.message ?: e.toString()), finished = true)
            } finally {
                archive.delete()
                unpackDir.deleteRecursively()
            }
        }, "model-download-${model.id}").start()
    }

    fun cancel(model: AsrModel) {
        cancelled += model.id
    }

    fun delete(context: Context, model: AsrModel) {
        AsrProvider.releaseIf(model.id)
        modelDir(context, model).deleteRecursively()
        publish(model.id, State.NotInstalled, finished = true)
    }

    private fun publish(id: String, state: State, finished: Boolean = false) {
        if (finished && state !is State.Failed) active.remove(id) else active[id] = state
        main.post {
            val copy = synchronized(listeners) { listeners.toList() }
            copy.forEach { it.onChanged(id, state) }
        }
    }

    private class CancelledException : Exception()

    private fun checkCancelled(id: String) {
        if (id in cancelled) throw CancelledException()
    }

    private fun downloadTo(model: AsrModel, target: File) {
        // Переадресацію GitHub (https → https на інший домен) HttpURLConnection виконує сам
        val connection = (URL(model.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        if (connection.responseCode !in 200..299) {
            val code = connection.responseCode
            connection.disconnect()
            throw IOException("Сервер відповів $code")
        }

        try {
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: (model.downloadMb * 1024L * 1024L)
            var done = 0L
            var lastPercent = -1
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        checkCancelled(model.id)
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        val percent = (done * 100 / total).toInt().coerceAtMost(100)
                        if (percent != lastPercent) {
                            lastPercent = percent
                            publish(model.id, State.Downloading(percent / 100f))
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Архіви sherpa-onnx мають одну теку верхнього рівня — її відкидаємо. */
    private fun stripTopDir(name: String): String? {
        val clean = name.replace('\\', '/').trimStart('/')
        val slash = clean.indexOf('/')
        val inner = if (slash >= 0) clean.substring(slash + 1) else clean
        if (inner.isEmpty() || inner.startsWith("test_wavs")) return null
        return inner
    }

    private fun safeFile(dest: File, relative: String): File {
        val out = File(dest, relative)
        if (!out.canonicalPath.startsWith(dest.canonicalPath + File.separator)) {
            throw IOException("Небезпечний шлях в архіві: $relative")
        }
        return out
    }

    private fun untarBz2(input: InputStream, dest: File) {
        TarArchiveInputStream(BZip2CompressorInputStream(input)).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                val relative = stripTopDir(entry.name) ?: continue
                val out = safeFile(dest, relative)
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { tar.copyTo(it) }
                }
            }
        }
    }

    private fun unzip(input: InputStream, dest: File) {
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val relative = stripTopDir(entry.name) ?: continue
                val out = safeFile(dest, relative)
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                }
            }
        }
    }
}
