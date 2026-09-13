package com.example.ainarrator;

import com.example.ainarrator.collector.EventCollector;
import com.example.ainarrator.command.NarratorCommand;
import com.example.ainarrator.config.ModConfig;
import com.example.ainarrator.io.AtomicJsonWriter;
import com.example.ainarrator.io.TriggerWatcher;
import com.example.ainarrator.state.ModState;
import com.example.ainarrator.state.StateBuilder;
import com.example.ainarrator.window.WindowBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public class AINarratorMod implements ModInitializer {
    public static final String MOD_ID = "ai-narrator";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static AINarratorMod instance;
    private ModConfig config;
    private EventCollector collector;
    private TriggerWatcher triggerWatcher;
    private Thread triggerThread;

    private volatile boolean triggerPending = false;
    private long lastTriggerTime = System.currentTimeMillis();
    private long lastHeartbeatTime = 0;

    @Override
    public void onInitialize() {
        instance = this;
        LOGGER.info("[AI-Narrator] Inicializando AI Narrator Mod 0.1.0...");

        // 1. Carrega config
        this.config = ModConfig.load();

        // 2. Coletor de eventos
        this.collector = EventCollector.getInstance();
        this.collector.setMaxEvents(config.getMaxRingBufferEvents());
        this.collector.registerHandlers();

        // 3. Trigger Watcher
        this.triggerWatcher = new TriggerWatcher(config.getBridgePath(), config.getTriggerPollMs(), () -> {
            this.triggerPending = true;
        });
        this.triggerThread = new Thread(triggerWatcher, "AI-Narrator-TriggerWatcher");
        this.triggerThread.setDaemon(true);
        this.triggerThread.start();

        // 4. Tick do servidor
        ServerTickEvents.END_SERVER_TICK.register(this::onEndServerTick);

        // 5. Lifecycle do servidor
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);

        // 6. Comandos
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> {
            NarratorCommand.register(dispatcher, collector, config);
        });

        LOGGER.info("[AI-Narrator] Mod inicializado com sucesso. Bridge Path: {}", config.getBridgePath());
    }

    private void onServerStarted(MinecraftServer server) {
        LOGGER.info("[AI-Narrator] Servidor iniciado. Resetando seq e escrevendo state.json inicial...");
        ModState.resetSeq();
        collector.reset();
        lastTriggerTime = System.currentTimeMillis();

        try {
            JsonObject state = StateBuilder.build(server, config);
            AtomicJsonWriter.write(config.getBridgePath().resolve("state.json"), state);
            writeHeartbeat(server);
        } catch (Exception e) {
            LOGGER.error("[AI-Narrator] Falha ao escrever state.json inicial: {}", e.getMessage());
        }
    }

    private void onServerStopping(MinecraftServer server) {
        LOGGER.info("[AI-Narrator] Servidor parando. Encerrando threads...");
        if (triggerWatcher != null) {
            triggerWatcher.stop();
        }
        if (triggerThread != null) {
            triggerThread.interrupt();
        }

        try {
            ModState.setPaused(true);
            JsonObject state = StateBuilder.build(server, config);
            AtomicJsonWriter.write(config.getBridgePath().resolve("state.json"), state);
        } catch (Exception e) {
            LOGGER.error("[AI-Narrator] Falha ao salvar state final: {}", e.getMessage());
        }
    }

    private void onEndServerTick(MinecraftServer server) {
        long now = System.currentTimeMillis();

        // Tick no coletor (verificar biomas, fome, vida, etc.)
        collector.tick(server, config);

        // Processar trigger pendente ou timeout de 60s em mod-only
        boolean shouldTrigger = triggerPending || (now - lastTriggerTime >= 60000);
        if (shouldTrigger) {
            triggerPending = false;
            lastTriggerTime = now;
            processTrigger(server);
        }

        // Heartbeat periódico a cada heartbeatIntervalMs
        if (now - lastHeartbeatTime >= config.getHeartbeatIntervalMs()) {
            lastHeartbeatTime = now;
            writeHeartbeat(server);
        }
    }

    private void processTrigger(MinecraftServer server) {
        LOGGER.info("[AI-Narrator] Processando trigger para seq={}", ModState.getSeq());
        try {
            Path bridgeDir = config.getBridgePath();

            // 1. Snapshot da janela antes do reset
            JsonObject window = WindowBuilder.build(collector.buildWindow());

            // 2. Reset do buffer da janela
            collector.reset();

            // 3. Snapshot do estado atual
            JsonObject state = StateBuilder.build(server, config);

            // 4. Escritas atômicas
            AtomicJsonWriter.write(bridgeDir.resolve("state.json"), state);
            AtomicJsonWriter.write(bridgeDir.resolve("window.json"), window);
            writeHeartbeat(server);

            // 5. Incrementa seq
            ModState.incrementSeq();
        } catch (Exception e) {
            LOGGER.error("[AI-Narrator] Erro ao processar trigger: {}", e.getMessage(), e);
        }
    }

    private void writeHeartbeat(MinecraftServer server) {
        try {
            JsonObject hb = new JsonObject();
            hb.addProperty("last_seen", System.currentTimeMillis());
            hb.addProperty("tps", 20.0);
            hb.addProperty("player_count", server.getCurrentPlayerCount());
            hb.addProperty("mod_version", "0.1.0");
            hb.addProperty("seq", ModState.getSeq());

            AtomicJsonWriter.write(config.getBridgePath().resolve("heartbeat.json"), hb);
        } catch (IOException e) {
            LOGGER.warn("[AI-Narrator] Falha ao escrever heartbeat.json: {}", e.getMessage());
        }
    }

    public static AINarratorMod getInstance() {
        return instance;
    }

    public ModConfig getConfig() {
        return config;
    }
}
