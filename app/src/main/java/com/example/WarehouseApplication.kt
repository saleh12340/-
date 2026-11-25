package com.example

import android.app.Application
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Captures fatal exceptions so the next launch can display a shareable report. */
class WarehouseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val writer = StringWriter()
                error.printStackTrace(PrintWriter(writer))
                val report = buildString {
                    appendLine("Warehouse Ledger crash report")
                    appendLine("Thread: ${thread.name}")
                    appendLine("Time: ${System.currentTimeMillis()}")
                    appendLine()
                    append(writer.toString())
                }
                File(filesDir, "last_startup_crash.txt").writeText(report, Charsets.UTF_8)
            }
            if (previousHandler != null) previousHandler.uncaughtException(thread, error)
            else Process.killProcess(Process.myPid())
        }
    }
}
