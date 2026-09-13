package com.example.ainarrator.state;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class ModState {
    private static final AtomicLong SEQ = new AtomicLong(1);
    private static final AtomicBoolean PAUSED = new AtomicBoolean(false);

    private ModState() {}

    public static long getSeq() {
        return SEQ.get();
    }

    public static long incrementSeq() {
        return SEQ.incrementAndGet();
    }

    public static void resetSeq() {
        SEQ.set(1);
    }

    public static boolean isPaused() {
        return PAUSED.get();
    }

    public static void setPaused(boolean paused) {
        PAUSED.set(paused);
    }
}
