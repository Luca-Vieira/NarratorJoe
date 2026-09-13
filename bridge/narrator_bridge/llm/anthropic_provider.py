import os
import httpx
from typing import List, Dict
from .base import LLMClient
from ..config import LLMConfig

class AnthropicProvider(LLMClient):
    def __init__(self, config: LLMConfig):
        self.config = config
        self.api_key = os.environ.get("ANTHROPIC_API_KEY", "")
        if not self.api_key or self.api_key.startswith("sk-ant-..."):
            raise ValueError("ANTHROPIC_API_KEY não configurada no ambiente")

    def complete(self, messages: List[Dict[str, str]]) -> str:
        url = "https://api.anthropic.com/v1/messages"
        headers = {
            "x-api-key": self.api_key,
            "anthropic-version": "2023-06-01",
            "Content-Type": "application/json",
        }
        system = ""
        user_msgs = []
        for m in messages:
            if m["role"] == "system":
                system += m["content"] + "\n"
            else:
                user_msgs.append({"role": m["role"], "content": m["content"]})

        payload = {
            "model": self.config.model,
            "system": system.strip(),
            "messages": user_msgs,
            "temperature": self.config.temperature,
            "max_tokens": self.config.max_tokens,
        }
        with httpx.Client(timeout=self.config.timeout_s) as client:
            resp = client.post(url, headers=headers, json=payload)
            if resp.status_code in (408, 502, 503, 504):
                raise ConnectionError(f"Anthropic server error: {resp.status_code}")
            resp.raise_for_status()
            data = resp.json()
            return data["content"][0]["text"].strip()

    def name(self) -> str:
        return "anthropic"