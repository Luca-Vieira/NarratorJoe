from abc import ABC, abstractmethod
from typing import List, Dict

class LLMClient(ABC):
    @abstractmethod
    def complete(self, messages: List[Dict[str, str]]) -> str:
        pass

    @abstractmethod
    def name(self) -> str:
        pass