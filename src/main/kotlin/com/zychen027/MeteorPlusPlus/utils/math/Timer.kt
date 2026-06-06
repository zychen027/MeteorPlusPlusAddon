package com.zychen027.meteorplusplus.utils.math

class Timer {
    private var time = -1L

    fun reset() {
        time = System.currentTimeMillis()
    }

    fun setMs(ms: Long) {
        time = System.currentTimeMillis() - ms
    }

    fun passedMs(ms: Long): Boolean {
        return System.currentTimeMillis() - time >= ms
    }

    fun getMs(): Long {
        return System.currentTimeMillis() - time
    }
}
