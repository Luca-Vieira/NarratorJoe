from .base import LLMClient
from ..config import LLMConfig
from .openai_provider import OpenAIProvider
from .anthropic_provider import AnthropicProvider
from .gemini_provider import GeminiProvider
from .ollama_provider import OllamaProvider
from .mock_provider import MockProvider
from loguru import logger

def create_llm_client(config: LLMConfig) -> LLMClient:
    provider = config.provider.lower()
    try:
        if provider == "openai":
            return OpenAIProvider(config)
        if provider == "anthropic":
            return AnthropicProvider(config)
        if provider == "gemini":
            return GeminiProvider(config)
        if provider == "ollama":
            return OllamaProvider(config)
        if provider == "mock":
            return MockProvider(config)
    except ValueError as e:
        logger.warning(f"Provider {provider} não pôde ser inicializado ({e}). Ativando MockProvider como fallback seguro.")
        return MockProvider(config)

    raise ValueError(f"Provider desconhecido: {provider}")