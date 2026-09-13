package com.example.ainarrator.state;

import com.google.gson.JsonObject;
import net.minecraft.util.math.BlockPos;

public class BiomeHistory {
    private BiomeEntry current;
    private BiomeEntry previous;

    public BiomeHistory(String initialBiomeId, BlockPos initialPos) {
        this.current = new BiomeEntry(initialBiomeId, System.currentTimeMillis(), initialPos);
        this.previous = null;
    }

    public synchronized void updateBiome(String newBiomeId, BlockPos pos) {
        if (current == null) {
            current = new BiomeEntry(newBiomeId, System.currentTimeMillis(), pos);
            return;
        }
        if (!current.getId().equals(newBiomeId)) {
            current.setUntilT(System.currentTimeMillis());
            previous = current;
            current = new BiomeEntry(newBiomeId, System.currentTimeMillis(), pos);
        }
    }

    public synchronized BiomeEntry getCurrent() { return current; }
    public synchronized BiomeEntry getPrevious() { return previous; }

    public synchronized JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.add("current", current != null ? current.toJson() : null);
        if (previous != null) {
            obj.add("previous", previous.toJson());
        } else {
            obj.add("previous", null);
        }
        return obj;
    }
}
