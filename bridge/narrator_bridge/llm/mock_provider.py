from typing import List, Dict
from .base import LLMClient
from ..config import LLMConfig

class MockProvider(LLMClient):
    def __init__(self, config: LLMConfig):
        self.config = config

    def complete(self, messages: List[Dict[str, str]]) -> str:
        return "Sob o céu vasto deste mundo, o capivara segue firme em sua jornada, desbravando novos horizontes e construindo sua própria história passo a passo."

    def name(self) -> str:
        return "mock"