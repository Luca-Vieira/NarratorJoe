package com.example.ainarrator.state;

import com.google.gson.JsonObject;
import net.minecraft.util.math.BlockPos;

public class SpawnEntry {
    private final BlockPos pos;
    private final long setT;
    private boolean isHome;
    private long dwellMs;

    public SpawnEntry(BlockPos pos, long setT) {
        this.pos = pos;
        this.setT = setT;
        this.isHome = false;
        this.dwellMs = 0;
    }

    public BlockPos getPos() { return pos; }
    public long getSetT() { return setT; }
    public boolean isHome() { return isHome; }
    public void setHome(boolean home) { this.isHome = home; }
    public long getDwellMs() { return dwellMs; }
    public void addDwellMs(long delta) { this.dwellMs += delta; }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        JsonObject p = new JsonObject();
        p.addProperty("x", pos.getX());
        p.addProperty("y", pos.getY());
        p.addProperty("z", pos.getZ());
        obj.add("pos", p);
        obj.addProperty("set_t", setT);
        obj.addProperty("is_home", isHome);
        obj.addProperty("dwell_ms", dwellMs);
        return obj;
    }
}
