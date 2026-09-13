import json
import pytest
from narrator_bridge.schemas import State, Window, Heartbeat

def test_state_schema_parsing():
    raw_state = {
        "seq": 42,
        "updated_at": 1788398627884,
        "player": {
            "name": "BusinessCapybara",
            "dimension": "minecraft:overworld",
            "pos": {"x": 119, "y": 81, "z": 644},
            "health": 20.0,
            "food": 20,
            "day_time": 13400,
            "period": "noite",
            "weather": {"raining": False, "thundering": False},
            "biome": {
                "current": {"id": "minecraft:savanna", "since_t": 1788398000000, "pos": {"x": 119, "y": 81, "z": 644}},
                "previous": {"id": "minecraft:plains", "since_t": 1788397000000, "until_t": 1788398000000}
            },
            "spawn": {
                "current": {"pos": {"x": 120, "y": 80, "z": 650}, "set_t": 1788397000000, "is_home": True, "dwell_ms": 650000},
                "previous": {"pos": {"x": 10, "y": 65, "z": 10}, "set_t": 1788390000000, "is_home": False, "dwell_ms": 0}
            },
            "held_items_recent": [
                {"item": "minecraft:netherite_sword", "since_t": 1788398520000},
                {"item": "minecraft:iron_ingot", "since_t": 1788398500000}
            ],
            "entities_nearby": {
                "hostil": {"minecraft:zombie": 3},
                "passivo": {"minecraft:cow": 2},
                "neutro": {},
                "players": 0
            },
            "paused": False
        }
    }
    state = State.model_validate(raw_state)
    assert state.seq == 42
    assert state.player.name == "BusinessCapybara"
    assert state.player.health == 20.0
    assert state.player.biome.current.id == "minecraft:savanna"
    assert state.player.spawn.current.is_home is True

def test_window_schema_parsing_with_combat():
    raw_window = {
        "seq": 42,
        "window_start": 1788398567884,
        "window_end": 1788398627884,
        "activity": {
            "blocks_placed": {"minecraft:sand": 12},
            "blocks_broken": {"minecraft:short_grass": 14},
            "items_gained": {"minecraft:iron_ingot": 5},
            "items_lost": {"minecraft:acacia_planks": 264},
            "mobs_killed": {"minecraft:pig": 1, "minecraft:villager": 4},
            "containers_opened": {"minecraft:chest": 5},
            "stations_used": {"minecraft:furnace": 1, "minecraft:brewing_stand": 3},
            "combat_window": {
                "damage_dealt": {"minecraft:zombie": 18.5, "minecraft:pig": 6.0},
                "damage_taken": {"fall": 4.0, "minecraft:zombie": 8.0},
                "kills": [
                    {"entity": "minecraft:zombie", "t": 1788398583000, "weapon": "minecraft:netherite_sword", "damage": 18.5},
                    {"entity": "minecraft:pig", "t": 1788398590000, "weapon": "minecraft:netherite_sword", "damage": 6.0}
                ],
                "deaths_nearby": [
                    {"entity": "minecraft:villager", "cause": "mob", "t": 1788398595000, "pos": {"x": 125, "y": 80, "z": 640}, "distance": 7.2}
                ],
                "player_death": None
            }
        },
        "highlights": [
            {"type": "advancement", "text": "Acquire Hardware", "t": 1788398583816},
            {"type": "effect_gained", "effect": "minecraft:poison", "amplifier": 1, "t": 1788398583000},
            {"type": "food_low", "food": 4, "t": 1788398590000}
        ]
    }
    window = Window.model_validate(raw_window)
    assert window.seq == 42
    assert window.window_end > window.window_start
    assert "minecraft:zombie" in window.activity.combat_window.damage_dealt
    assert len(window.activity.combat_window.kills) == 2
    assert len(window.highlights) == 3

def test_heartbeat_parsing():
    raw_hb = {
        "last_seen": 1788398627884,
        "tps": 19.8,
        "player_count": 1,
        "mod_version": "0.1.0",
        "seq": 42
    }
    hb = Heartbeat.model_validate(raw_hb)
    assert hb.tps == 19.8
    assert hb.seq == 42