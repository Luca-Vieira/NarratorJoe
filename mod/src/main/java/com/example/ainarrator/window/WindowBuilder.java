package com.example.ainarrator.window;

import com.example.ainarrator.collector.GameEvent;
import com.example.ainarrator.state.ModState;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

public class WindowBuilder {
    public static JsonObject build(WindowSnapshot snapshot) {
        JsonObject root = new JsonObject();
        root.addProperty("seq", ModState.getSeq());
        root.addProperty("window_start", snapshot.getWindowStart());
        root.addProperty("window_end", snapshot.getWindowEnd());

        JsonObject activity = new JsonObject();

        Map<String, Integer> blocksPlaced = new LinkedHashMap<>();
        Map<String, Integer> blocksBroken = new LinkedHashMap<>();
        Map<String, Integer> itemsGained = new LinkedHashMap<>();
        Map<String, Integer> itemsLost = new LinkedHashMap<>();
        Map<String, Integer> mobsKilled = new LinkedHashMap<>();
        Map<String, Integer> containersOpened = new LinkedHashMap<>();
        Map<String, Integer> stationsUsed = new LinkedHashMap<>();

        Map<String, Float> damageDealt = new LinkedHashMap<>();
        Map<String, Float> damageTaken = new LinkedHashMap<>();
        JsonArray killsArray = new JsonArray();
        JsonArray deathsNearbyArray = new JsonArray();
        JsonObject playerDeathObj = null;

        JsonArray highlights = new JsonArray();

        for (GameEvent e : snapshot.getEvents()) {
            if (e instanceof GameEvent.BlockBreakEvent bbe) {
                blocksBroken.merge(bbe.getBlock(), 1, Integer::sum);
            } else if (e instanceof GameEvent.BlockPlaceEvent bpe) {
                blocksPlaced.merge(bpe.getBlock(), 1, Integer::sum);
            } else if (e instanceof GameEvent.ItemPickupEvent ipe) {
                itemsGained.merge(ipe.getItem(), ipe.getCount(), Integer::sum);
            } else if (e instanceof GameEvent.ItemDropEvent ide) {
                itemsLost.merge(ide.getItem(), ide.getCount(), Integer::sum);
            } else if (e instanceof GameEvent.MobKillEvent mke) {
                mobsKilled.merge(mke.getEntity(), 1, Integer::sum);
                JsonObject k = new JsonObject();
                k.addProperty("entity", mke.getEntity());
                k.addProperty("t", mke.getT());
                k.addProperty("weapon", mke.getWeapon());
                k.addProperty("damage", mke.getDamage());
                killsArray.add(k);
            } else if (e instanceof GameEvent.DeathNearbyEvent dne) {
                JsonObject d = new JsonObject();
                d.addProperty("entity", dne.getEntity());
                d.addProperty("cause", dne.getCause());
                d.addProperty("t", dne.getT());
                if (dne.getPos() != null) {
                    JsonObject p = new JsonObject();
                    p.addProperty("x", dne.getPos().getX());
                    p.addProperty("y", dne.getPos().getY());
                    p.addProperty("z", dne.getPos().getZ());
                    d.add("pos", p);
                } else {
                    d.add("pos", null);
                }
                d.addProperty("distance", dne.getDistance());
                deathsNearbyArray.add(d);
            } else if (e instanceof GameEvent.ContainerOpenEvent coe) {
                containersOpened.merge(coe.getContainer(), 1, Integer::sum);
            } else if (e instanceof GameEvent.StationUseEvent sue) {
                stationsUsed.merge(sue.getStation(), 1, Integer::sum);
            } else if (e instanceof GameEvent.DamageDealtEvent dde) {
                damageDealt.merge(dde.getTargetEntity(), dde.getDamage(), Float::sum);
            } else if (e instanceof GameEvent.DamageTakenEvent dte) {
                damageTaken.merge(dte.getSource(), dte.getDamage(), Float::sum);
            } else if (e instanceof GameEvent.PlayerDeathEvent pde) {
                playerDeathObj = new JsonObject();
                playerDeathObj.addProperty("cause", pde.getCause());
                playerDeathObj.addProperty("t", pde.getT());
                if (pde.getPos() != null) {
                    JsonObject p = new JsonObject();
                    p.addProperty("x", pde.getPos().getX());
                    p.addProperty("y", pde.getPos().getY());
                    p.addProperty("z", pde.getPos().getZ());
                    playerDeathObj.add("pos", p);
                }
                playerDeathObj.addProperty("killer_entity", pde.getKillerEntity());
                playerDeathObj.addProperty("killer_player", pde.getKillerPlayer());

                JsonObject hl = new JsonObject();
                hl.addProperty("type", "player_death");
                hl.addProperty("text", "morto por " + (pde.getKillerEntity() != null ? pde.getKillerEntity() : pde.getCause()));
                hl.addProperty("t", pde.getT());
                highlights.add(hl);
            } else if (e instanceof GameEvent.AdvancementEvent ae) {
                JsonObject hl = new JsonObject();
                hl.addProperty("type", "advancement");
                hl.addProperty("text", ae.getText());
                hl.addProperty("t", ae.getT());
                highlights.add(hl);
            } else if (e instanceof GameEvent.EffectChangeEvent ece) {
                JsonObject hl = new JsonObject();
                if (ece.isAdded()) {
                    hl.addProperty("type", "effect_gained");
                    hl.addProperty("effect", ece.getEffectId());
                    hl.addProperty("amplifier", ece.getAmplifier());
                } else {
                    hl.addProperty("type", "effect_lost");
                    hl.addProperty("effect", ece.getEffectId());
                }
                hl.addProperty("t", ece.getT());
                highlights.add(hl);
            } else if (e instanceof GameEvent.FoodLowEvent fle) {
                JsonObject hl = new JsonObject();
                hl.addProperty("type", "food_low");
                hl.addProperty("food", fle.getFood());
                hl.addProperty("t", fle.getT());
                highlights.add(hl);
            } else if (e instanceof GameEvent.HealthLowEvent hle) {
                JsonObject hl = new JsonObject();
                hl.addProperty("type", "health_low");
                hl.addProperty("health", hle.getHealth());
                hl.addProperty("t", hle.getT());
                highlights.add(hl);
            } else if (e instanceof GameEvent.BiomeChangeEvent bce) {
                JsonObject hl = new JsonObject();
                hl.addProperty("type", "biome_change");
                hl.addProperty("from_biome", bce.getFrom());
                hl.addProperty("to_biome", bce.getTo());
                hl.addProperty("t", bce.getT());
                highlights.add(hl);
            } else if (e instanceof GameEvent.DimensionChangeEvent dce) {
                JsonObject hl = new JsonObject();
                hl.addProperty("type", "dimension_change");
                hl.addProperty("from_dim", dce.getFrom());
                hl.addProperty("to_dim", dce.getTo());
                hl.addProperty("t", dce.getT());
                highlights.add(hl);
            } else if (e instanceof GameEvent.WeatherChangeEvent wce) {
                JsonObject hl = new JsonObject();
                hl.addProperty("type", "weather_change");
                hl.addProperty("weather_event", wce.getEvent());
                hl.addProperty("t", wce.getT());
                highlights.add(hl);
            }
        }

        JsonObject bpObj = new JsonObject();
        blocksPlaced.forEach(bpObj::addProperty);
        activity.add("blocks_placed", bpObj);

        JsonObject bbObj = new JsonObject();
        blocksBroken.forEach(bbObj::addProperty);
        activity.add("blocks_broken", bbObj);

        JsonObject igObj = new JsonObject();
        itemsGained.forEach(igObj::addProperty);
        activity.add("items_gained", igObj);

        JsonObject ilObj = new JsonObject();
        itemsLost.forEach(ilObj::addProperty);
        activity.add("items_lost", ilObj);

        JsonObject mkObj = new JsonObject();
        mobsKilled.forEach(mkObj::addProperty);
        activity.add("mobs_killed", mkObj);

        JsonObject coObj = new JsonObject();
        containersOpened.forEach(coObj::addProperty);
        activity.add("containers_opened", coObj);

        JsonObject suObj = new JsonObject();
        stationsUsed.forEach(suObj::addProperty);
        activity.add("stations_used", suObj);

        // combat_window
        JsonObject combatWindow = new JsonObject();
        JsonObject ddObj = new JsonObject();
        damageDealt.forEach(ddObj::addProperty);
        combatWindow.add("damage_dealt", ddObj);

        JsonObject dtObj = new JsonObject();
        damageTaken.forEach(dtObj::addProperty);
        combatWindow.add("damage_taken", dtObj);

        combatWindow.add("kills", killsArray);
        combatWindow.add("deaths_nearby", deathsNearbyArray);
        if (playerDeathObj != null) {
            combatWindow.add("player_death", playerDeathObj);
        } else {
            combatWindow.add("player_death", null);
        }
        activity.add("combat_window", combatWindow);

        root.add("activity", activity);
        root.add("highlights", highlights);

        return root;
    }
}
