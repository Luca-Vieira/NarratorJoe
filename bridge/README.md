# AI Narrator - Python Bridge

Serviço em Python que processa dados de eventos do Minecraft gerados pelo mod Fabric, monta prompts contextuais estruturados e se comunica com LLMs para gerar a narração em tempo real.

## Requisitos

- Python 3.11+
- Virtualenv ou gerenciador de pacotes pip

## Instalação

No diretório `bridge/`:

```bash
# Criação do ambiente virtual
python -m venv .venv

# Ativação do ambiente virtual
# No Windows:
.venv\Scripts\activate
# No Linux/macOS:
source .venv/bin/activate

# Instalação das dependências
pip install -e .
```

## Configuração

1. Copie `.env.example` para `.env` e configure suas chaves de API (se desejar usar um provedor externo como OpenAI, Anthropic ou Gemini):
   ```bash
   cp .env.example .env
   ```
   *Nota:* Se nenhuma chave for definida, o bridge funcionará perfeitamente utilizando o modo de templates de fallback (Jinja2) ou Ollama local.

2. Edite `narrator_config.toml` se desejar alterar estilo de narração (`storyteller`, `documentary`, `playful`, `epic`), caminho do bridge ou intervalos.

## Execução

Com o ambiente virtual ativado:

```bash
# Executar o entrypoint CLI
narrator-bridge

# Ou diretamente via módulo
python -m narrator_bridge
```

## Executar Testes

```bash
pytest
```