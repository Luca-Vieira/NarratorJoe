import time
import json
from pathlib import Path
import pytest
from narrator_bridge.config import Config, BridgeConfig, LLMConfig, PromptConfig, LoggingConfig
from narrator_bridge.loop import MainLoop
from narrator_bridge import io

def test_full_cycle_simulation(tmp_path: Path):
    bridge_dir = tmp_path / "ai_narrator"
    bridge_dir.mkdir()

    cfg = Config(
        bridge=BridgeConfig(path=bridge_dir, cycle_interval_s=1, poll_interval_s=1, mod_dead_timeout_s=30),
        llm=LLMConfig(provider="mock", model="mock-v1", enable_fallback=True),
        prompt=PromptConfig(max_chars=400),
        logging=LoggingConfig(level="DEBUG")
    )

    loop = MainLoop(cfg)

    # 1. Cria heartbeat fresco
    io.write_json_atomic(bridge_dir / "heartbeat.json", {
        "last_seen": int(time.time() * 1000),
        "tps": 20.0,
        "player_count": 1,
        "mod_version": "0.1.0",
        "seq": 1
    })

    # 2. Cria state.json e window.json com seq=1
    state_data = {
        "seq": 1,
        "updated_at": int(time.time() * 1000),
        "player": {
            "name": "BusinessCapybara",
            "dimension": "minecraft:overworld",
            "pos": {"x": 10, "y": 64, "z": 20},
            "health": 20.0,
            "food": 20,
            "day_time": 1000,
            "period": "amanhecer",
            "weather": {"raining": False, "thundering": False},
            "biome": {"current": {"id": "minecraft:plains", "since_t": 1000, "pos": {"x": 10, "y": 64, "z": 20}}},
            "spawn": {"current": {"pos": {"x": 10, "y": 64, "z": 20}, "set_t": 1000, "is_home": True, "dwell_ms": 100000}},
            "held_items_recent": [],
            "entities_nearby": {},
            "paused": False
        }
    }
    window_data = {
        "seq": 1,
        "window_start": int(time.time() * 1000) - 60000,
        "window_end": int(time.time() * 1000),
        "activity": {
            "blocks_broken": {"minecraft:stone": 5}
        },
        "highlights": []
    }
    io.write_json_atomic(bridge_dir / "state.json", state_data)
    io.write_json_atomic(bridge_dir / "window.json", window_data)

    # Executa _tick
    loop._tick()

    # Valida que o ciclo processou:
    assert loop.last_seq == 1
    assert not (bridge_dir / "window.json").exists()  # deletado
    assert (bridge_dir / "trigger.flag").exists()     # trigger criado
    assert (bridge_dir / "last_narration.txt").exists()
    assert (bridge_dir / "prompt.json").exists()
    assert (bridge_dir / "narration.log").exists()

    log_content = (bridge_dir / "narration.log").read_text(encoding="utf-8")
    assert "=== Ciclo #1" in log_content
    assert "[NARRATION]" in log_content
    assert "[/NARRATION]" in log_content