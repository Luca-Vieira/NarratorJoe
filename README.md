# AI Narrator

Sistema integrado de narração em tempo real para Minecraft 1.21.1 usando Fabric e um bridge Python alimentado por LLM.

## Arquitetura do Monorepo

- `mod/`: Mod Fabric (Minecraft 1.21.1, Java 21) responsável por monitorar o mundo e gerar snapshots atômicos (`state.json`, `window.json`, `heartbeat.json`).
- `bridge/`: Serviço Python (Python 3.11+, Pydantic v2, Tenacity) que monitora o diretório de dados (`~/ai-narrator`), gera o prompt estruturado com tom adaptativo e chama o LLM ou aciona templates de fallback (Jinja2).
- `docs/`: Especificações completas do projeto (`ARCHITECTURE.md`, `CHECKLIST.md`, `MOD_SPEC.md`, `PYTHON_BRIDGE_SPEC.md`, `SCHEMA.md`, `PROMPT_ENGINEERING.md`).

## Comunicação (IPC via Filesystem)

O mod e o bridge comunicam-se via arquivos no diretório `~/ai-narrator/` (ou configurado em `narrator_config.toml` e `config/ai-narrator.json`):
- `state.json`: Estado do jogador, bioma, spawn, itens em mãos e entidades.
- `window.json`: Janela de eventos recentes (blocos, itens, estações, combate detalhado e destaques).
- `heartbeat.json`: Sinal de vida do mod a cada 5s.
- `trigger.flag`: Sinalizador do Python para iniciar novo ciclo no mod.
- `last_narration.txt`: Texto da última narração para continuidade de contexto.
- `prompt.json`: Cópia de depuração do prompt construído.
- `narration.log`: Histórico de narrações geradas em append-only.
- `bridge.log`: Log rotativo do processo Python.
