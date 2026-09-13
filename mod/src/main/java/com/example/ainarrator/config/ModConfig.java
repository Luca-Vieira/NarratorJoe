package com.example.ainarrator.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ModConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("AI-Narrator-Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Path bridgePath;
    private long heartbeatIntervalMs = 5000;
    private long triggerPollMs = 1000;
    private int maxRingBufferEvents = 500;
    private int entitiesScanRadius = 32;
    private int entitiesScanIntervalTicks = 20;
    private int homeRadius = 32;
    private long homeDwellThresholdMs = 300000;
    private int foodLowThreshold = 5;
    private int healthLowThreshold = 6;
    private String logLevel = "INFO";

    public ModConfig() {
        this.bridgePath = Paths.get(System.getProperty("user.home"), "ai-narrator");
    }

    public static ModConfig load() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path configFile = configDir.resolve("ai-narrator.json");

        ModConfig config = new ModConfig();

        if (Files.exists(configFile)) {
            try {
                String text = Files.readString(configFile, StandardCharsets.UTF_8);
                JsonObject json = JsonParser.parseString(text).getAsJsonObject();

                if (json.has("bridge_path")) {
                    String p = json.get("bridge_path").getAsString();
                    if (p.startsWith("~")) {
                        p = System.getProperty("user.home") + p.substring(1);
                    }
                    config.bridgePath = Paths.get(p);
                }
                if (json.has("heartbeat_interval_ms")) config.heartbeatIntervalMs = json.get("heartbeat_interval_ms").getAsLong();
                if (json.has("trigger_poll_ms")) config.triggerPollMs = json.get("trigger_poll_ms").getAsLong();
                if (json.has("max_ring_buffer_events")) config.maxRingBufferEvents = json.get("max_ring_buffer_events").getAsInt();
                if (json.has("entities_scan_radius")) config.entitiesScanRadius = json.get("entities_scan_radius").getAsInt();
                if (json.has("entities_scan_interval_ticks")) config.entitiesScanIntervalTicks = json.get("entities_scan_interval_ticks").getAsInt();
                if (json.has("home_radius")) config.homeRadius = json.get("home_radius").getAsInt();
                if (json.has("home_dwell_threshold_ms")) config.homeDwellThresholdMs = json.get("home_dwell_threshold_ms").getAsLong();
                if (json.has("food_low_threshold")) config.foodLowThreshold = json.get("food_low_threshold").getAsInt();
                if (json.has("health_low_threshold")) config.healthLowThreshold = json.get("health_low_threshold").getAsInt();
                if (json.has("log_level")) config.logLevel = json.get("log_level").getAsString();
            } catch (Exception e) {
                LOGGER.error("Failed to parse ai-narrator.json, keeping defaults: {}", e.getMessage());
            }
        } else {
            save(config, configFile);
        }

        try {
            Files.createDirectories(config.bridgePath);
        } catch (IOException e) {
            LOGGER.error("Failed to create bridge path {}: {}", config.bridgePath, e.getMessage());
        }

        return config;
    }

    public static void save(ModConfig config, Path configFile) {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("bridge_path", config.bridgePath.toString());
            json.addProperty("heartbeat_interval_ms", config.heartbeatIntervalMs);
            json.addProperty("trigger_poll_ms", config.triggerPollMs);
            json.addProperty("max_ring_buffer_events", config.maxRingBufferEvents);
            json.addProperty("entities_scan_radius", config.entitiesScanRadius);
            json.addProperty("entities_scan_interval_ticks", config.entitiesScanIntervalTicks);
            json.addProperty("home_radius", config.homeRadius);
            json.addProperty("home_dwell_threshold_ms", config.homeDwellThresholdMs);
            json.addProperty("food_low_threshold", config.foodLowThreshold);
            json.addProperty("health_low_threshold", config.healthLowThreshold);
            json.addProperty("log_level", config.logLevel);

            if (configFile.getParent() != null) {
                Files.createDirectories(configFile.getParent());
            }
            Files.writeString(configFile, GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.error("Failed to save config to {}: {}", configFile, e.getMessage());
        }
    }

    public Path getBridgePath() { return bridgePath; }
    public long getHeartbeatIntervalMs() { return heartbeatIntervalMs; }
    public long getTriggerPollMs() { return triggerPollMs; }
    public int getMaxRingBufferEvents() { return maxRingBufferEvents; }
    public int getEntitiesScanRadius() { return entitiesScanRadius; }
    public int getEntitiesScanIntervalTicks() { return entitiesScanIntervalTicks; }
    public int getHomeRadius() { return homeRadius; }
    public long getHomeDwellThresholdMs() { return homeDwellThresholdMs; }
    public int getFoodLowThreshold() { return foodLowThreshold; }
    public int getHealthLowThreshold() { return healthLowThreshold; }
    public String getLogLevel() { return logLevel; }
}
