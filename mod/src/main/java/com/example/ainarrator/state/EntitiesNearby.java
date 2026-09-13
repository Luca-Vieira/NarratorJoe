package com.example.ainarrator.state;

import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

public class EntitiesNearby {
    private final Map<String, Integer> hostil = new HashMap<>();
    private final Map<String, Integer> passivo = new HashMap<>();
    private final Map<String, Integer> neutro = new HashMap<>();
    private int players = 0;

    public Map<String, Integer> getHostil() { return hostil; }
    public Map<String, Integer> getPassivo() { return passivo; }
    public Map<String, Integer> getNeutro() { return neutro; }
    public int getPlayers() { return players; }
    public void setPlayers(int players) { this.players = players; }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();

        JsonObject h = new JsonObject();
        hostil.forEach(h::addProperty);
        obj.add("hostil", h);

        JsonObject p = new JsonObject();
        passivo.forEach(p::addProperty);
        obj.add("passivo", p);

        JsonObject n = new JsonObject();
        neutro.forEach(n::addProperty);
        obj.add("neutro", n);

        obj.addProperty("players", players);
        return obj;
    }
}
