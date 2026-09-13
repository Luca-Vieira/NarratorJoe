import os
import httpx
from typing import List, Dict
from .base import LLMClient
from ..config import LLMConfig

class OpenAIProvider(LLMClient):
    def __init__(self, config: LLMConfig):
        self.config = config
        self.api_key = os.environ.get("OPENAI_API_KEY", "")
        if not self.api_key or self.api_key.startswith("sk-..."):
            raise ValueError("OPENAI_API_KEY não configurada no ambiente")

    def complete(self, messages: List[Dict[str, str]]) -> str:
        url = "https://api.openai.com/v1/chat/completions"
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }
        payload = {
            "model": self.config.model,
            "messages": messages,
            "temperature": self.config.temperature,
            "max_tokens": self.config.max_tokens,
        }
        with httpx.Client(timeout=self.config.timeout_s) as client:
            resp = client.post(url, headers=headers, json=payload)
            if resp.status_code in (408, 502, 503, 504):
                raise ConnectionError(f"OpenAI server error: {resp.status_code}")
            resp.raise_for_status()
            data = resp.json()
            return data["choices"][0]["message"]["content"].strip()

    def name(self) -> str:
        return "openai"