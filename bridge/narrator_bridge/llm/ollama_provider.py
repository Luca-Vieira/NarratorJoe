import os
import httpx
from typing import List, Dict
from .base import LLMClient
from ..config import LLMConfig

class OllamaProvider(LLMClient):
    def __init__(self, config: LLMConfig):
        self.config = config
        self.host = os.environ.get("OLLAMA_HOST", "http://localhost:11434").rstrip("/")

    def complete(self, messages: List[Dict[str, str]]) -> str:
        url = f"{self.host}/api/chat"
        payload = {
            "model": self.config.model,
            "messages": messages,
            "stream": False,
            "options": {
                "temperature": self.config.temperature,
                "num_predict": self.config.max_tokens,
            },
        }
        try:
            with httpx.Client(timeout=self.config.timeout_s) as client:
                resp = client.post(url, json=payload)
                resp.raise_for_status()
                data = resp.json()
                return data.get("message", {}).get("content", "").strip()
        except (httpx.ConnectError, httpx.TimeoutException) as e:
            raise ConnectionError(f"Ollama connection error: {e}")

    def name(self) -> str:
        return "ollama"