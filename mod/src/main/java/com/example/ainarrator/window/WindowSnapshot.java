package com.example.ainarrator.window;

import com.example.ainarrator.collector.GameEvent;
import java.util.ArrayList;
import java.util.List;

public class WindowSnapshot {
    private final long windowStart;
    private final long windowEnd;
    private final List<GameEvent> events = new ArrayList<>();

    public WindowSnapshot(long windowStart, long windowEnd) {
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
    }

    public void addEvent(GameEvent event) {
        events.add(event);
    }

    public long getWindowStart() { return windowStart; }
    public long getWindowEnd() { return windowEnd; }
    public List<GameEvent> getEvents() { return events; }
}
