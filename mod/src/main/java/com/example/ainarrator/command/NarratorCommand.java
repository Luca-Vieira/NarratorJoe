package com.example.ainarrator.command;

import com.example.ainarrator.collector.EventCollector;
import com.example.ainarrator.config.ModConfig;
import com.example.ainarrator.state.ModState;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class NarratorCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                EventCollector collector,
                                ModConfig config) {
        dispatcher.register(CommandManager.literal("narrator")
            .requires(src -> src.hasPermissionLevel(2))
            .then(CommandManager.literal("pause")
                .executes(ctx -> {
                    ModState.setPaused(true);
                    ctx.getSource().sendFeedback(() -> Text.literal("§6[AI-Narrator]§r Narrador pausado pelo jogador."), false);
                    return 1;
                }))
            .then(CommandManager.literal("resume")
                .executes(ctx -> {
                    ModState.setPaused(false);
                    ctx.getSource().sendFeedback(() -> Text.literal("§a[AI-Narrator]§r Narrador retomado."), false);
                    return 1;
                }))
            .then(CommandManager.literal("status")
                .executes(ctx -> {
                    boolean paused = ModState.isPaused();
                    long seq = ModState.getSeq();
                    String path = config.getBridgePath().toString();
                    ctx.getSource().sendFeedback(() -> Text.literal(
                        "§b[AI-Narrator Status]§r\n" +
                        "  Estado: " + (paused ? "§cPAUSADO§r" : "§aATIVO§r") + "\n" +
                        "  Seq atual: §e" + seq + "§r\n" +
                        "  Bridge Path: §7" + path + "§r\n" +
                        "  Versão: §70.1.0§r"
                    ), false);
                    return 1;
                }))
            .then(CommandManager.literal("reload")
                .executes(ctx -> {
                    ModConfig.load();
                    ctx.getSource().sendFeedback(() -> Text.literal("§a[AI-Narrator]§r Configuração recarregada de ai-narrator.json"), false);
                    return 1;
                }))
        );
    }
}
