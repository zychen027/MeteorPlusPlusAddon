package com.zychen027.meteorplusplus.utils.efa.math

class Timer {
    private var time = -1L

    init {
        reset()
    }

    fun reset(): Timer {
        time = System.nanoTime()
        return this
    }

    fun tick(tick: Int): Boolean = passedMs(tick * 50L)
    fun passedTicks(tick: Int): Boolean = passedMs(tick * 50L)
    fun passedS(s: Double): Boolean = passedMs(s * 1000)
    fun passedMs(ms: Long): Boolean = passedNS(convertToNS(ms))
    fun passedMs(ms: Double): Boolean = passedMs(ms.toLong())
    fun passed(ms: Long): Boolean = passedMs(ms)
    fun passed(ms: Double): Boolean = passedMs(ms.toLong())

    fun setMs(ms: Long) {
        time = System.nanoTime() - convertToNS(ms)
    }

    fun passedNS(ns: Long): Boolean = System.nanoTime() - time >= ns
    fun getPassedTimeMs(): Long = getMs(System.nanoTime() - time)
    fun getMs(time: Long): Long = time / 1000000L
    fun getMs(): Long = System.currentTimeMillis() - time
    fun convertToNS(time: Long): Long = time * 1000000L
}
