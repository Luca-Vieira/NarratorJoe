package com.example.ainarrator.collector;

import com.example.ainarrator.config.ModConfig;
import com.example.ainarrator.state.PlayerTracker;
import com.example.ainarrator.state.StateBuilder;
import com.example.ainarrator.window.WindowSnapshot;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class EventCollector {
    private static final EventCollector INSTANCE = new EventCollector();

    private final ConcurrentLinkedQueue<GameEvent> events = new ConcurrentLinkedQueue<>();
    private volatile long windowStart = System.currentTimeMillis();
    private int maxEvents = 500;

    private record PendingPlace(String player, ServerWorld world, BlockPos pos, Block block) {}
    private final Map<UUID, PendingPlace> pendingPlaces = new HashMap<>();

    public static EventCollector getInstance() {
        return INSTANCE;
    }

    public void setMaxEvents(int max) {
        this.maxEvents = max;
    }

    public void add(GameEvent event) {
        events.add(event);
        while (events.size() > maxEvents) {
            events.poll();
        }
    }

    public void registerHandlers() {
        // 1. Block break
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
            add(new GameEvent.BlockBreakEvent(player.getName().getString(), blockId, pos));
        });

        // 2. Block place & interact
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (hand != Hand.MAIN_HAND || !(world instanceof ServerWorld sw)) return ActionResult.PASS;
            ItemStack held = player.getStackInHand(hand);
            if (held.getItem() instanceof BlockItem bi) {
                BlockPos clicked = hit.getBlockPos();
                BlockPos target = sw.getBlockState(clicked).isReplaceable() ? clicked : clicked.offset(hit.getSide());
                synchronized (pendingPlaces) {
                    pendingPlaces.put(player.getUuid(), new PendingPlace(player.getName().getString(), sw, target, bi.getBlock()));
                }
            } else {
                String blockId = Registries.BLOCK.getId(sw.getBlockState(hit.getBlockPos()).getBlock()).toString();
                if (blockId.contains("chest") || blockId.contains("barrel") || blockId.contains("shulker_box")) {
                    add(new GameEvent.ContainerOpenEvent(player.getName().getString(), blockId));
                } else if (blockId.contains("furnace") || blockId.contains("smoker") || blockId.contains("blast_furnace") ||
                           blockId.contains("brewing_stand") || blockId.contains("crafting_table") || blockId.contains("anvil") ||
                           blockId.contains("smithing_table") || blockId.contains("enchanting_table")) {
                    add(new GameEvent.StationUseEvent(player.getName().getString(), blockId));
                }
            }
            return ActionResult.PASS;
        });

        // 3. Combat damage
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (source.getAttacker() instanceof ServerPlayerEntity p) {
                String targetId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
                add(new GameEvent.DamageDealtEvent(p.getName().getString(), targetId, amount));
            }
            if (entity instanceof ServerPlayerEntity p) {
                String src = source.getName();
                Entity attacker = source.getAttacker();
                if (attacker != null) {
                    src = Registries.ENTITY_TYPE.getId(attacker.getType()).toString();
                }
                add(new GameEvent.DamageTakenEvent(p.getName().getString(), src, amount));
            }
            return true;
        });

        // 4. Combat kills & player death & deaths nearby
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity p) {
                String killerEntity = null;
                String killerPlayer = null;
                Entity atk = source.getAttacker();
                if (atk instanceof ServerPlayerEntity kp) {
                    killerPlayer = kp.getName().getString();
                } else if (atk != null) {
                    killerEntity = Registries.ENTITY_TYPE.getId(atk.getType()).toString();
                }
                add(new GameEvent.PlayerDeathEvent(p.getName().getString(), source.getName(), killerEntity, killerPlayer, p.getBlockPos()));
            } else if (!(entity instanceof ArmorStandEntity)) {
                if (source.getAttacker() instanceof ServerPlayerEntity killer) {
                    String entityId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
                    ItemStack weapon = killer.getMainHandStack();
                    String weaponId = weapon.isEmpty() ? null : Registries.ITEM.getId(weapon.getItem()).toString();
                    add(new GameEvent.MobKillEvent(killer.getName().getString(), entityId, weaponId, entity.getMaxHealth()));
                } else {
                    for (net.minecraft.entity.player.PlayerEntity p : entity.getWorld().getPlayers()) {
                        double distSq = p.squaredDistanceTo(entity);
                        if (distSq <= 32 * 32) {
                            add(new GameEvent.DeathNearbyEvent(
                                Registries.ENTITY_TYPE.getId(entity.getType()).toString(),
                                source.getName(),
                                entity.getBlockPos(),
                                Math.round(Math.sqrt(distSq) * 10) / 10.0
                            ));
                        }
                    }
                }
            }
        });

        // 5. Advancements
        ServerMessageEvents.GAME_MESSAGE.register((server, message, overlay) -> {
            if (message.getContent() instanceof TranslatableTextContent t
                    && t.getKey().startsWith("chat.type.advancement")) {
                add(new GameEvent.AdvancementEvent(message.getString()));
            }
        });

        // 6. Respawn
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            // Player respawned
        });
    }

    public void tick(MinecraftServer server, ModConfig config) {
        verifyPendingPlaces();

        long ticks = server.getTicks();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PlayerTracker tracker = StateBuilder.getOrCreateTracker(player);
            tracker.tick(player, config, this);

            if (ticks % config.getEntitiesScanIntervalTicks() == 0) {
                tracker.updateEntitiesNearby(player, config);
            }
        }
    }

    private void verifyPendingPlaces() {
        synchronized (pendingPlaces) {
            if (pendingPlaces.isEmpty()) return;
            Iterator<Map.Entry<UUID, PendingPlace>> it = pendingPlaces.entrySet().iterator();
            while (it.hasNext()) {
                PendingPlace pp = it.next().getValue();
                it.remove();
                if (pp.world().getBlockState(pp.pos()).getBlock() == pp.block()) {
                    String blockId = Registries.BLOCK.getId(pp.block()).toString();
                    add(new GameEvent.BlockPlaceEvent(pp.player(), blockId, pp.pos()));
                }
            }
        }
    }

    public WindowSnapshot buildWindow() {
        long now = System.currentTimeMillis();
        WindowSnapshot snapshot = new WindowSnapshot(windowStart, now);
        for (GameEvent e : events) {
            snapshot.addEvent(e);
        }
        return snapshot;
    }

    public void reset() {
        events.clear();
        windowStart = System.currentTimeMillis();
    }
}
