import pytest
from narrator_bridge.config import PromptConfig
from narrator_bridge.prompt import PromptBuilder
from narrator_bridge.schemas import State, Window

def test_prompt_builder():
    config = PromptConfig(max_chars=400, style="storyteller")
    builder = PromptBuilder(config)

    assert builder._derive_alias("BusinessCapybara") == "capivara"
    assert builder._derive_alias("Steve") == "steve"

    raw_state = {
        "seq": 1,
        "updated_at": 1000000,
        "player": {
            "name": "BusinessCapybara",
            "dimension": "minecraft:overworld",
            "pos": {"x": 10, "y": 64, "z": 20},
            "health": 18.0,
            "food": 16,
            "day_time": 6000,
            "period": "dia",
            "weather": {"raining": False, "thundering": False},
            "biome": {"current": {"id": "minecraft:plains", "since_t": 900000, "pos": {"x": 10, "y": 64, "z": 20}}},
            "spawn": {"current": {"pos": {"x": 10, "y": 64, "z": 20}, "set_t": 900000, "is_home": True, "dwell_ms": 100000}},
            "held_items_recent": [{"item": "minecraft:iron_sword", "since_t": 950000}],
            "entities_nearby": {"hostil": {}, "passivo": {"minecraft:cow": 2}, "neutro": {}, "players": 0},
            "paused": False
        }
    }
    raw_window = {
        "seq": 1,
        "window_start": 940000,
        "window_end": 1000000,
        "activity": {
            "blocks_broken": {"minecraft:oak_log": 4},
            "combat_window": {
                "damage_dealt": {"minecraft:zombie": 10.0},
                "damage_taken": {},
                "kills": [{"entity": "minecraft:zombie", "t": 980000, "weapon": "minecraft:iron_sword", "damage": 10.0}],
                "deaths_nearby": [],
                "player_death": None
            }
        },
        "highlights": []
    }

    state = State.model_validate(raw_state)
    window = Window.model_validate(raw_window)

    messages = builder.build(state, window, "Última história...")
    assert len(messages) == 2
    assert messages[0]["role"] == "system"
    assert "BusinessCapybara" in messages[0]["content"]
    assert messages[1]["role"] == "user"
    assert "=== ESTADO ATUAL ===" in messages[1]["content"]
    assert "=== JANELA" in messages[1]["content"]
    assert "=== TOM SUGERIDO ===" in messages[1]["content"]

def test_clean_and_validate():
    config = PromptConfig(max_chars=400)
    builder = PromptBuilder(config)

    raw = '  "Narração: **O capivara** caminha com determinação pela vasta planície de grama alta."  '
    cleaned = builder.clean_narration(raw)
    assert not cleaned.startswith('"')
    assert not cleaned.startswith("Narração:")
    assert "**" not in cleaned
    assert builder.validate_narration(cleaned) is True

    too_short = "Curto."
    assert builder.validate_narration(too_short) is False

    forbidden = "Como uma IA, você encontra blocos de pedra e caminha em silêncio absoluto."
    assert builder.validate_narration(forbidden) is False