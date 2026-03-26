/*
 * Decompiled with CFR 0.152.
 */
package dev.gzsakura_miitong.core.impl;

import java.util.ArrayDeque;
import java.util.Deque;

public class FPSManager {
    private final Deque<Long> records = new ArrayDeque<Long>();

    public void record() {
        this.records.addLast(System.currentTimeMillis());
    }

    public int getFps() {
        long cutoff = System.currentTimeMillis() - 1000L;
        while (!this.records.isEmpty() && this.records.peekFirst() < cutoff) {
            this.records.pollFirst();
        }
        return this.records.size();
    }
}

