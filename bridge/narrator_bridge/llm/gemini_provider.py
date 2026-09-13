import os
import httpx
from typing import List, Dict
from .base import LLMClient
from ..config import LLMConfig

class GeminiProvider(LLMClient):
    def __init__(self, config: LLMConfig):
        self.config = config
        self.api_key = os.environ.get("GEMINI_API_KEY", "")
        if not self.api_key:
            raise ValueError("GEMINI_API_KEY não configurada no ambiente")

    def complete(self, messages: List[Dict[str, str]]) -> str:
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{self.config.model}:generateContent"
        headers = {
            "x-goog-api-key": self.api_key,
            "Content-Type": "application/json",
        }
        system_instruction = ""
        contents = []
        for m in messages:
            if m["role"] == "system":
                system_instruction += m["content"] + "\n"
            else:
                role = "user" if m["role"] == "user" else "model"
                contents.append({"role": role, "parts": [{"text": m["content"]}]})

        payload = {
            "system_instruction": {"parts": [{"text": system_instruction.strip()}]},
            "contents": contents,
            "generationConfig": {
                "temperature": self.config.temperature,
                "maxOutputTokens": self.config.max_tokens,
            },
        }
        with httpx.Client(timeout=self.config.timeout_s) as client:
            resp = client.post(url, headers=headers, json=payload)
            if resp.status_code in (408, 500, 502, 503, 504):
                raise ConnectionError(f"Gemini server error: {resp.status_code}")
            resp.raise_for_status()
            data = resp.json()
            cands = data.get("candidates", [])
            if not cands:
                return "O silêncio ecoa pela vasta paisagem enquanto o capivara continua sua jornada."
            parts = cands[0].get("content", {}).get("parts", [])
            return "".join(p.get("text", "") for p in parts).strip()

    def name(self) -> str:
        return "gemini"