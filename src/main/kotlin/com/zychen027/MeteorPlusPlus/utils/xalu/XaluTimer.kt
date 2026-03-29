package com.zychen027.meteorplusplus.utils.xalu

/**
 * 计时器工具类 - 移植自 XALU
 */
class XaluTimer {
    private var timeMs: Long = -1L

    init {
        reset()
    }

    fun reset(): XaluTimer {
        timeMs = System.currentTimeMillis()
        return this
    }

    fun tick(tick: Int): Boolean {
        return passedMs((tick * 50).toLong())
    }

    fun passedTicks(tick: Int): Boolean {
        return passedMs((tick * 50).toLong())
    }

    fun passedS(s: Double): Boolean {
        return passedMs((s * 1000).toLong())
    }

    fun passedMs(ms: Long): Boolean {
        return System.currentTimeMillis() - timeMs >= ms
    }

    fun passedMs(ms: Double): Boolean {
        return passedMs(ms.toLong())
    }

    fun passed(ms: Long): Boolean {
        return passedMs(ms)
    }

    fun passed(ms: Double): Boolean {
        return passedMs(ms.toLong())
    }

    fun setMs(ms: Long) {
        timeMs = System.currentTimeMillis() - ms
    }

    fun passedNS(ns: Long): Boolean {
        return System.nanoTime() - convertToNS(timeMs) >= ns
    }

    fun getPassedTimeMs(): Long {
        return System.currentTimeMillis() - timeMs
    }

    fun getMs(time: Long): Long {
        return time / 1000000L
    }

    fun getMs(): Long {
        return System.currentTimeMillis() - timeMs
    }

    fun convertToNS(time: Long): Long {
        return time * 1000000L
    }
}
