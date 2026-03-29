package com.dev.xalu.utils.math;

/* JADX INFO: loaded from: XALU Beta Version-1.2.1.jar:com/dev/xalu/utils/math/Timer.class */
public class Timer {
    private long time = System.currentTimeMillis();

    public void reset() {
        this.time = System.currentTimeMillis();
    }

    public void setMs(long ms) {
        this.time = System.currentTimeMillis() - ms;
    }

    public boolean passedMs(long ms) {
        return System.currentTimeMillis() - this.time >= ms;
    }

    public long getPassedTimeMs() {
        return System.currentTimeMillis() - this.time;
    }
}
