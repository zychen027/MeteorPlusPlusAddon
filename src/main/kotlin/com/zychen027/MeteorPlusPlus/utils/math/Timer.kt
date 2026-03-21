package com.zychen027.meteorplusplus.utils.math

/**
 * 时间计时器工具类 - 来自 LeavesHack
 * 用于测量经过的时间和延迟控制
 */
class Timer {
    private var time: Long = -1L

    init {
        reset()
    }

    /**
     * 重置计时器到当前时间
     */
    fun reset(): Timer {
        time = System.nanoTime()
        return this
    }

    /**
     * 检查是否经过了指定的 tick 数 (1 tick = 50ms)
     */
    fun tick(tick: Int): Boolean = passedMs(tick * 50L)

    /**
     * 检查是否经过了指定的 tick 数
     */
    fun passedTicks(tick: Int): Boolean = passedMs(tick * 50L)

    /**
     * 检查是否经过了指定的秒数
     */
    fun passedS(s: Double): Boolean = passedMs((s * 1000).toLong())

    /**
     * 检查是否经过了指定的毫秒数
     */
    fun passedMs(ms: Long): Boolean = passedNS(convertToNS(ms))

    /**
     * 检查是否经过了指定的毫秒数
     */
    fun passedMs(ms: Double): Boolean = passedMs(ms.toLong())

    /**
     * 检查是否经过了指定的毫秒数
     */
    fun passed(ms: Long): Boolean = passedMs(ms)

    /**
     * 检查是否经过了指定的毫秒数
     */
    fun passed(ms: Double): Boolean = passedMs(ms.toLong())

    /**
     * 设置计时器的时间偏移
     */
    fun setMs(ms: Long) {
        time = System.nanoTime() - convertToNS(ms)
    }

    /**
     * 检查是否经过了指定的纳秒数
     */
    fun passedNS(ns: Long): Boolean = System.nanoTime() - time >= ns

    /**
     * 获取已经经过的时间 (毫秒)
     */
    fun getPassedTimeMs(): Long = getMs(System.nanoTime() - time)

    /**
     * 将纳秒转换为毫秒
     */
    fun getMs(time: Long): Long = time / 1000000L

    /**
     * 获取从设置时间到现在经过的毫秒数
     */
    fun getMs(): Long = System.currentTimeMillis() - time

    /**
     * 将毫秒转换为纳秒
     */
    fun convertToNS(time: Long): Long = time * 1000000L
}
