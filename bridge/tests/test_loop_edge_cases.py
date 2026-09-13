import time
from pathlib import Path
from narrator_bridge.config import Config, BridgeConfig, LLMConfig, PromptConfig, LoggingConfig
from narrator_bridge.loop import MainLoop
from narrator_bridge import io

def test_mod_restart_and_paused(tmp_path: Path):
    bridge_dir = tmp_path / "ai_narrator_edge"
    bridge_dir.mkdir()

    cfg = Config(
        bridge=BridgeConfig(path=bridge_dir, cycle_interval_s=1, poll_interval_s=1, mod_dead_timeout_s=30),
        llm=LLMConfig(provider="mock", enable_fallback=True),
        prompt=PromptConfig(max_chars=400),
        logging=LoggingConfig(level="DEBUG")
    )

    loop = MainLoop(cfg)
    loop.last_seq = 10
    loop.last_narration = "História anterior"

    # 1. Teste de mod inativo (sem heartbeat)
    loop._tick()
    assert loop.last_seq == 10  # Não processou

    # 2. Mod vivo, mas com seq menor (reinício)
    io.write_json_atomic(bridge_dir / "heartbeat.json", {
        "last_seen": int(time.time() * 1000), "tps": 20.0, "player_count": 1, "mod_version": "0.1.0", "seq": 1
    })
    io.write_json_atomic(bridge_dir / "state.json", {
        "seq": 1,
        "updated_at": int(time.time() * 1000),
        "player": {
            "name": "Capy", "dimension": "minecraft:overworld",
            "pos": {"x": 0, "y": 64, "z": 0}, "health": 20.0, "food": 20, "day_time": 6000, "period": "dia",
            "weather": {"raining": False, "thundering": False},
            "biome": {"current": {"id": "minecraft:plains", "since_t": 1000, "pos": {"x": 0, "y": 64, "z": 0}}},
            "spawn": {"current": {"pos": {"x": 0, "y": 64, "z": 0}, "set_t": 1000, "is_home": True, "dwell_ms": 10000}},
            "held_items_recent": [], "entities_nearby": {}, "paused": True
        }
    })

    # Pausado deve pular ciclo
    loop._tick()
    assert loop.last_seq == 1
    assert loop.last_narration == ""  # Resetou contexto devido ao seq < 10