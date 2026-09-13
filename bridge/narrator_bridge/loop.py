import time
import json
from pathlib import Path
from typing import Optional, List, Dict
from datetime import datetime, timezone
from loguru import logger
from tenacity import retry, stop_after_attempt, wait_exponential, retry_if_exception_type

from .config import Config
from .schemas import State, Window
from . import io
from .llm.factory import create_llm_client
from .prompt import PromptBuilder
from .fallback import FallbackManager

class MainLoop:
    def __init__(self, config: Config):
        self.config = config
        self.bridge_path = config.bridge.path
        self.last_seq: int = 0
        self.last_narration: str = ""
        self.llm_client = create_llm_client(config.llm)
        self.prompt_builder = PromptBuilder(config.prompt)
        self.fallback = FallbackManager(config.prompt) if config.llm.enable_fallback else None

        self.bridge_path.mkdir(parents=True, exist_ok=True)

        self.state_path = self.bridge_path / "state.json"
        self.window_path = self.bridge_path / "window.json"
        self.heartbeat_path = self.bridge_path / "heartbeat.json"
        self.trigger_path = self.bridge_path / "trigger.flag"
        self.last_narration_path = self.bridge_path / "last_narration.txt"
        self.prompt_path = self.bridge_path / "prompt.json"
        self.narration_log_path = self.bridge_path / "narration.log"

        if self.last_narration_path.exists():
            try:
                self.last_narration = self.last_narration_path.read_text(encoding="utf-8").strip()
                logger.info(f"Last narration carregada do disco: {len(self.last_narration)} chars")
            except Exception as e:
                logger.warning(f"Erro ao carregar last_narration.txt: {e}")

        initial_state = io.read_json_atomic(self.state_path, State)
        if initial_state:
            self.last_seq = initial_state.seq
            logger.info(f"Seq inicial definido do state.json: {self.last_seq}")

    def run(self) -> None:
        logger.info("Bridge loop iniciado. Monitorando {}", self.bridge_path)
        try:
            while True:
                self._tick()
                time.sleep(self.config.bridge.poll_interval_s)
        except KeyboardInterrupt:
            logger.info("Interrompido pelo usuário. Encerrando loop.")
        except Exception as e:
            logger.exception(f"Erro fatal no loop: {e}")
            raise

    def _tick(self) -> None:
        if not self._is_mod_alive():
            logger.debug("Mod inativo ou heartbeat ausente, aguardando...")
            return

        state = io.read_json_atomic(self.state_path, State)
        if state is None:
            return

        if state.seq < self.last_seq:
            logger.warning(f"Mod reiniciado (seq caiu de {self.last_seq} para {state.seq}). Resetando contexto narrativo.")
            self.last_narration = ""
            io.write_text_atomic(self.last_narration_path, "")
            self.last_seq = 0

        if state.seq <= self.last_seq:
            return

        if state.player.paused:
            logger.info("Player pausou a narração (/narrator pause). Pulando ciclo.")
            self.last_seq = state.seq
            return

        window = io.read_json_atomic(self.window_path, Window)
        if window is None:
            logger.warning(f"window.json não disponível para seq={state.seq}. Pulando ciclo.")
            self.last_seq = state.seq
            return

        if not io.delete_if_exists(self.window_path):
            logger.warning("window.json não pôde ser deletado após leitura.")

        self._process_cycle(state, window)

    def _is_mod_alive(self) -> bool:
        if not self.heartbeat_path.exists():
            return False
        try:
            data = json.loads(self.heartbeat_path.read_text(encoding="utf-8"))
            last_seen = data.get("last_seen", 0)
            now_ms = time.time() * 1000
            age_s = (now_ms - last_seen) / 1000.0
            return age_s < self.config.bridge.mod_dead_timeout_s
        except Exception:
            return False

    def _process_cycle(self, state: State, window: Window) -> None:
        cycle_start = time.time()
        seq = state.seq
        logger.info(f"Ciclo #{seq} iniciado")

        messages = self.prompt_builder.build(state, window, self.last_narration)

        prompt_debug = {
            "seq": seq,
            "timestamp": int(time.time() * 1000),
            "messages": messages,
            "provider": self.config.llm.provider,
            "model": self.config.llm.model,
        }
        io.write_json_atomic(self.prompt_path, prompt_debug)

        raw_narration = self._call_llm_with_retry(messages, seq)
        narration: Optional[str] = None
        narration_meta = "LLM"

        if raw_narration:
            cleaned = self.prompt_builder.clean_narration(raw_narration)
            if self.prompt_builder.validate_narration(cleaned):
                if self.last_narration and self.prompt_builder.word_jaccard(cleaned, self.last_narration) > 0.6:
                    logger.warning(f"Ciclo #{seq}: narração muito similar à anterior (Jaccard > 0.6).")
                    if self.fallback:
                        narration = self.fallback.generate(state, window)
                        narration_meta = "FALLBACK"
                    else:
                        narration = cleaned
                else:
                    narration = cleaned
            else:
                logger.warning(f"Ciclo #{seq}: narração rejeitada pela validação.")

        if narration is None:
            if self.fallback:
                narration = self.fallback.generate(state, window)
                narration_meta = "FALLBACK"
                logger.warning(f"Ciclo #{seq}: usado fallback de templates ({narration_meta})")
            else:
                narration = "O capivara segue sua jornada com cautela, contemplando a vastidão do mundo."
                narration_meta = "FALLBACK"

        self._save_narration(narration, state, window, seq, narration_meta)

        self.last_narration = narration
        io.write_text_atomic(self.last_narration_path, narration)

        self._emit_trigger()

        self.last_seq = seq

        dur_ms = (time.time() - cycle_start) * 1000
        logger.info(f"Ciclo #{seq} concluído em {dur_ms:.0f}ms ({narration_meta})")

    def _call_llm_with_retry(self, messages: List[Dict[str, str]], seq: int) -> Optional[str]:
        @retry(
            stop=stop_after_attempt(self.config.llm.max_retries),
            wait=wait_exponential(
                multiplier=self.config.llm.retry_base_delay_s,
                max=self.config.llm.retry_max_delay_s,
            ),
            retry=retry_if_exception_type((TimeoutError, ConnectionError)),
            before_sleep=lambda rs: logger.warning(f"Ciclo #{seq}: retry {rs.attempt_number} após erro de conexão/timeout"),
            reraise=True,
        )
        def _call():
            return self.llm_client.complete(messages)

        try:
            return _call()
        except Exception as e:
            logger.error(f"Ciclo #{seq}: LLM falhou após retries: {e}")
            return None

    def _save_narration(self, narration: str, state: State, window: Window, seq: int, meta: str) -> None:
        ts = datetime.now(timezone.utc).isoformat()
        prov = self.config.llm.provider
        mod = self.config.llm.model

        entry = []
        entry.append(f"=== Ciclo #{seq} | {ts} | seq={seq} | provider={prov} | model={mod} | meta={meta} ===")
        entry.append(f"[STATE] {state.player.name} em {state.player.dimension}, {state.player.period}, health={state.player.health:.1f}/20, biome={state.player.biome.current.id}")

        activities = []
        a = window.activity
        if a.blocks_broken:
            activities.append(f"quebrou {self._fmt_dict(a.blocks_broken)}")
        if a.blocks_placed:
            activities.append(f"colocou {self._fmt_dict(a.blocks_placed)}")
        if a.items_gained:
            activities.append(f"ganhou {self._fmt_dict(a.items_gained)}")
        if a.items_lost:
            activities.append(f"perdeu {self._fmt_dict(a.items_lost)}")
        if a.mobs_killed:
            activities.append(f"matou {self._fmt_dict(a.mobs_killed)}")
        if a.containers_opened:
            activities.append(f"abriu {self._fmt_dict(a.containers_opened)}")
        if a.stations_used:
            activities.append(f"usou {self._fmt_dict(a.stations_used)}")

        cw = a.combat_window
        if cw.damage_dealt:
            activities.append(f"causou {self._fmt_dict(cw.damage_dealt)} de dano")
        if cw.damage_taken:
            activities.append(f"recebeu {self._fmt_dict(cw.damage_taken)} de dano")
        if cw.kills:
            activities.append(f"{len(cw.kills)} abates")
        if cw.player_death:
            activities.append(f"MORREU ({cw.player_death.cause})")

        dur_ms = max(0, window.window_end - window.window_start)
        entry.append(f"[WINDOW] {dur_ms}ms: " + (", ".join(activities) if activities else "(sem atividade)"))

        if window.highlights:
            hl_str = " | ".join(self.prompt_builder._fmt_highlight(h) for h in window.highlights)
            entry.append(f"[HIGHLIGHTS] {hl_str}")

        if self.last_narration:
            prev_short = self.last_narration[:200] + ("..." if len(self.last_narration) > 200 else "")
            entry.append(f"[PREVIOUS] \"{prev_short}\"")

        entry.append("[NARRATION]")
        entry.append(narration)
        entry.append("[/NARRATION]")
        entry.append("")

        io.append_text(self.narration_log_path, "\n".join(entry) + "\n")

    def _fmt_dict(self, d: Dict) -> str:
        return ", ".join(f"{v} {str(k).split(':')[-1]}" for k, v in d.items())

    def _emit_trigger(self) -> None:
        try:
            io.touch(self.trigger_path)
            logger.debug("trigger.flag criado com sucesso")
        except FileExistsError:
            logger.warning("trigger.flag já existe em disco, mod pode estar lento no consumo.")