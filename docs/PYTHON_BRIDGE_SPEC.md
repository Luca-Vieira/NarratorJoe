# Especificação do Bridge Python

> Este documento descreve a implementação completa do bridge Python que lê os JSONs gerados pelo mod, chama o LLM e produz a narração. **Toda implementação deve seguir esta spec à risca.**

---

## 1. Visão Geral

O bridge Python é um processo independente que roda em paralelo ao Minecraft. Ele:

1. **Monitora** o diretório `~/ai-narrator/` em busca de novos ciclos (via `seq` do `state.json`).
2. **Lê** `state.json`, `window.json` e `last_narration.txt` quando um novo ciclo chega.
3. **Monta** o prompt (system + user) seguindo as diretrizes de `PROMPT_ENGINEERING.md`.
4. **Chama** o LLM via interface genérica (`LLMClient`), com retry e fallback.
5. **Salva** a narração em `narration.log` (append) e atualiza `last_narration.txt`.
6. **Sinaliza** o mod criando `trigger.flag`.

O bridge é **tolerante a falhas**: se o LLM falhar, usa templates; se o mod morrer, pausa; se reiniciar, retoma do último `seq` visto.

---

## 2. Stack e Dependências

### 2.1 Python
- **Versão:** ≥ 3.11 (obrigatório, usa `tomllib` nativo).
- **Empacotamento:** `pyproject.toml` com `uv` ou `pip install -e .`.

### 2.2 Dependências principais (`pyproject.toml`)
```toml
[project]
name = "narrator-bridge"
version = "0.1.0"
requires-python = ">=3.11"
dependencies = [
    "pydantic>=2.5",            # validação de schemas
    "loguru>=0.7",              # logging estruturado
    "tomli>=2.0; python_version < '3.11'",
    "python-dotenv>=1.0",       # carrega .env
    "httpx>=0.27",              # cliente HTTP para LLMs
    "tenacity>=8.2",            # retry com backoff
]

[project.optional-dependencies]
openai = ["openai>=1.0"]
anthropic = ["anthropic>=0.18"]
gemini = ["google-genai>=0.3"]
ollama = ["ollama>=0.1.6"]
dev = [
    "pytest>=8.0",
    "pytest-asyncio>=0.23",
    "ruff>=0.4",
    "mypy>=1.10",
]

[project.scripts]
narrator-bridge = "narrator_bridge.__main__:main"
```

### 2.3 Estrutura de diretórios
```
bridge/
├── narrator_bridge/
│   ├── __init__.py
│   ├── __main__.py                # entrypoint CLI
│   ├── loop.py                    # MainLoop
│   ├── config.py                  # carrega .env + narrator_config.toml
│   ├── schemas.py                 # pydantic models para State/Window/etc
│   ├── io.py                      # leitura/escrita atômica
│   ├── prompt.py                  # PromptBuilder
│   ├── fallback.py                # FallbackManager (templates)
│   ├── logging_setup.py           # configura loguru
│   ├── llm/
│   │   ├── __init__.py
│   │   ├── base.py                # LLMClient (interface ABC)
│   │   ├── openai_provider.py
│   │   ├── anthropic_provider.py
│   │   ├── gemini_provider.py
│   │   ├── ollama_provider.py
│   │   └── factory.py             # cria provider por config
│   └── templates/
│       ├── combat.txt.j2          # template de fallback para combate
│       ├── explore.txt.j2         # template para exploração
│       ├── build.txt.j2           # template para construção
│       └── idle.txt.j2            # template para idle/AFK
├── tests/
│   ├── test_loop.py
│   ├── test_prompt.py
│   ├── test_schemas.py
│   ├── test_io.py
│   └── test_fallback.py
├── narrator_config.toml           # config editável pelo usuário
├── .env.example
├── pyproject.toml
└── README.md
```

---

## 3. Configuração

### 3.1 `.env.example`

```bash
# Chaves de API dos LLM providers (preencha o que usar)
OPENAI_API_KEY=sk-...
ANTHROPIC_API_KEY=sk-ant-...
GEMINI_API_KEY=...
OLLAMA_HOST=http://localhost:11434

# Provider ativo (openai | anthropic | gemini | ollama)
NARRATOR_LLM_PROVIDER=openai

# Diretório do bridge (mesmo usado pelo mod)
NARRATOR_BRIDGE_PATH=~/ai-narrator
```

### 3.2 `narrator_config.toml` (config editável sem reiniciar código)

```toml
[bridge]
# Caminho compartilhado com o mod (deve bater com config do mod)
path = "~/ai-narrator"

# Intervalo base entre ciclos (segundos)
cycle_interval_s = 60

# Polling de state.json (segundos)
poll_interval_s = 1

# Timeout do mod antes de considerar morto (segundos)
mod_dead_timeout_s = 30

[llm]
# Provider: openai | anthropic | gemini | ollama
provider = "openai"

# Modelo específico do provider
model = "gpt-4o-mini"

# Temperatura (0.0 = determinístico, 1.0 = criativo)
temperature = 0.7

# Máximo de tokens de saída
max_tokens = 300

# Timeout por chamada (segundos)
timeout_s = 30

# Número de retries com backoff exponencial
max_retries = 3
retry_base_delay_s = 1.0
retry_max_delay_s = 8.0

# Habilitar fallback de templates se LLM falhar
enable_fallback = true

[prompt]
# Caminho para system prompt customizado (opcional; usa default se vazio)
system_prompt_file = ""

# Idioma da narração
language = "pt-BR"

# Estilo do narrador: documentary | storyteller | playful | epic
style = "storyteller"

# Tamanho máximo da narração em caracteres (LLM será instruído)
max_chars = 400

[logging]
# Nível: DEBUG | INFO | WARNING | ERROR
level = "INFO"

# Rotação do bridge.log (ex: "10 MB", "1 day")
rotation = "10 MB"

# Retenção (ex: "7 days", "1 month")
retention = "30 days"

# Tamanho do ring buffer em memória
ring_buffer_size = 500
```

### 3.3 `config.py` (carregamento)

```python
import tomllib
from pathlib import Path
from dataclasses import dataclass
from dotenv import load_dotenv
import os

@dataclass
class BridgeConfig:
    path: Path
    cycle_interval_s: int
    poll_interval_s: int
    mod_dead_timeout_s: int

@dataclass
class LLMConfig:
    provider: str
    model: str
    temperature: float
    max_tokens: int
    timeout_s: int
    max_retries: int
    retry_base_delay_s: float
    retry_max_delay_s: float
    enable_fallback: bool

@dataclass
class PromptConfig:
    system_prompt_file: str
    language: str
    style: str
    max_chars: int

@dataclass
class LoggingConfig:
    level: str
    rotation: str
    retention: str
    ring_buffer_size: int

@dataclass
class Config:
    bridge: BridgeConfig
    llm: LLMConfig
    prompt: PromptConfig
    logging: LoggingConfig

    @classmethod
    def load(cls) -> "Config":
        load_dotenv()
        # Carrega .env para API keys
        # Carrega narrator_config.toml
        config_path = Path("narrator_config.toml")
        if not config_path.exists():
            # Fallback para defaults
            ...
        with open(config_path, "rb") as f:
            data = tomllib.load(f)

        # Resolve ~ no path
        bridge_path = Path(os.path.expanduser(data["bridge"]["path"]))

        return cls(
            bridge=BridgeConfig(
                path=bridge_path,
                cycle_interval_s=data["bridge"]["cycle_interval_s"],
                poll_interval_s=data["bridge"]["poll_interval_s"],
                mod_dead_timeout_s=data["bridge"]["mod_dead_timeout_s"],
            ),
            llm=LLMConfig(**data["llm"]),
            prompt=PromptConfig(**data["prompt"]),
            logging=LoggingConfig(**data["logging"]),
        )
```

---

## 4. Schemas (Pydantic Models)

### 4.1 `schemas.py`

```python
from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime

class Weather(BaseModel):
    raining: bool
    thundering: bool

class BiomeEntry(BaseModel):
    id: str
    since_t: int
    pos: dict[str, int]
    until_t: Optional[int] = None

class BiomeHistory(BaseModel):
    current: BiomeEntry
    previous: Optional[BiomeEntry] = None

class SpawnEntry(BaseModel):
    pos: dict[str, int]
    set_t: int
    is_home: bool = False
    dwell_ms: int = 0

class SpawnHistory(BaseModel):
    current: SpawnEntry
    previous: Optional[SpawnEntry] = None

class HeldItem(BaseModel):
    item: str
    since_t: int

class EntitiesNearby(BaseModel):
    hostil: dict[str, int] = Field(default_factory=dict)
    passivo: dict[str, int] = Field(default_factory=dict)
    neutro: dict[str, int] = Field(default_factory=dict)
    players: int = 0

class PlayerState(BaseModel):
    name: str
    dimension: str
    pos: dict[str, int]
    health: float
    food: int
    day_time: int
    period: str
    weather: Weather
    biome: BiomeHistory
    spawn: SpawnHistory
    held_items_recent: list[HeldItem]
    entities_nearby: EntitiesNearby
    paused: bool = False

class State(BaseModel):
    seq: int
    updated_at: int
    player: PlayerState

# ---- Window ----

class Activity(BaseModel):
    blocks_placed: dict[str, int] = Field(default_factory=dict)
    blocks_broken: dict[str, int] = Field(default_factory=dict)
    items_gained: dict[str, int] = Field(default_factory=dict)
    items_lost: dict[str, int] = Field(default_factory=dict)
    mobs_killed: dict[str, int] = Field(default_factory=dict)
    containers_opened: dict[str, int] = Field(default_factory=dict)
    stations_used: dict[str, int] = Field(default_factory=dict)
    combat_window: "CombatWindow" = Field(default_factory=lambda: CombatWindow())

class KillEntry(BaseModel):
    entity: str
    t: int
    weapon: Optional[str] = None
    damage: Optional[float] = None

class DeathEntry(BaseModel):
    entity: Optional[str] = None
    cause: str
    t: int
    pos: Optional[dict[str, int]] = None
    distance: Optional[float] = None

class PlayerDeath(BaseModel):
    cause: str
    t: int
    pos: dict[str, int]

class CombatWindow(BaseModel):
    damage_dealt: dict[str, float] = Field(default_factory=dict)
    damage_taken: dict[str, float] = Field(default_factory=dict)
    kills: list[KillEntry] = Field(default_factory=list)
    deaths_nearby: list[DeathEntry] = Field(default_factory=list)
    player_death: Optional[PlayerDeath] = None

class Highlight(BaseModel):
    type: str
    t: int
    # Campos opcionais dependendo do tipo
    text: Optional[str] = None
    effect: Optional[str] = None
    amplifier: Optional[int] = None
    food: Optional[int] = None
    health: Optional[float] = None
    from_biome: Optional[str] = None
    to_biome: Optional[str] = None
    from_dim: Optional[str] = None
    to_dim: Optional[str] = None
    weather_event: Optional[str] = None

class Window(BaseModel):
    seq: int
    window_start: int
    window_end: int
    activity: Activity
    highlights: list[Highlight] = Field(default_factory=list)

# Resolver forward ref
Activity.model_rebuild()
```

### 4.2 Validação
- **Sempre** parsear com `State.model_validate_json(text)` ou `Window.model_validate_json(text)`.
- Se parsing falhar, logar erro e tratar como `None` (não processar ciclo).

---

## 5. I/O de Arquivos

### 5.1 `io.py`

```python
import os
import json
from pathlib import Path
from typing import Optional, Any
import pydantic
from loguru import logger

def read_json_atomic(path: Path, model: type[pydantic.BaseModel]) -> Optional[Any]:
    """
    Lê um JSON de forma segura. Retorna None se:
    - arquivo não existe
    - JSON inválido
    - falha de validação
    """
    try:
        if not path.exists():
            return None
        text = path.read_text(encoding="utf-8")
        return model.model_validate_json(text)
    except pydantic.ValidationError as e:
        logger.warning(f"JSON inválido em {path}: {e}")
        return None
    except Exception as e:
        logger.error(f"Erro lendo {path}: {e}")
        return None

def write_json_atomic(path: Path, data: Any) -> None:
    """
    Escreve JSON atomicamente: temp + fsync + os.replace.
    """
    tmp = path.with_suffix(path.suffix + ".tmp")
    text = json.dumps(data, indent=2, ensure_ascii=False) if not isinstance(data, str) else data
    tmp.write_text(text, encoding="utf-8")
    # fsync
    with open(tmp, "rb") as f:
        os.fsync(f.fileno())
    os.replace(tmp, path)

def write_text_atomic(path: Path, text: str) -> None:
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(text, encoding="utf-8")
    with open(tmp, "rb") as f:
        os.fsync(f.fileno())
    os.replace(tmp, path)

def append_text(path: Path, text: str) -> None:
    """Append simples (não atômico, mas aceitável para logs)."""
    with open(path, "a", encoding="utf-8") as f:
        f.write(text)
        f.flush()
        os.fsync(f.fileno())

def delete_if_exists(path: Path) -> bool:
    """Deleta arquivo se existir. Retorna True se deletou."""
    try:
        path.unlink()
        return True
    except FileNotFoundError:
        return False
    except Exception as e:
        logger.error(f"Erro deletando {path}: {e}")
        return False

def touch(path: Path) -> None:
    """Cria arquivo vazio (para flags)."""
    path.touch(exist_ok=False)
```

### 5.2 Leitura tolerante
Todo `read_json_atomic` retorna `Optional[Model]`. O caller **sempre** deve checar `is None` antes de usar.

---

## 6. Loop Principal

### 6.1 `loop.py`

```python
import time
from pathlib import Path
from typing import Optional
from loguru import logger
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

        # Garante que o diretório existe
        self.bridge_path.mkdir(parents=True, exist_ok=True)

        # Caminhos
        self.state_path = self.bridge_path / "state.json"
        self.window_path = self.bridge_path / "window.json"
        self.heartbeat_path = self.bridge_path / "heartbeat.json"
        self.trigger_path = self.bridge_path / "trigger.flag"
        self.last_narration_path = self.bridge_path / "last_narration.txt"
        self.prompt_path = self.bridge_path / "prompt.json"
        self.narration_log_path = self.bridge_path / "narration.log"

        # Carrega last_narration persistente (caso reiniciado)
        if self.last_narration_path.exists():
            self.last_narration = self.last_narration_path.read_text(encoding="utf-8").strip()
            logger.info(f"Last narration carregada do disco: {len(self.last_narration)} chars")

        # Carrega seq inicial do state.json (não processa o atual, espera próximo)
        initial_state = io.read_json_atomic(self.state_path, State)
        if initial_state:
            self.last_seq = initial_state.seq
            logger.info(f"Seq inicial definido: {self.last_seq}")

    def run(self) -> None:
        logger.info("Bridge iniciado. Monitorando {}", self.bridge_path)
        try:
            while True:
                self._tick()
                time.sleep(self.config.bridge.poll_interval_s)
        except KeyboardInterrupt:
            logger.info("Interrompido pelo usuário. Encerrando...")
        except Exception as e:
            logger.exception(f"Erro fatal no loop: {e}")
            raise

    def _tick(self) -> None:
        # 1. Health check
        if not self._is_mod_alive():
            logger.debug("Mod inativo, aguardando heartbeat...")
            return

        # 2. Lê state.json
        state = io.read_json_atomic(self.state_path, State)
        if state is None:
            return

        # 3. Detecta seq novo
        if state.seq <= self.last_seq:
            return  # já processado

        # 4. Detecta mod reiniciado (seq menor)
        if state.seq < self.last_seq:
            logger.warning(f"Mod reiniciado (seq caiu de {self.last_seq} para {state.seq}). Resetando contexto.")
            self.last_narration = ""
            io.write_text_atomic(self.last_narration_path, "")

        # 5. Player pausou?
        if state.player.paused:
            logger.info("Player pausou a narração. Pulando ciclo.")
            self.last_seq = state.seq
            return

        # 6. Lê window.json
        window = io.read_json_atomic(self.window_path, Window)
        if window is None:
            logger.warning(f"window.json não disponível para seq={state.seq}. Pulando ciclo.")
            self.last_seq = state.seq
            return

        # 7. Deleta window.json (sinaliza consumo)
        if not io.delete_if_exists(self.window_path):
            logger.warning("window.json não pôde ser deletado. Pode haver race condition.")

        # 8. Processa o ciclo
        self._process_cycle(state, window)

    def _is_mod_alive(self) -> bool:
        if not self.heartbeat_path.exists():
            return False
        try:
            import json
            data = json.loads(self.heartbeat_path.read_text(encoding="utf-8"))
            last_seen = data.get("last_seen", 0)
            age_s = (time.time() * 1000 - last_seen) / 1000
            return age_s < self.config.bridge.mod_dead_timeout_s
        except Exception:
            return False

    def _process_cycle(self, state: State, window: Window) -> None:
        cycle_start = time.time()
        seq = state.seq
        logger.info(f"Ciclo #{seq} iniciado")

        # 1. Monta prompt
        messages = self.prompt_builder.build(state, window, self.last_narration)

        # Salva prompt para debug
        import json
        io.write_json_atomic(self.prompt_path, {
            "seq": seq,
            "messages": messages,
            "timestamp": int(time.time() * 1000),
        })

        # 2. Chama LLM
        narration = self._call_llm_with_retry(messages, seq)

        if narration is None:
            # 3. Fallback
            if self.fallback:
                narration = self.fallback.generate(state, window)
                logger.warning(f"Ciclo #{seq} usado fallback de templates")
                narration_meta = "FALLBACK"
            else:
                logger.error(f"Ciclo #{seq} sem narração (LLM falhou e fallback desabilitado)")
                self.last_seq = seq
                # Mesmo assim, cria trigger para o mod continuar
                self._emit_trigger()
                return
        else:
            narration_meta = "LLM"

        # 4. Salva narração
        self._save_narration(narration, state, window, seq, narration_meta)

        # 5. Atualiza last_narration
        self.last_narration = narration
        io.write_text_atomic(self.last_narration_path, narration)

        # 6. Cria trigger
        self._emit_trigger()

        # 7. Atualiza seq
        self.last_seq = seq

        cycle_duration = (time.time() - cycle_start) * 1000
        logger.info(f"Ciclo #{seq} concluído em {cycle_duration:.0f}ms")

    def _call_llm_with_retry(self, messages: list[dict], seq: int) -> Optional[str]:
        import tenacity
        from tenacity import retry, stop_after_attempt, wait_exponential, retry_if_exception_type

        @retry(
            stop=stop_after_attempt(self.config.llm.max_retries),
            wait=wait_exponential(
                multiplier=self.config.llm.retry_base_delay_s,
                max=self.config.llm.retry_max_delay_s,
            ),
            retry=retry_if_exception_type((TimeoutError, ConnectionError)),
            before_sleep=lambda rs: logger.warning(f"Ciclo #{seq}: retry {rs.attempt_number} após erro"),
            reraise=True,
        )
        def _call():
            return self.llm_client.complete(messages)

        try:
            return _call()
        except Exception as e:
            logger.error(f"Ciclo #{seq}: LLM falhou após {self.config.llm.max_retries} tentativas: {e}")
            return None

    def _save_narration(self, narration: str, state: State, window: Window, seq: int, meta: str) -> None:
        from datetime import datetime, timezone
        ts = datetime.now(timezone.utc).isoformat()

        # Formata entrada do log
        entry = []
        entry.append(f"=== Ciclo #{seq} | {ts} | seq={seq} | provider={self.config.llm.provider} | model={self.config.llm.model} | meta={meta} ===")
        entry.append(f"[STATE] {state.player.name} em {state.player.dimension}, {state.player.period}, health={state.player.health}/{20}, biome={state.player.biome.current.id}")
        # Resumo da window
        activities = []
        if window.activity.blocks_broken:
            activities.append(f"quebrou {self._fmt_dict(window.activity.blocks_broken)}")
        if window.activity.blocks_placed:
            activities.append(f"colocou {self._fmt_dict(window.activity.blocks_placed)}")
        if window.activity.items_gained:
            activities.append(f"ganhou {self._fmt_dict(window.activity.items_gained)}")
        if window.activity.items_lost:
            activities.append(f"perdeu {self._fmt_dict(window.activity.items_lost)}")
        if window.activity.mobs_killed:
            activities.append(f"matou {self._fmt_dict(window.activity.mobs_killed)}")
        if window.activity.containers_opened:
            activities.append(f"abriu {self._fmt_dict(window.activity.containers_opened)}")
        if window.activity.stations_used:
            activities.append(f"usou {self._fmt_dict(window.activity.stations_used)}")
        # Combat summary
        cw = window.activity.combat_window
        if cw.damage_dealt:
            activities.append(f"causou {self._fmt_dict(cw.damage_dealt)} de dano")
        if cw.damage_taken:
            activities.append(f"recebeu {self._fmt_dict(cw.damage_taken)} de dano")
        if cw.kills:
            activities.append(f"{len(cw.kills)} abates")
        if cw.player_death:
            activities.append(f"MORREU ({cw.player_death.cause})")

        entry.append(f"[WINDOW] {window.window_end - window.window_start}ms: " + ", ".join(activities))

        if window.highlights:
            hl_str = " | ".join(self._fmt_highlight(h) for h in window.highlights)
            entry.append(f"[HIGHLIGHTS] {hl_str}")

        if self.last_narration:
            prev_short = self.last_narration[:200] + ("..." if len(self.last_narration) > 200 else "")
            entry.append(f"[PREVIOUS] \"{prev_short}\"")

        entry.append(f"[NARRATION]")
        entry.append(narration)
        entry.append(f"[/NARRATION]")
        entry.append("")  # linha em branco separadora

        io.append_text(self.narration_log_path, "\n".join(entry) + "\n")

    def _fmt_dict(self, d: dict) -> str:
        return ", ".join(f"{v} {k.split(':')[-1]}" for k, v in d.items())

    def _fmt_highlight(self, h) -> str:
        if h.type == "advancement":
            return f"advancement:{h.text}"
        if h.type == "effect_gained":
            return f"effect:{h.effect}(amp={h.amplifier})"
        if h.type == "food_low":
            return f"food_low:{h.food}"
        if h.type == "health_low":
            return f"health_low:{h.health}"
        if h.type == "biome_change":
            return f"biome:{h.from_biome}->{h.to_biome}"
        if h.type == "dimension_change":
            return f"dim:{h.from_dim}->{h.to_dim}"
        if h.type == "weather_change":
            return f"weather:{h.weather_event}"
        if h.type == "player_death":
            return f"death:{h.text}"
        return f"{h.type}"

    def _emit_trigger(self) -> None:
        try:
            io.touch(self.trigger_path)
            logger.debug("trigger.flag criado")
        except FileExistsError:
            # Flag já existe (mod ainda não consumiu). Esperar.
            logger.warning("trigger.flag já existe, mod pode estar lento. Pulando criação.")
```

---

## 7. LLMClient (Interface Genérica)

### 7.1 `llm/base.py`

```python
from abc import ABC, abstractmethod
from typing import Optional

class LLMClient(ABC):
    """Interface comum para todos os providers de LLM."""

    @abstractmethod
    def complete(self, messages: list[dict]) -> str:
        """
        Chama o LLM com uma lista de mensagens.

        Args:
            messages: lista de dicts no formato OpenAI
                [{"role": "system", "content": "..."}, {"role": "user", "content": "..."}]

        Returns:
            Texto da resposta do LLM.

        Raises:
            TimeoutError: se exceder o timeout
            ConnectionError: se houver erro de rede
            Exception: para outros erros (não retentáveis)
        """
        ...

    @abstractmethod
    def name(self) -> str:
        """Nome do provider para logs."""
        ...
```

### 7.2 `llm/openai_provider.py`

```python
import os
from typing import Optional
from .base import LLMClient
from ..config import LLMConfig

class OpenAIProvider(LLMClient):
    def __init__(self, config: LLMConfig):
        from openai import OpenAI
        api_key = os.environ.get("OPENAI_API_KEY")
        if not api_key:
            raise ValueError("OPENAI_API_KEY não definida no .env")
        self.client = OpenAI(api_key=api_key, timeout=config.timeout_s)
        self.config = config

    def complete(self, messages: list[dict]) -> str:
        resp = self.client.chat.completions.create(
            model=self.config.model,
            messages=messages,
            temperature=self.config.temperature,
            max_tokens=self.config.max_tokens,
        )
        return resp.choices[0].message.content.strip()

    def name(self) -> str:
        return "openai"
```

### 7.3 `llm/anthropic_provider.py`

```python
import os
from .base import LLMClient
from ..config import LLMConfig

class AnthropicProvider(LLMClient):
    def __init__(self, config: LLMConfig):
        from anthropic import Anthropic
        api_key = os.environ.get("ANTHROPIC_API_KEY")
        if not api_key:
            raise ValueError("ANTHROPIC_API_KEY não definida no .env")
        self.client = Anthropic(api_key=api_key, timeout=config.timeout_s)
        self.config = config

    def complete(self, messages: list[dict]) -> str:
        # Anthropic separa system do resto
        system = ""
        user_messages = []
        for m in messages:
            if m["role"] == "system":
                system += m["content"] + "\n"
            else:
                user_messages.append({"role": m["role"], "content": m["content"]})

        resp = self.client.messages.create(
            model=self.config.model,
            system=system.strip(),
            messages=user_messages,
            temperature=self.config.temperature,
            max_tokens=self.config.max_tokens,
        )
        return resp.content[0].text.strip()

    def name(self) -> str:
        return "anthropic"
```

### 7.4 `llm/gemini_provider.py`

```python
import os
from .base import LLMClient
from ..config import LLMConfig

class GeminiProvider(LLMClient):
    def __init__(self, config: LLMConfig):
        from google import genai
        api_key = os.environ.get("GEMINI_API_KEY")
        if not api_key:
            raise ValueError("GEMINI_API_KEY não definida no .env")
        self.client = genai.Client(api_key=api_key)
        self.config = config

    def complete(self, messages: list[dict]) -> str:
        from google.genai import types
        # Gemini usa contents com role user/model
        contents = []
        system_instruction = ""
        for m in messages:
            if m["role"] == "system":
                system_instruction += m["content"] + "\n"
            else:
                role = "user" if m["role"] == "user" else "model"
                contents.append(types.Content(role=role, parts=[types.Part(text=m["content"])]))

        config = types.GenerateContentConfig(
            system_instruction=system_instruction.strip(),
            temperature=self.config.temperature,
            max_output_tokens=self.config.max_tokens,
        )

        resp = self.client.models.generate_content(
            model=self.config.model,
            contents=contents,
            config=config,
        )
        return resp.text.strip()

    def name(self) -> str:
        return "gemini"
```

### 7.5 `llm/ollama_provider.py`

```python
import os
from .base import LLMClient
from ..config import LLMConfig

class OllamaProvider(LLMClient):
    def __init__(self, config: LLMConfig):
        from ollama import Client
        host = os.environ.get("OLLAMA_HOST", "http://localhost:11434")
        self.client = Client(host=host)
        self.config = config
        # Verifica se o modelo está disponível
        try:
            self.client.show(self.config.model)
        except Exception as e:
            raise ValueError(f"Modelo '{self.config.model}' não disponível no Ollama: {e}")

    def complete(self, messages: list[dict]) -> str:
        resp = self.client.chat(
            model=self.config.model,
            messages=messages,
            options={
                "temperature": self.config.temperature,
                "num_predict": self.config.max_tokens,
            },
        )
        return resp["message"]["content"].strip()

    def name(self) -> str:
        return "ollama"
```

### 7.6 `llm/factory.py`

```python
from .base import LLMClient
from ..config import LLMConfig
from .openai_provider import OpenAIProvider
from .anthropic_provider import AnthropicProvider
from .gemini_provider import GeminiProvider
from .ollama_provider import OllamaProvider

def create_llm_client(config: LLMConfig) -> LLMClient:
    provider = config.provider.lower()
    if provider == "openai":
        return OpenAIProvider(config)
    if provider == "anthropic":
        return AnthropicProvider(config)
    if provider == "gemini":
        return GeminiProvider(config)
    if provider == "ollama":
        return OllamaProvider(config)
    raise ValueError(f"Provider desconhecido: {provider}")
```

---

## 8. Prompt Builder

Detalhes em `PROMPT_ENGINEERING.md`. Resumo da interface:

```python
class PromptBuilder:
    def __init__(self, config: PromptConfig):
        self.config = config
        self.system_prompt = self._load_system_prompt()

    def build(self, state: State, window: Window, last_narration: str) -> list[dict]:
        user_content = self._format_user_content(state, window, last_narration)
        return [
            {"role": "system", "content": self.system_prompt},
            {"role": "user", "content": user_content},
        ]

    def _format_user_content(self, state, window, last_narration) -> str:
        # Implementação detalhada em PROMPT_ENGINEERING.md
        ...
```

---

## 9. Fallback de Templates

### 9.1 `fallback.py`

```python
from pathlib import Path
from jinja2 import Environment, FileSystemLoader, select_autoescape
from .schemas import State, Window
from .config import PromptConfig
from loguru import logger

class FallbackManager:
    def __init__(self, config: PromptConfig):
        self.config = config
        templates_dir = Path(__file__).parent / "templates"
        self.env = Environment(
            loader=FileSystemLoader(templates_dir),
            autoescape=select_autoescape(["html", "xml"]),
        )

    def generate(self, state: State, window: Window) -> str:
        # Escolhe template baseado na atividade dominante
        template_name = self._choose_template(state, window)
        template = self.env.get_template(template_name)
        return template.render(
            state=state,
            window=window,
            player=state.player,
            activity=window.activity,
        ).strip()

    def _choose_template(self, state: State, window: Window) -> str:
        a = window.activity
        # Combate dominante?
        if a.combat_window.kills or a.combat_window.player_death:
            return "combat.txt.j2"
        # Construção dominante?
        total_blocks = sum(a.blocks_placed.values()) + sum(a.blocks_broken.values())
        if total_blocks > 20:
            return "build.txt.j2"
        # Exploração?
        if state.player.biome.current.id != state.player.biome.previous.id if state.player.biome.previous else False:
            return "explore.txt.j2"
        # Idle
        return "idle.txt.j2"
```

### 9.2 Exemplo de template `templates/combat.txt.j2`

```jinja
{# Template de fallback para ciclos com combate #}
{% set kills = activity.combat_window.kills %}
{% if activity.combat_window.player_death %}
O capivara caiu. {{ activity.combat_window.player_death.cause }}{% if activity.combat_window.player_death.cause == 'mob' %}, vítima de uma emboscada{% endif %} — mas sempre há o respawn para tentar de novo.
{% elif kills %}
{% set last_kill = kills[-1] %}
Após trocar golpes com {{ last_kill.entity.split(':')[-1] | replace('_', ' ') }}{% if kills | length > 1 %} e outros {{ kills | length - 1 }} inimigos{% endif %}, o capivara prevalece. {% if activity.combat_window.damage_taken %}Levou alguns arranhões, mas nada que a comida não cure.{% endif %}
{% else %}
Hora de respirar — o combate terminou. {% if activity.combat_window.damage_taken %}Os ferimentos do ciclo ainda doem.{% else %}Saiu ileso desta vez.{% endif %}
{% endif %}
```

---

## 10. Logging

### 10.1 `logging_setup.py`

```python
from loguru import logger
from .config import LoggingConfig
import sys

def setup_logging(config: LoggingConfig, bridge_path):
    logger.remove()  # remove handler default

    # Console (stderr)
    logger.add(
        sys.stderr,
        level=config.level,
        format="<green>{time:YYYY-MM-DD HH:mm:ss.SSS}</green> | <level>{level: <8}</level> | <cyan>{name}</cyan> - <level>{message}</level>",
        colorize=True,
    )

    # bridge.log (rotativo)
    logger.add(
        bridge_path / "bridge.log",
        level=config.level,
        rotation=config.rotation,
        retention=config.retention,
        format="{time:YYYY-MM-DDTHH:mm:ss.SSSZ} | {level: <8} | {name} | {message}",
        encoding="utf-8",
        enqueue=True,  # thread-safe
    )
```

### 10.2 Convenções de log
- **INFO:** início/fim de ciclo, decisões importantes, fallback ativado.
- **DEBUG:** I/O de arquivos, conteúdo de prompts, latências.
- **WARNING:** arquivos faltantes, retries, race conditions.
- **ERROR:** falhas de LLM, falhas de I/O, exceções.

---

## 11. Empacotamento e Execução

### 11.1 `__main__.py`

```python
import sys
from .config import Config
from .logging_setup import setup_logging
from .loop import MainLoop
from loguru import logger

def main():
    logger.info("Carregando configuração...")
    try:
        config = Config.load()
    except Exception as e:
        print(f"Erro carregando config: {e}", file=sys.stderr)
        sys.exit(1)

    setup_logging(config.logging, config.bridge.path)
    logger.info(f"Bridge path: {config.bridge.path}")
    logger.info(f"LLM provider: {config.llm.provider} / model: {config.llm.model}")
    logger.info(f"Cycle interval: {config.bridge.cycle_interval_s}s")

    loop = MainLoop(config)
    loop.run()

if __name__ == "__main__":
    main()
```

### 11.2 Execução
```bash
# Instalar (modo desenvolvimento)
cd bridge
pip install -e .

# Rodar
narrator-bridge

# Ou
python -m narrator_bridge

# Com config customizado
NARRATOR_BRIDGE_PATH=/tmp/test narrator-bridge
```

### 11.3 Empacotamento final (futuro)
- Distribuir como wheel em `dist/`.
- Script de inicialização para Windows (`narrator-bridge.bat`) e Linux/macOS (`narrator-bridge.sh`).
- Opcional: empacotar com `pyinstaller` para executável standalone.

---

## 12. Tratamento de Erros — Resumo

| Cenário | Ação |
|---|---|
| `state.json` não existe | Logar DEBUG, esperar próximo poll |
| `state.json` corrompido | Logar WARNING, ignorar ciclo |
| `seq` repetido | Ignorar silenciosamente |
| `seq` menor (mod reiniciado) | Resetar `last_narration`, logar WARNING |
| `window.json` não existe após seq novo | Logar WARNING, marcar seq como processado, criar trigger |
| `paused: true` no state | Logar INFO, pular ciclo, atualizar seq |
| LLM timeout | Retentar com backoff exponencial (3x) |
| LLM erro 5xx | Retentar |
| LLM erro 4xx (exceto 429) | Não retentar, ir para fallback |
| LLM 429 (rate limit) | Retentar com delay maior |
| Fallback falha | Logar ERROR, gravar "[ERRO: narração indisponível]" no log |
| `trigger.flag` já existe ao criar | Logar WARNING, pular criação |
| Mod morto (heartbeat velho) | Logar DEBUG, pausar processamento |
| Disco cheio | Logar ERROR, continuar tentando |
| Exceção não tratada | Logar CRITICAL, continuar loop (não crashar) |

---

## 13. Testes

### 13.1 Unitários
- `test_schemas.py`: valida parse de JSONs de exemplo (`state.json` e `window.json` do `upload/`).
- `test_io.py`: testa escrita atômica, leitura tolerante, delete.
- `test_prompt.py`: valida que o prompt gerado contém campos esperados.
- `test_fallback.py`: valida escolha de template e renderização.

### 13.2 Integração
- `test_loop.py`: mock do LLMClient, simula arquivos no `tmp_path`, executa N ciclos e valida logs.
- Teste de reinício: para o loop no meio, recarrega, verifica que retoma do último seq.

### 13.3 E2E
- Subir um mock do mod (script Python que cria `state.json`/`window.json` sintéticos).
- Rodar o bridge com provider mock.
- Validar `narration.log` tem entradas para cada ciclo.

---

## 14. Próximos Passos

Após implementar o bridge, valide seguindo o `CHECKLIST.md`. Para detalhes de como montar o prompt, consulte `PROMPT_ENGINEERING.md`. Para schemas completos, consulte `SCHEMA.md`.
