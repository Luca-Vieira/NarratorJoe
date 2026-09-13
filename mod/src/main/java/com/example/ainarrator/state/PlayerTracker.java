package com.example.ainarrator.state;

import com.example.ainarrator.collector.GameEvent;
import com.example.ainarrator.collector.EventCollector;
import com.example.ainarrator.config.ModConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.*;

public class PlayerTracker {
    private final UUID playerUuid;
    private BiomeHistory biomeHistory;
    private SpawnHistory spawnHistory;
    private final LinkedList<HeldItem> heldItemsRecent = new LinkedList<>();
    private EntitiesNearby entitiesNearby = new EntitiesNearby();

    private int lastFood = 20;
    private float lastHealth = 20.0f;
    private String lastDimension = "";
    private boolean lastRaining = false;
    private boolean lastThundering = false;
    private String lastHeldItemId = "";

    public PlayerTracker(ServerPlayerEntity player) {
        this.playerUuid = player.getUuid();
        ServerWorld world = (ServerWorld) player.getWorld();
        String biomeId = world.getBiome(player.getBlockPos()).getKey().map(k -> k.getValue().toString()).orElse("minecraft:plains");
        this.biomeHistory = new BiomeHistory(biomeId, player.getBlockPos());

        BlockPos spawnPos = player.getSpawnPointPosition();
        if (spawnPos == null) spawnPos = player.getBlockPos();
        this.spawnHistory = new SpawnHistory(spawnPos);

        this.lastFood = player.getHungerManager().getFoodLevel();
        this.lastHealth = player.getHealth();
        this.lastDimension = world.getRegistryKey().getValue().toString();
        this.lastRaining = world.isRaining();
        this.lastThundering = world.isThundering();

        ItemStack held = player.getMainHandStack();
        String heldId = held.isEmpty() ? "minecraft:air" : Registries.ITEM.getId(held.getItem()).toString();
        this.lastHeldItemId = heldId;
        heldItemsRecent.addFirst(new HeldItem(heldId, System.currentTimeMillis()));
    }

    public void tick(ServerPlayerEntity player, ModConfig config, EventCollector collector) {
        ServerWorld world = (ServerWorld) player.getWorld();
        BlockPos currentPos = player.getBlockPos();
        long now = System.currentTimeMillis();

        // 1. Biome change
        String currentBiome = world.getBiome(currentPos).getKey().map(k -> k.getValue().toString()).orElse("minecraft:plains");
        BiomeEntry currentEntry = biomeHistory.getCurrent();
        if (currentEntry != null && !currentEntry.getId().equals(currentBiome)) {
            String prevBiome = currentEntry.getId();
            biomeHistory.updateBiome(currentBiome, currentPos);
            collector.add(new GameEvent.BiomeChangeEvent(prevBiome, currentBiome));
        }

        // 2. Dimension change
        String currentDim = world.getRegistryKey().getValue().toString();
        if (!lastDimension.isEmpty() && !currentDim.equals(lastDimension)) {
            collector.add(new GameEvent.DimensionChangeEvent(lastDimension, currentDim));
            lastDimension = currentDim;
        }

        // 3. Weather change
        boolean isRaining = world.isRaining();
        boolean isThundering = world.isThundering();
        if (isRaining != lastRaining) {
            collector.add(new GameEvent.WeatherChangeEvent(isRaining ? "rain_start" : "rain_stop"));
            lastRaining = isRaining;
        }
        if (isThundering != lastThundering) {
            collector.add(new GameEvent.WeatherChangeEvent(isThundering ? "thunder_start" : "thunder_stop"));
            lastThundering = isThundering;
        }

        // 4. Food low
        int currentFood = player.getHungerManager().getFoodLevel();
        if (currentFood < config.getFoodLowThreshold() && lastFood >= config.getFoodLowThreshold()) {
            collector.add(new GameEvent.FoodLowEvent(currentFood));
        }
        lastFood = currentFood;

        // 5. Health low
        float currentHealth = player.getHealth();
        if (currentHealth < config.getHealthLowThreshold() && lastHealth >= config.getHealthLowThreshold()) {
            collector.add(new GameEvent.HealthLowEvent(currentHealth));
        }
        lastHealth = currentHealth;

        // 6. Spawn update & dwell calculation (tick = 50ms)
        BlockPos spawnPos = player.getSpawnPointPosition();
        if (spawnPos != null) {
            spawnHistory.updateSpawn(spawnPos);
            SpawnEntry currentSpawn = spawnHistory.getCurrent();
            if (currentSpawn != null) {
                double distSq = currentPos.getSquaredDistance(currentSpawn.getPos());
                if (distSq <= config.getHomeRadius() * config.getHomeRadius()) {
                    currentSpawn.addDwellMs(50);
                    if (currentSpawn.getDwellMs() >= config.getHomeDwellThresholdMs()) {
                        currentSpawn.setHome(true);
                    }
                }
            }
        }

        // 7. Held item
        ItemStack held = player.getMainHandStack();
        String heldId = held.isEmpty() ? "minecraft:air" : Registries.ITEM.getId(held.getItem()).toString();
        if (!heldId.equals(lastHeldItemId)) {
            lastHeldItemId = heldId;
            heldItemsRecent.addFirst(new HeldItem(heldId, now));
            while (heldItemsRecent.size() > 5) {
                heldItemsRecent.removeLast();
            }
        }
    }

    public void updateEntitiesNearby(ServerPlayerEntity player, ModConfig config) {
        ServerWorld world = (ServerWorld) player.getWorld();
        int radius = config.getEntitiesScanRadius();
        Box box = player.getBoundingBox().expand(radius);

        EntitiesNearby near = new EntitiesNearby();
        int otherPlayers = 0;
        int count = 0;

        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (e == player || e instanceof ArmorStandEntity) continue;
            if (count++ >= 64) break; // Limit 64

            if (e instanceof ServerPlayerEntity) {
                otherPlayers++;
                continue;
            }

            String entityId = Registries.ENTITY_TYPE.getId(e.getType()).toString();
            String category = classify(e);
            switch (category) {
                case "hostil" -> near.getHostil().merge(entityId, 1, Integer::sum);
                case "passivo" -> near.getPassivo().merge(entityId, 1, Integer::sum);
                default -> near.getNeutro().merge(entityId, 1, Integer::sum);
            }
        }
        near.setPlayers(otherPlayers);
        this.entitiesNearby = near;
    }

    private String classify(LivingEntity e) {
        SpawnGroup group = e.getType().getSpawnGroup();
        if (group == SpawnGroup.MONSTER) return "hostil";
        if (group == SpawnGroup.CREATURE || group == SpawnGroup.AMBIENT ||
            group == SpawnGroup.WATER_CREATURE || group == SpawnGroup.WATER_AMBIENT ||
            group == SpawnGroup.UNDERGROUND_WATER_CREATURE || group == SpawnGroup.AXOLOTLS) {
            return "passivo";
        }
        return "neutro";
    }

    public BiomeHistory getBiomeHistory() { return biomeHistory; }
    public SpawnHistory getSpawnHistory() { return spawnHistory; }
    public List<HeldItem> getHeldItemsRecent() { return heldItemsRecent; }
    public EntitiesNearby getEntitiesNearby() { return entitiesNearby; }
}
