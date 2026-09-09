package com.zerosix.app

import android.app.BatteryManager
import android.content.Context
import android.net.TrafficStats
import android.os.StatFs
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class SystemMonitor(private val context: Context) {
    data class Snapshot(
        val cpu: Float,
        val ram: Float,
        val storage: Float,
        val battery: Int,
        val rx: Long,
        val tx: Long
    )

    private var lastTotal = -1L
    private var lastIdle = -1L
    private var lastRx = -1L
    private var lastTx = -1L
    private var lastTime = System.nanoTime()

    fun sample(): Snapshot {
        val cpu = cpuUsage()
        val mem = memoryUsage()
        val stat = StatFs(context.filesDir.absolutePath)
        val total = stat.totalBytes.coerceAtLeast(1L)
        val used = total - stat.availableBytes
        val storage = used.toDouble() / total.toDouble() * 100.0
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        val rxNow = TrafficStats.getTotalRxBytes().coerceAtLeast(0L)
        val txNow = TrafficStats.getTotalTxBytes().coerceAtLeast(0L)
        val now = System.nanoTime()
        val seconds = ((now - lastTime).toDouble() / 1_000_000_000.0).coerceAtLeast(0.1)
        val rxRate = if (lastRx >= 0) ((rxNow - lastRx) / seconds).toLong() else 0L
        val txRate = if (lastTx >= 0) ((txNow - lastTx) / seconds).toLong() else 0L
        lastRx = rxNow; lastTx = txNow; lastTime = now
        return Snapshot(cpu, mem, storage.toFloat(), battery, rxRate.coerceAtLeast(0), txRate.coerceAtLeast(0))
    }

    private fun cpuUsage(): Float {
        val line = runCatching { File("/proc/stat").bufferedReader().use { it.readLine() } }.getOrNull() ?: return 0f
        val p = line.trim().split(Regex("\\s+"))
        if (p.size < 5) return 0f
        val user = p[1].toLongOrNull() ?: return 0f
        val nice = p[2].toLongOrNull() ?: 0L
        val system = p[3].toLongOrNull() ?: 0L
        val idle = p[4].toLongOrNull() ?: 0L
        val iowait = p.getOrNull(5)?.toLongOrNull() ?: 0L
        val irq = p.getOrNull(6)?.toLongOrNull() ?: 0L
        val softirq = p.getOrNull(7)?.toLongOrNull() ?: 0L
        val steal = p.getOrNull(8)?.toLongOrNull() ?: 0L
        val idleAll = idle + iowait
        val total = user + nice + system + idle + iowait + irq + softirq + steal
        if (lastTotal < 0) { lastTotal = total; lastIdle = idleAll; return 0f }
        val dTotal = total - lastTotal
        val dIdle = idleAll - lastIdle
        lastTotal = total; lastIdle = idleAll
        return if (dTotal <= 0) 0f else ((dTotal - dIdle).toFloat() / dTotal * 100f).coerceIn(0f, 100f)
    }

    private fun memoryUsage(): Float {
        var total = 0L; var available = 0L
        runCatching {
            File("/proc/meminfo").forEachLine { line ->
                when {
                    line.startsWith("MemTotal:") -> total = line.filter { it.isDigit() }.toLongOrNull()?.times(1024) ?: 0L
                    line.startsWith("MemAvailable:") -> available = line.filter { it.isDigit() }.toLongOrNull()?.times(1024) ?: 0L
                }
            }
        }
        return if (total <= 0) 0f else ((total - available).toDouble() / total * 100.0).toFloat().coerceIn(0f, 100f)
    }
}
