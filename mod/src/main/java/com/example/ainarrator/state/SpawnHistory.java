package com.example.ainarrator.state;

import com.google.gson.JsonObject;
import net.minecraft.util.math.BlockPos;

public class SpawnHistory {
    private SpawnEntry current;
    private SpawnEntry previous;

    public SpawnHistory(BlockPos initialPos) {
        this.current = new SpawnEntry(initialPos, System.currentTimeMillis());
        this.previous = null;
    }

    public synchronized void updateSpawn(BlockPos newPos) {
        if (current == null) {
            current = new SpawnEntry(newPos, System.currentTimeMillis());
            return;
        }
        if (!current.getPos().equals(newPos)) {
            previous = current;
            current = new SpawnEntry(newPos, System.currentTimeMillis());
        }
    }

    public synchronized SpawnEntry getCurrent() { return current; }
    public synchronized SpawnEntry getPrevious() { return previous; }

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
