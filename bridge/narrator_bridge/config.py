import os
import tomllib
from dataclasses import dataclass
from pathlib import Path
from dotenv import load_dotenv

@dataclass
class BridgeConfig:
    path: Path
    cycle_interval_s: int = 60
    poll_interval_s: int = 1
    mod_dead_timeout_s: int = 30

@dataclass
class LLMConfig:
    provider: str = "openai"
    model: str = "gpt-4o-mini"
    temperature: float = 0.7
    max_tokens: int = 300
    timeout_s: int = 30
    max_retries: int = 3
    retry_base_delay_s: float = 1.0
    retry_max_delay_s: float = 8.0
    enable_fallback: bool = True

@dataclass
class PromptConfig:
    system_prompt_file: str = ""
    language: str = "pt-BR"
    style: str = "storyteller"
    max_chars: int = 400

@dataclass
class LoggingConfig:
    level: str = "INFO"
    rotation: str = "10 MB"
    retention: str = "30 days"
    ring_buffer_size: int = 500

@dataclass
class Config:
    bridge: BridgeConfig
    llm: LLMConfig
    prompt: PromptConfig
    logging: LoggingConfig

    @classmethod
    def load(cls, config_path: Path | str = "narrator_config.toml") -> "Config":
        load_dotenv()

        cfg_file = Path(config_path)
        data = {}
        if cfg_file.exists():
            with open(cfg_file, "rb") as f:
                data = tomllib.load(f)

        bridge_data = data.get("bridge", {})
        raw_bridge_path = os.environ.get("NARRATOR_BRIDGE_PATH") or bridge_data.get("path", "~/ai-narrator")
        bridge_path = Path(os.path.expanduser(raw_bridge_path)).resolve()

        llm_data = data.get("llm", {})
        env_provider = os.environ.get("NARRATOR_LLM_PROVIDER")
        if env_provider:
            llm_data["provider"] = env_provider

        prompt_data = data.get("prompt", {})
        logging_data = data.get("logging", {})

        return cls(
            bridge=BridgeConfig(
                path=bridge_path,
                cycle_interval_s=bridge_data.get("cycle_interval_s", 60),
                poll_interval_s=bridge_data.get("poll_interval_s", 1),
                mod_dead_timeout_s=bridge_data.get("mod_dead_timeout_s", 30),
            ),
            llm=LLMConfig(
                provider=llm_data.get("provider", "openai"),
                model=llm_data.get("model", "gpt-4o-mini"),
                temperature=float(llm_data.get("temperature", 0.7)),
                max_tokens=int(llm_data.get("max_tokens", 300)),
                timeout_s=int(llm_data.get("timeout_s", 30)),
                max_retries=int(llm_data.get("max_retries", 3)),
                retry_base_delay_s=float(llm_data.get("retry_base_delay_s", 1.0)),
                retry_max_delay_s=float(llm_data.get("retry_max_delay_s", 8.0)),
                enable_fallback=bool(llm_data.get("enable_fallback", True)),
            ),
            prompt=PromptConfig(
                system_prompt_file=prompt_data.get("system_prompt_file", ""),
                language=prompt_data.get("language", "pt-BR"),
                style=prompt_data.get("style", "storyteller"),
                max_chars=int(prompt_data.get("max_chars", 400)),
            ),
            logging=LoggingConfig(
                level=logging_data.get("level", "INFO"),
                rotation=logging_data.get("rotation", "10 MB"),
                retention=logging_data.get("retention", "30 days"),
                ring_buffer_size=int(logging_data.get("ring_buffer_size", 500)),
            ),
        )