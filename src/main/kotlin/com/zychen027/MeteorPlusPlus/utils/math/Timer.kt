package com.zychen027.meteorplusplus.utils.math

/**
 * 时间计时器工具类 - 移植自 LeavesHack
 */
class Timer {
    private var time: Long = -1L

    init {
        reset()
    }

    fun reset(): Timer {
        time = System.nanoTime()
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
        return passedNS(convertToNS(ms))
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
        time = System.nanoTime() - convertToNS(ms)
    }

    fun passedNS(ns: Long): Boolean {
        return System.nanoTime() - time >= ns
    }

    fun getPassedTimeMs(): Long {
        return getMs(System.nanoTime() - time)
    }

    fun getMs(time: Long): Long {
        return time / 1000000L
    }

    fun getMs(): Long {
        return System.currentTimeMillis() - time
    }

    fun convertToNS(time: Long): Long {
        return time * 1000000L
    }
}
