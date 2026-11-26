package com.github.damontecres.wholphin.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.Toast
import com.github.damontecres.wholphin.BuildConfig
import com.github.damontecres.wholphin.services.hilt.AppModule
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.api.client.extensions.systemApi
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Jellyeye freeze recorder. Watches for the app locking up and, when it does, writes down where it was stuck
 * and sends that to the Jellyfin server's logs (Dashboard → Logs), the same way crash reports already go.
 * Nothing is installed on the server.
 *
 * Two kinds of lock-up are caught:
 *  - the app itself has stopped (nothing on screen can move): the stuck code is recorded;
 *  - the app is running but the remote's presses are going nowhere (nothing selected on the page).
 *
 * Each report also has a short trail of what happened just before (pages opened, keys pressed, focus moves).
 * If the report can't be sent at the time, it's kept and sent the next time the app starts.
 */
object FreezeRecorder {
    private const val FILE_NAME = "jellyeye-freeze.txt"
    private const val STUCK_MS = 6_000L
    private const val IGNORED_KEYS = 10

    private val trail = ArrayDeque<String>()
    private val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.UK)
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var lastTick = SystemClock.uptimeMillis()

    @Volatile private var stuckReported = false
    private var ignoredRun = 0
    private var ignoredReported = false
    private var appContext: Context? = null

    fun start(context: Context) {
        if (appContext != null) return
        val ctx = context.applicationContext
        appContext = ctx
        note("app started, version ${BuildConfig.VERSION_NAME}")
        // anything saved from last time (the app was force-stopped before it could send it)
        thread(name = "jellyeye-freeze-send", isDaemon = true) { sendSaved(ctx) }
        thread(name = "jellyeye-freeze-watch", isDaemon = true) {
            while (true) {
                main.post { lastTick = SystemClock.uptimeMillis() }
                try {
                    Thread.sleep(1_000)
                } catch (_: InterruptedException) {
                    return@thread
                }
                val stuckFor = SystemClock.uptimeMillis() - lastTick
                if (stuckFor >= STUCK_MS && !stuckReported) {
                    stuckReported = true
                    record(ctx, "The app stopped responding (stuck for ${stuckFor / 1000}s)", withStacks = true)
                } else if (stuckFor < 2_000 && stuckReported) {
                    stuckReported = false
                    note("app responding again")
                }
            }
        }
    }

    /** Adds a line to the trail of what happened just before a lock-up. */
    fun note(what: String) {
        val line = "${synchronized(clock) { clock.format(Date()) }}  $what"
        synchronized(trail) {
            trail.addLast(line)
            while (trail.size > 80) trail.removeFirst()
        }
        Timber.i("Jellyeye: %s", what)
    }

    /** Called for every remote press, with whether the app did anything with it. */
    fun key(
        event: KeyEvent,
        handled: Boolean,
    ) {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return
        note("key ${KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_")}${if (handled) "" else " (nothing happened)"}")
        if (handled) {
            ignoredRun = 0
            ignoredReported = false
            return
        }
        ignoredRun++
        if (ignoredRun >= IGNORED_KEYS && !ignoredReported) {
            ignoredReported = true
            val ctx = appContext ?: return
            thread(name = "jellyeye-freeze-keys", isDaemon = true) {
                record(ctx, "The remote's presses are going nowhere ($ignoredRun in a row did nothing)", withStacks = true)
            }
        }
    }

    private fun record(
        ctx: Context,
        reason: String,
        withStacks: Boolean,
    ) {
        val report =
            try {
                buildReport(ctx, reason, withStacks)
            } catch (ex: Throwable) {
                "Jellyeye freeze report\n$reason\n(couldn't build the full report: $ex)"
            }
        val file = File(ctx.filesDir, FILE_NAME)
        try {
            file.writeText(report)
        } catch (ex: Exception) {
            Timber.w(ex, "Jellyeye: couldn't save the freeze report")
        }
        if (send(ctx, report)) file.delete()
    }

    private fun buildReport(
        ctx: Context,
        reason: String,
        withStacks: Boolean,
    ): String =
        buildString {
            appendLine("Jellyeye freeze report")
            appendLine("When: ${Date()}")
            appendLine("What: $reason")
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android API ${Build.VERSION.SDK_INT}")
            val rt = Runtime.getRuntime()
            val mb = 1024 * 1024
            appendLine("App memory: ${(rt.totalMemory() - rt.freeMemory()) / mb} MB used of ${rt.maxMemory() / mb} MB")
            try {
                val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
                appendLine("Device memory: ${mi.availMem / mb} MB free of ${mi.totalMem / mb} MB, low=${mi.lowMemory}")
            } catch (_: Exception) {
            }
            appendLine()
            appendLine("---- What happened just before (oldest first) ----")
            synchronized(trail) { trail.forEach { appendLine(it) } }
            if (withStacks) {
                appendLine()
                appendLine("---- Where the app's main thread is ----")
                Looper.getMainLooper().thread.stackTrace.forEach { appendLine("    at $it") }
                appendLine()
                appendLine("---- Other threads ----")
                Thread.getAllStackTraces().forEach { (t, stack) ->
                    if (t === Looper.getMainLooper().thread || stack.isEmpty()) return@forEach
                    appendLine("${t.name} (${t.state})")
                    stack.take(12).forEach { appendLine("    at $it") }
                }
            }
            appendLine()
            appendLine("---- App log (last 300 lines) ----")
            append(logcat(300))
        }

    private fun logcat(lines: Int): String =
        try {
            val process =
                ProcessBuilder()
                    .command("logcat", "-d", "-t", lines.toString(), "*:V")
                    .redirectErrorStream(true)
                    .start()
            val text = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            process.destroy()
            text
        } catch (ex: Exception) {
            "(couldn't read the log: $ex)"
        }

    private fun sendSaved(ctx: Context) {
        val file = File(ctx.filesDir, FILE_NAME)
        if (!file.exists()) return
        val report =
            try {
                file.readText()
            } catch (_: Exception) {
                return
            }
        if (send(ctx, "(sent after the app restarted)\n$report")) file.delete()
    }

    private fun send(
        ctx: Context,
        report: String,
    ): Boolean =
        try {
            val api = AppModule.mostRecentServerProvider(ctx).get().createApi(ctx)
            if (api == null) {
                false
            } else {
                val name = runBlocking { api.systemApi.logFile(report).content.fileName }
                Timber.i("Jellyeye: freeze report sent to the server as %s", name)
                main.post {
                    Toast.makeText(ctx, "Jellyeye sent a freeze report to your server: $name", Toast.LENGTH_LONG).show()
                }
                true
            }
        } catch (ex: Exception) {
            Timber.w(ex, "Jellyeye: couldn't send the freeze report, will try next start")
            false
        }
}
