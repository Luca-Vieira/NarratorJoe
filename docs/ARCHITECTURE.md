# Arquitetura Técnica

> Este documento descreve a arquitetura detalhada do sistema AI Narrator, incluindo componentes, fluxos de comunicação, decisões técnicas e tratamento de casos extremos. **Deve ser lido antes de implementar qualquer componente.**

---

## 1. Visão Arquitetural

O sistema segue o padrão **Pipeline com IPC por Arquivos**. Três processos independentes cooperam através de arquivos compartilhados num diretório externo (`~/ai-narrator/`), sem nenhum canal de comunicação direta (sem sockets, sem pipes, sem HTTP interno).

### 1.1 Diagrama de Componentes

```
┌──────────────────────────────────────────────────────────────────────────┐
│                          Processo Minecraft (JVM)                         │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │  Mod Fabric (ai-narrator-mod)                                       │ │
│  │                                                                      │ │
│  │  ┌──────────────┐   ┌──────────────┐   ┌────────────────────────┐  │ │
│  │  │ EventCollector│ → │ StateBuilder │ → │ AtomicJsonWriter       │  │ │
│  │  │ (ring buffer) │   │ (snapshot)   │   │ (temp + rename)        │  │ │
│  │  └──────┬───────┘   └──────────────┘   └────────────┬───────────┘  │ │
│  │         │                                            │               │ │
│  │         │                                 ┌──────────▼──────────┐    │ │
│  │         │                                 │  TriggerWatcher      │    │ │
│  │         │                                 │  (poll trigger.flag) │    │ │
│  │         │                                 └──────────┬──────────┘    │ │
│  │         └────────────────────────────────────────────┘               │ │
│  │                                                                      │ │
│  │  Thread: ServerTickEvents.END_SERVER_TICK                            │ │
│  │  Thread: dedicada para I/O de disco (não bloquear tick)              │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ filesystem (~/ai-narrator/)
                                    ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                          Filesystem (~/ai-narrator/)                     │
│                                                                          │
│  state.json          ← sempre atualizado pelo mod                       │
│  window.json         ← escrito pelo mod, consumido/deletado pelo Python │
│  trigger.flag        ← escrito pelo Python, consumido/deletado pelo mod │
│  heartbeat.json      ← escrito pelo mod (a cada tick de server)         │
│  last_narration.txt  ← escrito pelo Python após cada narração           │
│  prompt.json         ← escrito pelo Python (debug)                      │
│  narration.log       ← append-only, escrito pelo Python                 │
│  bridge.log          ← append-only, escrito pelo Python                 │
│  narrator_config.toml← editável pelo usuário                            │
└──────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ filesystem (mesma pasta)
                                    ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                          Processo Python (narrator_bridge)               │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │  MainLoop (asyncio ou thread)                                       │ │
│  │                                                                      │ │
│  │  ┌──────────────┐   ┌──────────────┐   ┌────────────────────────┐  │ │
│  │  │ SeqWatcher   │ → │ PromptBuilder│ → │ LLMClient (interface)  │  │ │
│  │  │ (poll state) │   │ (monta msg)  │   │  ├─ OpenAIProvider     │  │ │
│  │  └──────────────┘   └──────────────┘   │  ├─ AnthropicProvider  │  │ │
│  │                                          │  ├─ GeminiProvider    │  │ │
│  │  ┌──────────────┐   ┌──────────────┐   │  └─ OllamaProvider    │  │ │
│  │  │ HeartbeatChk │   │ FallbackMgr  │   └────────────┬───────────┘  │ │
│  │  │ (mod vivo?)  │   │ (templates)  │                │               │ │
│  │  └──────────────┘   └──────────────┘                │               │ │
│  │                                                    ▼               │ │
│  │  ┌──────────────────────────────────────────────────────────────┐  │ │
│  │  │ NarrationWriter (log + last_narration.txt)                   │  │ │
│  │  │ TriggerEmitter (cria trigger.flag)                           │  │ │
│  │  └──────────────────────────────────────────────────────────────┘  │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ HTTPS
                                    ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                          LLM Provider (externo)                          │
│  OpenAI / Anthropic / Gemini / Ollama (local)                            │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Por que IPC por Arquivos?

A escolha de comunicação via arquivos (em vez de sockets, gRPC, pipes ou HTTP interno) foi deliberada e baseada nos seguintes trade-offs:

### Vantagens
- **Tolerância a falhas:** se o Python morrer, o mod continua coletando eventos sem perceber. Se o mod morrer, o Python detecta via heartbeat e pausa. Não há conexão para "quebrar".
- **Debugabilidade:** todos os artefatos (`state.json`, `window.json`, `prompt.json`, `narration.log`) ficam visíveis em disco. Qualquer ciclo pode ser auditado posteriormente.
- **Reinicialização independente:** o Python pode ser reiniciado para trocar o prompt sem reiniciar o Minecraft. O mod pode ser recarregado com `/reload` sem matar o Python.
- **Simplicidade:** não há protocolo binário, não há serialização customizada, não há schema gRPC. Apenas arquivos JSON.
- **Portabilidade:** funciona em Windows, Linux e macOS sem mudanças (apenas o caminho `~/ai-narrator/` muda).

### Desvantagens (e mitigações)
- **Latência de I/O de disco:** mitigada por escritas atômicas (temp + rename) que evitam bloqueios. SSDs modernos tornam isso irrelevante (escrita de <2KB em <1ms).
- **Risco de leitura parcial:** mitigado pelo padrão temp + rename — o leitor nunca vê um arquivo pela metade, pois o rename é atômico no nível do filesystem.
- **Sem push:** ambos os lados precisam fazer polling. Mitigado por intervalos curtos (1s no mod para trigger, 1s no Python para novos seqs).
- **Concorrência:** se ambos tentarem escrever no mesmo arquivo simultaneamente, há corrupção. Mitigado pelo contrato: **cada arquivo é escrito por um único processo**. `state.json`, `window.json`, `heartbeat.json` são sempre do mod. `trigger.flag`, `last_narration.txt`, `prompt.json`, `narration.log`, `bridge.log` são sempre do Python. `narrator_config.toml` é somente leitura em runtime.

---

## 3. Diagrama de Sequência — Ciclo Completo

```
  Mod (Java)                     Filesystem                  Python
  ─────────                      ─────────                   ──────
       │                              │                          │
       │  [tick: coleta eventos]      │                          │
       │  [em memória: ring buffer]   │                          │
       │                              │                          │
       │ ─────── trigger.flag? ──────►│                          │
       │                              │ ◄──── poll state.json ───│
       │                              │      (a cada 1s)         │
       │                              │                          │
       │  [se trigger.flag existe]    │                          │
       │  [deleta trigger.flag]       │                          │
       │  [zera ring buffer]          │                          │
       │                              │                          │
       │ ─── escreve state.json ─────►│                          │
       │     (temp + rename)          │                          │
       │ ─── escreve window.json ────►│                          │
       │ ─── escreve heartbeat.json ─►│                          │
       │                              │                          │
       │                              │ ──── detecta seq novo ───│
       │                              │      (seq > last_seq)    │
       │                              │                          │
       │                              │ ◄── lê state.json ───────│
       │                              │ ◄── lê window.json ──────│
       │                              │ ◄── lê last_narration ───│
       │                              │                          │
       │                              │ ─── deleta window.json ──►
       │                              │     (sinaliza consumo)   │
       │                              │                          │
       │                              │ ─── monta prompt.json ───►
       │                              │                          │
       │                              │                  ┌───────┴───────┐
       │                              │                  │ chama LLM    │
       │                              │                  │ (retry 3x)   │
       │                              │                  └───────┬───────┘
       │                              │                          │
       │                              │ ─── append narration.log►│
       │                              │ ─── escreve last_narr.txt►
       │                              │ ─── cria trigger.flag ───►
       │                              │                          │
       │ ─────── trigger.flag? ──────►│                          │
       │  [sim! novo ciclo]           │                          │
       │                              │                          │
       ▼                              ▼                          ▼
```

### 3.1 Timeline detalhada (com tempos típicos)

| t (ms) | Mod | Python |
|---|---|---|
| 0 | Recebe `trigger.flag`, deleta, zera buffer | — |
| 0–500 | Coleta eventos (ticks do server) | — |
| 500 | Escreve `state.json` + `window.json` + `heartbeat.json` | — |
| 500–510 | — | Detecta novo seq (poll 1s) |
| 510 | — | Lê 3 arquivos |
| 510 | — | Deleta `window.json` |
| 510–520 | — | Monta `prompt.json` |
| 520–3200 | — | Chama LLM (timeout 30s, retry 3x) |
| 3200 | — | Append em `narration.log` + `last_narration.txt` |
| 3200 | — | Cria `trigger.flag` |
| 3200 | Recebe `trigger.flag` no próximo tick (1–50ms depois) | — |
| **Total latência:** ~3.2s desde evento até narração gravada |

---

## 4. Contrato de Comunicação

### 4.1 Direção de escrita por arquivo

| Arquivo | Escrito por | Lido por | Frequência |
|---|---|---|---|
| `state.json` | Mod | Python | a cada ciclo (60s) |
| `window.json` | Mod | Python | a cada ciclo, deletado pelo Python após leitura |
| `heartbeat.json` | Mod | Python | a cada 5s |
| `trigger.flag` | Python | Mod | a cada ciclo, deletado pelo mod após leitura |
| `last_narration.txt` | Python | Python (e o mod, opcionalmente, para exibir no chat) | a cada narração |
| `prompt.json` | Python | — (debug) | a cada ciclo |
| `narration.log` | Python | — (humano) | append a cada narração |
| `bridge.log` | Python | — (humano) | append contínuo |
| `narrator_config.toml` | humano | ambos | somente leitura em runtime |

### 4.2 Regras de atomicidade

1. **Toda escrita de JSON é atômica:**
   - Escrever em `<arquivo>.tmp` (no mesmo diretório, para garantir mesmo filesystem).
   - Chamar `fsync()` no `.tmp`.
   - Renomear `.tmp` → `<arquivo>` (atômico em POSIX e Windows NTFS).
   - Em Java: `Files.move(tmp, target, REPLACE_EXISTING, ATOMIC_MOVE)`.
   - Em Python: `os.replace(tmp, target)` (atômico em todos os SOs suportados).

2. **Toda leitura deve tolerar arquivo inexistente:**
   - Se o arquivo não existe, retornar `None` (Python) ou `Optional.empty()` (Java).
   - Se o arquivo está corrompido (JSON inválido), logar erro e tratar como inexistente.

3. **Detecção de ciclo novo (Python):**
   - Ler `state.json` → comparar `seq` com `last_seq` mantido em memória.
   - Se `seq > last_seq`: novo ciclo. Processar. Atualizar `last_seq`.
   - Se `seq == last_seq`: ciclo já processado. Ignorar.
   - Se `seq < last_seq`: mod foi reiniciado. Resetar estado interno do Python (limpar `last_narration.txt`? decidir em `PYTHON_BRIDGE_SPEC.md`).

4. **Detecção de trigger (Mod):**
   - A cada tick de server (20 Hz), verificar se `trigger.flag` existe (cache de 1s para evitar I/O excessivo).
   - Se existe: ler, deletar, zerar ring buffer, marcar próximo tick para escrever novos JSONs.

---

## 5. Decisões Técnicas

### 5.1 Por que Fabric e não Forge/NeoForge?

- **Código acessível:** o usuário citou explicitamente "fabric pela acessibilidade do código". Fabric tem mixins diretos, API enxuta e toolchain simples.
- **Atualização rápida:** Fabric costuma ter builds para novas versões do Minecraft em horas/dias, ideal para 1.21.1.
- **Compatibilidade com outros mods:** Fabric é amplamente usado no ecossistema de mods de performance (Sodium, Lithium), aumentando a chance de convivência pacífica.
- **Toolchain:** Fabric Loom simplifica o build com Gradle. Não há necessidade de setup complexo de MCP mappings.

### 5.2 Por que Python como bridge?

- **Ecossistema LLM:** os SDKs oficiais (openai, anthropic, google-genai) são Python-first. SDKs Java existem mas são menos mantidos.
- **Velocidade de iteração:** ajustar prompt, testar novo provider, adicionar fallback é trivial em Python.
- **Não bloqueia o Minecraft:** o Python roda como processo separado; mesmo que ele trave ou fique lento, o Minecraft não é afetado.
- **Portabilidade:** o mesmo bridge Python funciona em qualquer SO onde o Minecraft roda.

### 5.3 Por que LLM genérico (provider-agnostic)?

- **Evita lock-in:** o usuário pode começar com Ollama local (grátis) e migrar para OpenAI quando quiser qualidade superior, sem reescrever o bridge.
- **Redundância:** se um provider estiver em outage, é possível trocar via variável de ambiente sem reiniciar o mod.
- **Custo:** permite usar modelos locais (Ollama) para desenvolvimento/testes sem gastar API.

### 5.4 Por que intervalo base de 60s?

- **Densidade de eventos:** em 60s, um jogador típico produz 5–20 eventos significativos (quebra 10–30 blocos, mata 0–3 mobs, come 1–2 comidas, anda 20–100 blocos). Isso dá contexto suficiente para uma narração rica sem sobrecarregar o prompt.
- **Latência percebida:** 60s é curto o suficiente para o jogador sentir que a narração acompanha sua jornada, mas longo o suficiente para não ser irritante (a cada ciclo, uma narração nova).
- **Custo:** a 60s, são ~60 chamadas LLM por hora. Com gpt-4o-mini (~$0.15/1M input tokens), isso custa alguns centavos por hora. Com Claude Haiku, similar. Com Ollama local, grátis.
- **Adaptativo:** o Python pode encurtar o intervalo para 30s se o `window.json` estiver "cheio" (mais de N eventos) e alongar para 120s se estiver vazio (jogador parado/AFK).

### 5.5 Por que deletar `window.json` em vez de sobrescrever?

- **Sinalização implícita:** o mod sabe que o Python consumiu a janela quando o arquivo some. Se o mod fosse sobrescrever, precisaria de um canal separado para saber quando o Python terminou de ler.
- **Simplicidade do contrato:** mod só escreve `window.json` se o arquivo não existir. Python só lê se existir. Simétrico e sem ambiguidade.
- **Race condition evitada:** se o mod sobrescrevesse, poderia estar escrevendo enquanto o Python lê. Com delete-then-write, o mod espera o arquivo ser deletado antes de escrever de novo.

### 5.6 Por que `trigger.flag` em vez de timestamp no `state.json`?

- **Semântica clara:** flag = "mod, comece uma nova janela agora". Timestamp = "eu te avisei às HH:MM:SS" (precisaria de lógica de comparação).
- **Idempotência:** se o Python criar a flag e o mod demorar a ver, a flag continua lá. Quando o mod finalmente vir, ele age. Não há "perda" do sinal.
- **Atomicidade:** criar/deletar um arquivo vazio é mais barato e atômico do que atualizar um campo num JSON.

---

## 6. Modos de Operação

### 6.1 Modo Normal (online)
- Python rodando, LLM acessível.
- Fluxo completo descrito na seção 3.

### 6.2 Modo Degradado (LLM fora)
- Python rodando, mas todas as 3 tentativas de LLM falharam.
- Python ativa `FallbackMgr`: gera narração via templates baseados em `window.json` (ex: "O jogador coletou 5 iron_ingot e matou 1 pig.").
- Narração de fallback é marcada com `[FALLBACK]` no log.
- Próximo ciclo tenta LLM novamente.

### 6.3 Modo Pausado (mod morto)
- Python detecta `heartbeat.json` com `last_seen` > 30s atrás.
- Python para de processar ciclos.
- Log: "Mod inativo há Xs, aguardando heartbeat..."
- Quando mod volta (heartbeat atualiza), Python retoma automaticamente.

### 6.4 Modo Mod-Only (Python morto)
- Mod continua coletando eventos no ring buffer.
- Mod continua escrevendo `state.json` + `window.json` a cada 60s mesmo sem trigger.
- Quando `window.json` atinge 60s de idade sem ser consumido, o mod **sobrescreve** com a próxima janela (política LIFO — eventos antigos são descartados).
- Isso garante que quando o Python voltar, ele tenha dados frescos.

### 6.5 Modo Pausado pelo Jogador
- Jogador executa `/narrator pause` no chat.
- Mod seta `paused: true` no `state.json` e para de coletar eventos.
- Python vê `paused: true` e não gera narração (apenas loga "Jogador pausou a narração").
- Jogador executa `/narrator resume` para retomar.

---

## 7. Concorrência e Threads

### 7.1 No Mod (Java)
- **Thread principal do server (tick):** 20 Hz. Apenas coleta eventos em memória (ring buffer). **Proibido** I/O de disco aqui.
- **Thread dedicada de I/O:** criada pelo mod, responsável por:
  - Verificar `trigger.flag` a cada 1s.
  - Escrever `state.json`, `window.json`, `heartbeat.json` (atômico).
- **Sincronização:** ring buffer é `ConcurrentLinkedQueue` ou similar; escritas de JSON usam `synchronized` no caminho do arquivo.

### 7.2 No Python
- **Loop principal:** thread única com `asyncio` ou loop simples `while True: sleep(1)`.
- **Chamada LLM:** síncrona dentro do loop (não paralela). Se precisar de paralelismo no futuro, usar `asyncio.gather` para múltiplos prompts.
- **I/O de disco:** síncrono, mas rápido (escritas atômicas de arquivos pequenos).

### 7.3 Invariantes de concorrência
1. **Apenas um processo escreve em cada arquivo** (ver seção 4.1).
2. **Ring buffer do mod é a única fonte de verdade para eventos da janela atual** — `window.json` é uma snapshot, não o estado.
3. **`seq` é monotônico dentro de uma sessão do mod** — nunca decrementa, nunca pula valores.
4. **`last_narration.txt` é sempre lido antes de deletar `window.json`** — garante continuidade narrativa.

---

## 8. Tratamento de Casos Extremos

### 8.1 Mod reiniciado (jogador saiu e voltou ao mundo)
- `seq` volta para 1.
- Python detecta `seq < last_seq`, reset `last_seq = 1`, limpa `last_narration.txt` (não há continuidade com sessão anterior).
- Log: "Mod reiniciado, resetando contexto narrativo."
- Próximo ciclo processa normalmente.

### 8.2 Python reiniciado
- Python perde `last_seq` em memória.
- Lê `last_narration.txt` (persistente) — ok.
- Lê `state.json` atual → assume `last_seq = state.seq` (não processa o ciclo atual, espera próximo).
- Log: "Bridge reiniciado, aguardando próximo ciclo."
- Isso evita processar a mesma janela duas vezes.

### 8.3 Disco cheio
- Mod: falha ao escrever JSON → loga erro, mantém ring buffer em memória, tenta novamente no próximo ciclo.
- Python: falha ao escrever log → loga no stderr, mas continua tentando.
- Não há recuperação automática; o usuário precisa liberar espaço.

### 8.4 LLM muito lento (>30s)
- Timeout de 30s por chamada.
- Após timeout, conta como falha → retry com backoff (1s, 2s, 4s).
- Após 3 falhas, ativa fallback de templates.
- Próximo ciclo tenta LLM novamente.

### 8.5 Clock do sistema mudou
- Timestamps podem ficar inconsistentes.
- Mitigação: o mod usa `System.currentTimeMillis()` e o Python usa `time.time() * 1000`. Ambos são monotômicos o suficiente na prática.
- Se detectar `window_end < window_start`, logar warning e usar 0 como duração.

### 8.6 Multiplayer (servidor dedicado)
- O mod roda no **server side** (não no client). Cada player conectado tem seu próprio `state.json` e `window.json`?
- **Decisão inicial:** o mod suporta apenas **singleplayer ou servidor com 1 jogador**. Multiplayer com vários jogadores fica como extensão futura (precisaria de `state_<player>.json` etc.).
- Documentar essa limitação no `fabric.mod.json` e no README do mod.

---

## 9. Observabilidade

### 9.1 Logs do Python (`bridge.log`)
Formato (uma linha por evento):
```
2024-01-15T14:23:01.123Z [INFO] [loop] Ciclo #42 iniciado (seq=42)
2024-01-15T14:23:01.124Z [DEBUG] [io] Lendo state.json (3.2KB)
2024-01-15T14:23:01.125Z [DEBUG] [io] Lendo window.json (1.8KB)
2024-01-15T14:23:01.126Z [DEBUG] [io] Lendo last_narration.txt (245B)
2024-01-15T14:23:01.127Z [DEBUG] [io] Deletando window.json
2024-01-15T14:23:01.130Z [INFO] [prompt] Prompt montado (1.4KB, 312 tokens)
2024-01-15T14:23:01.131Z [INFO] [llm] Chamando provider=openai model=gpt-4o-mini
2024-01-15T14:23:03.245Z [INFO] [llm] Resposta em 2114ms (87 tokens, $0.0001)
2024-01-15T14:23:03.246Z [INFO] [narration] "O capivara avança pela savana..."
2024-01-15T14:23:03.247Z [DEBUG] [io] Append narration.log
2024-01-15T14:23:03.248Z [DEBUG] [io] Escrevendo last_narration.txt
2024-01-15T14:23:03.250Z [DEBUG] [io] Criando trigger.flag
2024-01-15T14:23:03.251Z [INFO] [loop] Ciclo #42 concluído em 2128ms
```

### 9.2 Logs do Mod (console do Minecraft)
Formato (prefixo `[AI-Narrator]`):
```
[14:23:00] [Server thread/INFO]: [AI-Narrator] Trigger recebido, iniciando ciclo 42
[14:23:00] [AI-Narrator-IO/INFO]: [AI-Narrator] state.json escrito (3.2KB)
[14:23:00] [AI-Narrator-IO/INFO]: [AI-Narrator] window.json escrito (1.8KB)
```

### 9.3 Log de narração (`narration.log`)
Formato append-only com cabeçalho de contexto:
```
=== Ciclo #42 | 2024-01-15T14:23:00 | seq=42 | provider=openai | model=gpt-4o-mini | latency=2114ms ===
[STATE] BusinessCapybara em savanna, noite, health=20/20, biome=savanna (desde 1788398000000)
[WINDOW] 60s: quebrou 14 short_grass, ganhou 5 iron_ingot, matou 1 pig, abriu 5 chests, usou furnace
[HIGHLIGHTS] advancement:Acquire Hardware | effect_gained:poison | food_low:4
[PREVIOUS] "...o capivara encontra ferro pela primeira vez..."
[NARRATION]
O capivara avança pela savana sob a lua minguante, suas mãos calejadas já familiarizadas com o peso do ferro recém-adquirido. Ao redor, o capim se rende à sua passagem, e um porco selvagem cai sob sua lâmina — jantar garantido para a noite que se avizinha.
[/NARRATION]
```

---

## 10. Próximos Passos

Após absorver esta arquitetura, prossiga para:
- `MOD_SPEC.md` — detalhes de implementação do mod Fabric.
- `PYTHON_BRIDGE_SPEC.md` — detalhes de implementação do bridge Python.
- `SCHEMA.md` — referência completa dos arquivos transitados.
