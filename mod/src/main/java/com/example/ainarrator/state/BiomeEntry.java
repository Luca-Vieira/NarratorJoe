package com.example.ainarrator.state;

import com.google.gson.JsonObject;
import net.minecraft.util.math.BlockPos;

public class BiomeEntry {
    private final String id;
    private final long sinceT;
    private final BlockPos pos;
    private Long untilT;

    public BiomeEntry(String id, long sinceT, BlockPos pos) {
        this.id = id;
        this.sinceT = sinceT;
        this.pos = pos;
        this.untilT = null;
    }

    public String getId() { return id; }
    public long getSinceT() { return sinceT; }
    public BlockPos getPos() { return pos; }
    public Long getUntilT() { return untilT; }
    public void setUntilT(Long untilT) { this.untilT = untilT; }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        obj.addProperty("since_t", sinceT);
        if (pos != null) {
            JsonObject p = new JsonObject();
            p.addProperty("x", pos.getX());
            p.addProperty("y", pos.getY());
            p.addProperty("z", pos.getZ());
            obj.add("pos", p);
        }
        if (untilT != null) {
            obj.addProperty("until_t", untilT);
        }
        return obj;
    }
}
