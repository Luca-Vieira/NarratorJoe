package com.example.ainarrator.state;

import com.google.gson.JsonObject;

public class HeldItem {
    private final String item;
    private final long sinceT;

    public HeldItem(String item, long sinceT) {
        this.item = item;
        this.sinceT = sinceT;
    }

    public String getItem() { return item; }
    public long getSinceT() { return sinceT; }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("item", item);
        obj.addProperty("since_t", sinceT);
        return obj;
    }
}
