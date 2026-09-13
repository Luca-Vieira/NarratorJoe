package com.example.ainarrator.collector;

import net.minecraft.util.math.BlockPos;

public abstract class GameEvent {
    private final long t;

    public GameEvent() {
        this.t = System.currentTimeMillis();
    }

    public GameEvent(long t) {
        this.t = t;
    }

    public long getT() {
        return t;
    }

    public static class BlockBreakEvent extends GameEvent {
        private final String player;
        private final String block;
        private final BlockPos pos;

        public BlockBreakEvent(String player, String block, BlockPos pos) {
            this.player = player;
            this.block = block;
            this.pos = pos;
        }

        public String getPlayer() { return player; }
        public String getBlock() { return block; }
        public BlockPos getPos() { return pos; }
    }

    public static class BlockPlaceEvent extends GameEvent {
        private final String player;
        private final String block;
        private final BlockPos pos;

        public BlockPlaceEvent(String player, String block, BlockPos pos) {
            this.player = player;
            this.block = block;
            this.pos = pos;
        }

        public String getPlayer() { return player; }
        public String getBlock() { return block; }
        public BlockPos getPos() { return pos; }
    }

    public static class ItemPickupEvent extends GameEvent {
        private final String player;
        private final String item;
        private final int count;

        public ItemPickupEvent(String player, String item, int count) {
            this.player = player;
            this.item = item;
            this.count = count;
        }

        public String getPlayer() { return player; }
        public String getItem() { return item; }
        public int getCount() { return count; }
    }

    public static class ItemDropEvent extends GameEvent {
        private final String player;
        private final String item;
        private final int count;

        public ItemDropEvent(String player, String item, int count) {
            this.player = player;
            this.item = item;
            this.count = count;
        }

        public String getPlayer() { return player; }
        public String getItem() { return item; }
        public int getCount() { return count; }
    }

    public static class MobKillEvent extends GameEvent {
        private final String player;
        private final String entity;
        private final String weapon;
        private final double damage;

        public MobKillEvent(String player, String entity, String weapon, double damage) {
            this.player = player;
            this.entity = entity;
            this.weapon = weapon;
            this.damage = damage;
        }

        public String getPlayer() { return player; }
        public String getEntity() { return entity; }
        public String getWeapon() { return weapon; }
        public double getDamage() { return damage; }
    }

    public static class DeathNearbyEvent extends GameEvent {
        private final String entity;
        private final String cause;
        private final BlockPos pos;
        private final double distance;

        public DeathNearbyEvent(String entity, String cause, BlockPos pos, double distance) {
            this.entity = entity;
            this.cause = cause;
            this.pos = pos;
            this.distance = distance;
        }

        public String getEntity() { return entity; }
        public String getCause() { return cause; }
        public BlockPos getPos() { return pos; }
        public double getDistance() { return distance; }
    }

    public static class ContainerOpenEvent extends GameEvent {
        private final String player;
        private final String container;

        public ContainerOpenEvent(String player, String container) {
            this.player = player;
            this.container = container;
        }

        public String getPlayer() { return player; }
        public String getContainer() { return container; }
    }

    public static class StationUseEvent extends GameEvent {
        private final String player;
        private final String station;

        public StationUseEvent(String player, String station) {
            this.player = player;
            this.station = station;
        }

        public String getPlayer() { return player; }
        public String getStation() { return station; }
    }

    public static class DamageDealtEvent extends GameEvent {
        private final String player;
        private final String targetEntity;
        private final float damage;

        public DamageDealtEvent(String player, String targetEntity, float damage) {
            this.player = player;
            this.targetEntity = targetEntity;
            this.damage = damage;
        }

        public String getPlayer() { return player; }
        public String getTargetEntity() { return targetEntity; }
        public float getDamage() { return damage; }
    }

    public static class DamageTakenEvent extends GameEvent {
        private final String player;
        private final String source;
        private final float damage;

        public DamageTakenEvent(String player, String source, float damage) {
            this.player = player;
            this.source = source;
            this.damage = damage;
        }

        public String getPlayer() { return player; }
        public String getSource() { return source; }
        public float getDamage() { return damage; }
    }

    public static class PlayerDeathEvent extends GameEvent {
        private final String player;
        private final String cause;
        private final String killerEntity;
        private final String killerPlayer;
        private final BlockPos pos;

        public PlayerDeathEvent(String player, String cause, String killerEntity, String killerPlayer, BlockPos pos) {
            this.player = player;
            this.cause = cause;
            this.killerEntity = killerEntity;
            this.killerPlayer = killerPlayer;
            this.pos = pos;
        }

        public String getPlayer() { return player; }
        public String getCause() { return cause; }
        public String getKillerEntity() { return killerEntity; }
        public String getKillerPlayer() { return killerPlayer; }
        public BlockPos getPos() { return pos; }
    }

    public static class AdvancementEvent extends GameEvent {
        private final String text;

        public AdvancementEvent(String text) {
            this.text = text;
        }

        public String getText() { return text; }
    }

    public static class EffectChangeEvent extends GameEvent {
        private final String effectId;
        private final int amplifier;
        private final boolean added;

        public EffectChangeEvent(String effectId, int amplifier, boolean added) {
            this.effectId = effectId;
            this.amplifier = amplifier;
            this.added = added;
        }

        public String getEffectId() { return effectId; }
        public int getAmplifier() { return amplifier; }
        public boolean isAdded() { return added; }
    }

    public static class FoodLowEvent extends GameEvent {
        private final int food;

        public FoodLowEvent(int food) {
            this.food = food;
        }

        public int getFood() { return food; }
    }

    public static class HealthLowEvent extends GameEvent {
        private final float health;

        public HealthLowEvent(float health) {
            this.health = health;
        }

        public float getHealth() { return health; }
    }

    public static class BiomeChangeEvent extends GameEvent {
        private final String from;
        private final String to;

        public BiomeChangeEvent(String from, String to) {
            this.from = from;
            this.to = to;
        }

        public String getFrom() { return from; }
        public String getTo() { return to; }
    }

    public static class DimensionChangeEvent extends GameEvent {
        private final String from;
        private final String to;

        public DimensionChangeEvent(String from, String to) {
            this.from = from;
            this.to = to;
        }

        public String getFrom() { return from; }
        public String getTo() { return to; }
    }

    public static class WeatherChangeEvent extends GameEvent {
        private final String event;

        public WeatherChangeEvent(String event) {
            this.event = event;
        }

        public String getEvent() { return event; }
    }
}
