import pytest
from narrator_bridge.config import PromptConfig
from narrator_bridge.fallback import FallbackManager
from narrator_bridge.schemas import State, Window

def test_fallback_templates():
    config = PromptConfig(max_chars=400)
    manager = FallbackManager(config)

    raw_state = {
        "seq": 1,
        "updated_at": 1000000,
        "player": {
            "name": "BusinessCapybara",
            "dimension": "minecraft:overworld",
            "pos": {"x": 10, "y": 64, "z": 20},
            "health": 20.0,
            "food": 20,
            "day_time": 6000,
            "period": "dia",
            "weather": {"raining": False, "thundering": False},
            "biome": {"current": {"id": "minecraft:plains", "since_t": 900000, "pos": {"x": 10, "y": 64, "z": 20}}},
            "spawn": {"current": {"pos": {"x": 10, "y": 64, "z": 20}, "set_t": 900000, "is_home": True, "dwell_ms": 100000}},
            "held_items_recent": [],
            "entities_nearby": {},
            "paused": False
        }
    }
    state = State.model_validate(raw_state)

    # 1. Combat
    window_combat = Window.model_validate({
        "seq": 1, "window_start": 940000, "window_end": 1000000,
        "activity": {
            "combat_window": {
                "damage_dealt": {"minecraft:zombie": 15.0},
                "kills": [{"entity": "minecraft:zombie", "t": 950000, "weapon": "minecraft:sword", "damage": 15.0}]
            }
        },
        "highlights": []
    })
    res_combat = manager.generate(state, window_combat)
    assert len(res_combat) >= 50

    # 2. Build
    window_build = Window.model_validate({
        "seq": 1, "window_start": 940000, "window_end": 1000000,
        "activity": {
            "blocks_placed": {"minecraft:stone": 15}
        },
        "highlights": []
    })
    res_build = manager.generate(state, window_build)
    assert len(res_build) >= 50

    # 3. Idle
    window_idle = Window.model_validate({
        "seq": 1, "window_start": 940000, "window_end": 1000000,
        "activity": {},
        "highlights": []
    })
    res_idle = manager.generate(state, window_idle)
    assert len(res_idle) >= 50