package com.example.ainarrator.state;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.example.ainarrator.config.ModConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StateBuilder {
    private static final Map<UUID, PlayerTracker> TRACKERS = new ConcurrentHashMap<>();

    public static PlayerTracker getOrCreateTracker(ServerPlayerEntity player) {
        return TRACKERS.computeIfAbsent(player.getUuid(), k -> new PlayerTracker(player));
    }

    public static JsonObject build(MinecraftServer server, ModConfig config) {
        long seq = ModState.getSeq();
        long now = System.currentTimeMillis();

        JsonObject root = new JsonObject();
        root.addProperty("seq", seq);
        root.addProperty("updated_at", now);

        ServerPlayerEntity player = null;
        if (!server.getPlayerManager().getPlayerList().isEmpty()) {
            player = server.getPlayerManager().getPlayerList().get(0);
        }

        if (player != null) {
            PlayerTracker tracker = getOrCreateTracker(player);
            ServerWorld world = (ServerWorld) player.getWorld();

            JsonObject playerObj = new JsonObject();
            playerObj.addProperty("name", player.getName().getString());
            playerObj.addProperty("dimension", world.getRegistryKey().getValue().toString());

            BlockPos pos = player.getBlockPos();
            JsonObject posObj = new JsonObject();
            posObj.addProperty("x", pos.getX());
            posObj.addProperty("y", pos.getY());
            posObj.addProperty("z", pos.getZ());
            playerObj.add("pos", posObj);

            playerObj.addProperty("health", Math.round(player.getHealth() * 10) / 10.0f);
            playerObj.addProperty("food", player.getHungerManager().getFoodLevel());

            long dayTime = world.getTimeOfDay() % 24000;
            if (dayTime < 0) dayTime += 24000;
            playerObj.addProperty("day_time", dayTime);
            playerObj.addProperty("period", calculatePeriod(dayTime));

            JsonObject weatherObj = new JsonObject();
            weatherObj.addProperty("raining", world.isRaining());
            weatherObj.addProperty("thundering", world.isThundering());
            playerObj.add("weather", weatherObj);

            playerObj.add("biome", tracker.getBiomeHistory().toJson());
            playerObj.add("spawn", tracker.getSpawnHistory().toJson());

            JsonArray heldArr = new JsonArray();
            for (HeldItem h : tracker.getHeldItemsRecent()) {
                heldArr.add(h.toJson());
            }
            playerObj.add("held_items_recent", heldArr);

            playerObj.add("entities_nearby", tracker.getEntitiesNearby().toJson());
            playerObj.addProperty("paused", ModState.isPaused());

            root.add("player", playerObj);
        } else {
            // Placeholder when no player is connected
            JsonObject playerObj = new JsonObject();
            playerObj.addProperty("name", "Nobody");
            playerObj.addProperty("dimension", "minecraft:overworld");
            JsonObject posObj = new JsonObject();
            posObj.addProperty("x", 0);
            posObj.addProperty("y", 64);
            posObj.addProperty("z", 0);
            playerObj.add("pos", posObj);
            playerObj.addProperty("health", 20.0f);
            playerObj.addProperty("food", 20);
            playerObj.addProperty("day_time", 6000);
            playerObj.addProperty("period", "dia");
            JsonObject weatherObj = new JsonObject();
            weatherObj.addProperty("raining", false);
            weatherObj.addProperty("thundering", false);
            playerObj.add("weather", weatherObj);
            playerObj.add("biome", new BiomeHistory("minecraft:plains", new BlockPos(0,64,0)).toJson());
            playerObj.add("spawn", new SpawnHistory(new BlockPos(0,64,0)).toJson());
            playerObj.add("held_items_recent", new JsonArray());
            playerObj.add("entities_nearby", new EntitiesNearby().toJson());
            playerObj.addProperty("paused", ModState.isPaused());
            root.add("player", playerObj);
        }

        return root;
    }

    public static String calculatePeriod(long dayTime) {
        if (dayTime < 2000) return "amanhecer";
        if (dayTime < 10000) return "dia";
        if (dayTime < 12000) return "entardecer";
        if (dayTime < 23000) return "noite";
        return "madrugada";
    }
}
