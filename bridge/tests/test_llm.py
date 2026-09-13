import pytest
from narrator_bridge.config import LLMConfig
from narrator_bridge.llm.factory import create_llm_client
from narrator_bridge.llm.mock_provider import MockProvider

def test_mock_provider():
    cfg = LLMConfig(provider="mock")
    client = create_llm_client(cfg)
    assert client.name() == "mock"
    res = client.complete([{"role": "user", "content": "hello"}])
    assert len(res) > 20

def test_missing_keys_graceful_mock():
    # Sem chaves no ambiente, create_llm_client deve logar warning e cair no mock graciosamente
    cfg_openai = LLMConfig(provider="openai")
    client_openai = create_llm_client(cfg_openai)
    assert client_openai.name() == "mock"

    cfg_anthropic = LLMConfig(provider="anthropic")
    client_anthropic = create_llm_client(cfg_anthropic)
    assert client_anthropic.name() == "mock"

    cfg_gemini = LLMConfig(provider="gemini")
    client_gemini = create_llm_client(cfg_gemini)
    assert client_gemini.name() == "mock"