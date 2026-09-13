# Checklist de Implementação

> Use este checklist para validar que a implementação está completa e aderente às specs. **Cada item deve ser marcado como ✅ apenas após verificação ativa (não presumir).**

---

## 1. Pré-requisitos

- [ ] JDK 21 instalado e configurado (`java -version` retorna 21.x)
- [ ] Python 3.11+ instalado (`python --version` retorna 3.11+)
- [ ] Minecraft 1.21.1 com Fabric Loader ≥ 0.16.0 instalado
- [ ] Fabric API instalada no profile do Minecraft
- [ ] Gradle 8.x disponível (ou wrapper `./gradlew`)
- [ ] Editor de código com suporte a Java e Python (IntelliJ, VS Code, etc.)
- [ ] Pelo menos um LLM provider configurado:
  - [ ] OpenAI API key válida (se usar OpenAI)
  - [ ] Anthropic API key válida (se usar Anthropic)
  - [ ] Gemini API key válida (se usar Gemini)
  - [ ] Ollama rodando localmente com modelo baixado (se usar Ollama)
- [ ] Git inicializado no repositório

---

## 2. Estrutura do Projeto

- [ ] Diretório `ai-narrator/` criado na raiz do repositório
- [ ] Subpasta `mod/` para o projeto Fabric (Gradle)
- [ ] Subpasta `bridge/` para o projeto Python
- [ ] Subpasta `docs/` contendo todos os `.md` desta spec
- [ ] `.gitignore` adequado (ignora `build/`, `.gradle/`, `__pycache__/`, `.venv/`, `*.log`)
- [ ] `README.md` na raiz do monorepo explicando a estrutura

---

## 3. Mod Fabric

### 3.1 Setup do projeto
- [ ] `build.gradle` configurado com Fabric Loom plugin
- [ ] `gradle.properties` com versões corretas (minecraft 1.21.1, yarn mappings, loader 0.16.x, fabric API 0.102.x)
- [ ] `settings.gradle` com `pluginManagement` apontando para Fabric maven
- [ ] `src/main/resources/fabric.mod.json` válido (validar com `jq .`)
- [ ] `src/main/resources/ai-narrator.mixins.json` válido
- [ ] `src/main/java/com/example/ainarrator/AINarratorMod.java` implementado
- [ ] `src/main/resources/assets/ai-narrator/icon.png` presente (128x128)

### 3.2 Configuração
- [ ] `ModConfig.java` carrega `config/ai-narrator.json`
- [ ] Defaults aplicados quando config não existe
- [ ] Config salvo automaticamente no primeiro run
- [ ] `bridge_path` respeita `~` (expande para home do usuário)
- [ ] Diretório do bridge criado automaticamente se não existir

### 3.3 Coleta de eventos
- [ ] `EventCollector` singleton com `ConcurrentLinkedQueue`
- [ ] Limite de 500 eventos no ring buffer (configurável)
- [ ] Handlers registrados para:
  - [ ] `ServerTickEvents.END_SERVER_TICK`
  - [ ] `PlayerBlockBreakEvents.AFTER` (blocks_broken)
  - [ ] Mixin para block place (blocks_placed)
  - [ ] Mixin em `PlayerEntity.sendPickup` (items_gained)
  - [ ] Mixin em `PlayerEntity.dropItem` (items_lost)
  - [ ] `ServerLivingEntityEvents.AFTER_DEATH` (kills/death_nearby)
  - [ ] `ServerLivingEntityEvents.ALLOW_DAMAGE` (damage_dealt/taken)
  - [ ] Mixin em `PlayerEntity.openHandledScreen` (containers_opened)
  - [ ] `UseBlockCallback` para stations
  - [ ] Mixin em `LivingEntity.addStatusEffect` (effect_gained)
  - [ ] Mixin em `LivingEntity.removeStatusEffect` (effect_lost)
  - [ ] Callback de advancement (verificar disponibilidade)
- [ ] Eventos derivados calculados:
  - [ ] `food_low` (quando food < threshold)
  - [ ] `health_low` (quando health < threshold)
  - [ ] `biome_change` (comparar biome por tick)
  - [ ] `dimension_change`
  - [ ] `weather_change`

### 3.4 Estado do jogador
- [ ] `PlayerState` construído com todos os campos do schema
- [ ] `period` calculado corretamente de `day_time`
- [ ] `BiomeHistory` mantém `current` + `previous` em memória
- [ ] `SpawnHistory` calcula `dwell_ms` acumulando quando player está perto
- [ ] `is_home` marcado quando `dwell_ms > threshold`
- [ ] `held_items_recent` mantém últimos 5 (FIFO)
- [ ] `entities_nearby` escaneado a cada 20 ticks, raio 32, max 64 entidades
- [ ] Classificação de entidades (hostil/passivo/neutro) correta

### 3.5 I/O de disco
- [ ] `AtomicJsonWriter` implementado (temp + fsync + atomic move)
- [ ] Fallback para rename não-atômico em filesystems exóticos
- [ ] UTF-8 sem BOM
- [ ] 2 espaços de indentação no JSON
- [ ] `TriggerWatcher` roda em thread dedicada
- [ ] Polling de `trigger.flag` a cada 1s (configurável)
- [ ] `triggerPending` volatile para handoff seguro com thread do tick
- [ ] `processTrigger` executa no tick do server (não na thread do watcher)

### 3.6 Heartbeat
- [ ] `heartbeat.json` escrito a cada 5s (configurável)
- [ ] Contém: `last_seen`, `tps`, `player_count`, `mod_version`, `seq`
- [ ] Escrita atômica

### 3.7 Comandos
- [ ] `/narrator pause` seta `paused=true` no state
- [ ] `/narrator resume` seta `paused=false`
- [ ] `/narrator status` mostra info no chat
- [ ] `/narrator reload` recarrega config
- [ ] Comandos exigem op level 2+

### 3.8 Mixins
- [ ] `ai-narrator.mixins.json` declarado no `fabric.mod.json`
- [ ] Todos os mixins compilam sem erro
- [ ] Mixins verificados em runtime (não causam crash ao carregar)
- [ ] Nomes de métodos verificados para 1.21.1 (yarn mappings)

### 3.9 Testes do mod
- [ ] Mod carrega sem erros no log
- [ ] `/narrator status` responde corretamente
- [ ] Quebrar bloco gera entrada em `window.json`
- [ ] Matar mob gera entrada em `combat_window.kills`
- [ ] Tomar dano gera entrada em `combat_window.damage_taken`
- [ ] Trocar de bioma atualiza `biome.current` e preenche `biome.previous`
- [ ] Criar `trigger.flag` manualmente → mod detecta e gera novos JSONs

---

## 4. Bridge Python

### 4.1 Setup do projeto
- [ ] `pyproject.toml` configurado com todas as dependências
- [ ] `narrator_bridge/` pacote Python criado
- [ ] `narrator_config.toml` template presente
- [ ] `.env.example` presente
- [ ] `pip install -e .` funciona sem erro
- [ ] `narrator-bridge` script de entrypoint funciona

### 4.2 Configuração
- [ ] `config.py` carrega `.env` e `narrator_config.toml`
- [ ] Defaults aplicados quando config ausente
- [ ] `bridge.path` expande `~`
- [ ] Diretório do bridge criado automaticamente
- [ ] Validação de provider/model no startup (falha cedo se config inválida)

### 4.3 Schemas (Pydantic)
- [ ] `State` model corresponde exatamente ao `state.json` do `SCHEMA.md`
- [ ] `Window` model corresponde exatamente ao `window.json` do `SCHEMA.md`
- [ ] `CombatWindow` inclui `damage_dealt`, `damage_taken`, `kills`, `deaths_nearby`, `player_death`
- [ ] `Highlight` aceita todos os tipos listados
- [ ] Forward refs resolvidas (`Activity.model_rebuild()`)
- [ ] Teste: parsear `upload/state.json` e `upload/window.json` sem erro

### 4.4 I/O
- [ ] `read_json_atomic` retorna `Optional[Model]` (None se arquivo faltante/corrompido)
- [ ] `write_json_atomic` usa temp + fsync + `os.replace`
- [ ] `write_text_atomic` implementado
- [ ] `append_text` para logs (não atômico, mas com flush + fsync)
- [ ] `delete_if_exists` não lança exceção se arquivo não existe
- [ ] `touch` não falha se arquivo já existe (tratar no caller)

### 4.5 LLMClient
- [ ] `LLMClient` ABC definido com `complete()` e `name()`
- [ ] `OpenAIProvider` implementado e testado
- [ ] `AnthropicProvider` implementado (separando system do messages)
- [ ] `GeminiProvider` implementado (usando `system_instruction`)
- [ ] `OllamaProvider` implementado (verifica modelo disponível no init)
- [ ] `create_llm_client` factory retorna provider correto
- [ ] Erros de API (4xx, 5xx) propagam como exceções para o retry tratar

### 4.6 Loop principal
- [ ] `MainLoop.run()` bloqueia até KeyboardInterrupt
- [ ] `_tick()` chamado a cada `poll_interval_s`
- [ ] Health check (heartbeat) implementado
- [ ] Detecção de seq novo correta
- [ ] Detecção de mod reiniciado (seq menor) reseta `last_narration`
- [ ] Player pausado pula ciclo mas atualiza `last_seq`
- [ ] `window.json` deletado após leitura bem-sucedida
- [ ] `trigger.flag` criado após salvar narração
- [ ] Carregamento inicial de `last_narration.txt` ao iniciar

### 4.7 Retry e Fallback
- [ ] `tenacity` configurado com `stop_after_attempt(max_retries)`
- [ ] `wait_exponential` com `multiplier=base_delay`, `max=max_delay`
- [ ] `retry_if_exception_type((TimeoutError, ConnectionError))`
- [ ] Antes de cada retry, loga warning com número da tentativa
- [ ] Após esgotar retries, ativa `FallbackManager`
- [ ] `FallbackManager` escolhe template por atividade dominante
- [ ] Templates Jinja2 renderizam sem erro
- [ ] Narração de fallback marcada com `[FALLBACK]` no log

### 4.8 Prompt
- [ ] `PromptBuilder` gera system + user messages
- [ ] System prompt inclui regras, estilo, narração anterior
- [ ] User message contém: ESTADO ATUAL, JANELA, COMBATE, DESTAQUES, NARRAÇÃO ANTERIOR, INSTRUÇÃO
- [ ] `_derive_alias` funciona (BusinessCapybara → capivara)
- [ ] `_infer_mood_hint` retorna string apropriada
- [ ] `_clean_narration` remove aspas extras, markdown, prefixes
- [ ] `_validate_narration` rejeita narrações < 50 chars ou com termos proibidos
- [ ] Anti-repetição: Jaccard > 0.6 dispara regeneração
- [ ] Ring buffer de últimas 5 narrações incluído no prompt

### 4.9 Logging
- [ ] `loguru` configurado com console + `bridge.log`
- [ ] Rotação e retenção aplicadas
- [ ] Níveis respeitados (DEBUG só com config)
- [ ] `enqueue=True` para thread-safety
- [ ] Formato ISO-8601 com timezone

### 4.10 Narração log
- [ ] `narration.log` formato conforme `SCHEMA.md` seção 7
- [ ] Tags `[STATE]`, `[WINDOW]`, `[HIGHLIGHTS]`, `[PREVIOUS]`, `[NARRATION]`, `[/NARRATION]` presentes
- [ ] Meta marcada (`LLM` ou `FALLBACK`)
- [ ] Linha em branco separadora entre entradas
- [ ] Append (não sobrescreve)

### 4.11 Testes Python
- [ ] `test_schemas.py` valida parse dos exemplos
- [ ] `test_io.py` testa escrita atômica, leitura tolerante, delete
- [ ] `test_prompt.py` valida formato do prompt gerado
- [ ] `test_fallback.py` valida escolha de template
- [ ] `test_loop.py` mock do LLMClient, executa N ciclos
- [ ] Cobertura ≥ 70% do código do bridge

---

## 5. Integração Mod + Python

### 5.1 Caminhos consistentes
- [ ] `bridge_path` no config do mod é idêntico a `bridge.path` no `narrator_config.toml`
- [ ] Ambos expandem `~` para o mesmo caminho real
- [ ] Diretório criado automaticamente por ambos

### 5.2 Fluxo ponta-a-ponta
- [ ] Com mod rodando e Python rodando, ao quebrar um bloco, em até 70s a narração aparece em `narration.log`
- [ ] `seq` do `state.json` avança corretamente (1, 2, 3, ...)
- [ ] `window.json` é deletado pelo Python após leitura
- [ ] `trigger.flag` é criado pelo Python e deletado pelo mod
- [ ] `last_narration.txt` é atualizado a cada ciclo
- [ ] `heartbeat.json` é atualizado a cada 5s pelo mod

### 5.3 Casos extremos
- [ ] Matar o Python → mod continua escrevendo JSONs (sobrescreve window após 60s)
- [ ] Reiniciar o Python → retoma do último seq (não reprocessa)
- [ ] Reiniciar o mod → Python detecta seq menor, reseta last_narration
- [ ] LLM timeout → após 3 retries, fallback de template é usado
- [ ] Mod pausado via `/narrator pause` → Python pula ciclos
- [ ] Disco cheio → mod loga erro mas não crasha
- [ ] `trigger.flag` já existe ao criar → Python loga warning, não duplica

### 5.4 Performance
- [ ] Tick do server não é afetado pelo mod (< 0.5ms por tick)
- [ ] Latência total (evento → narração) < 5s em condições normais
- [ ] Uso de memória do mod < 50 MB
- [ ] Uso de memória do Python < 100 MB

---

## 6. Documentação

- [ ] `README.md` (raiz do monorepo) explica como rodar
- [ ] `mod/README.md` explica como buildar e instalar o mod
- [ ] `bridge/README.md` explica como instalar e rodar o Python
- [ ] `.env.example` documentado (comentários em cada variável)
- [ ] `narrator_config.toml` documentado (comentários em cada campo)
- [ ] Comandos admin documentados (`/narrator pause`, etc.)

---

## 7. Empacotamento e Distribuição

### 7.1 Mod
- [ ] `./gradlew build` gera `.jar` em `build/libs/`
- [ ] `.jar` testado em Minecraft 1.21.1 limpo (sem outros mods)
- [ ] `.jar` testado com mods comuns (Sodium, Lithium) sem conflito
- [ ] `fabric.mod.json` com metadata correta (autor, licença, etc.)

### 7.2 Python
- [ ] `pip install .` instala sem erro
- [ ] `narrator-bridge` script funciona após install
- [ ] Dependências opcionais (`[openai]`, `[anthropic]`, etc.) funcionam
- [ ] Empacotamento como wheel testado (`python -m build`)

---

## 8. Checklist Final (antes de marcar tarefa como pronta)

- [ ] Todos os arquivos da spec lidos e entendidos
- [ ] Estrutura de diretórios conforme `README.md` seção 5
- [ ] Schemas JSON validados com `jq` em arquivos reais gerados
- [ ] `combat_window` presente em todos os `window.json` gerados
- [ ] Sistema sobrevive a 1h de uso contínuo sem crash
- [ ] `narration.log` tem pelo menos 20 entradas válidas após teste
- [ ] `bridge.log` não tem erros não tratados (apenas warnings/info)
- [ ] Latência end-to-end medida e documentada
- [ ] Custo LLM medido (tokens consumidos) e documentado

---

## 9. Comandos de Validação Rápida

### 9.1 Validar JSONs gerados
```bash
# state.json válido?
jq . ~/ai-narrator/state.json > /dev/null && echo "✅ state.json OK" || echo "❌ state.json inválido"

# window.json válido?
jq . ~/ai-narrator/window.json > /dev/null && echo "✅ window.json OK" || echo "❌ window.json inválido"

# seq coerente?
STATE_SEQ=$(jq .seq ~/ai-narrator/state.json)
WINDOW_SEQ=$(jq .seq ~/ai-narrator/window.json 2>/dev/null || echo "null")
if [ "$STATE_SEQ" = "$WINDOW_SEQ" ]; then
  echo "✅ seq coerente ($STATE_SEQ)"
else
  echo "❌ seq divergente: state=$STATE_SEQ window=$WINDOW_SEQ"
fi

# window_end > window_start?
DUR=$(jq '.window_end - .window_start' ~/ai-narrator/window.json 2>/dev/null)
if [ -n "$DUR" ] && [ "$DUR" -gt 0 ]; then
  echo "✅ duração da janela: ${DUR}ms"
else
  echo "❌ duração da janela inválida: $DUR"
fi

# combat_window presente?
jq -e '.activity.combat_window' ~/ai-narrator/window.json > /dev/null 2>&1 && \
  echo "✅ combat_window presente" || echo "❌ combat_window ausente"

# heartbeat atualizado?
LAST_SEEN=$(jq .last_seen ~/ai-narrator/heartbeat.json)
NOW_MS=$(date +%s%3N)
AGE_S=$(( (NOW_MS - LAST_SEEN) / 1000 ))
if [ "$AGE_S" -lt 30 ]; then
  echo "✅ heartbeat fresco (${AGE_S}s atrás)"
else
  echo "❌ heartbeat velho (${AGE_S}s atrás)"
fi
```

### 9.2 Validar prompt gerado
```bash
# Prompt tem system + user?
jq -e '.messages | length >= 2' ~/ai-narrator/prompt.json > /dev/null && \
  echo "✅ prompt tem ≥2 mensagens" || echo "❌ prompt incompleto"

# Tem role system?
jq -e '.messages[] | select(.role == "system")' ~/ai-narrator/prompt.json > /dev/null && \
  echo "✅ system message presente" || echo "❌ system message ausente"

# Tem role user?
jq -e '.messages[] | select(.role == "user")' ~/ai-narrator/prompt.json > /dev/null && \
  echo "✅ user message presente" || echo "❌ user message ausente"
```

### 9.3 Validar narração log
```bash
# Conta entradas (cabeçalhos ===)
ENTRIES=$(grep -c "^=== Ciclo" ~/ai-narrator/narration.log 2>/dev/null || echo 0)
echo "📝 $ENTRIES narrações no log"

# Conta fallbacks
FALLBACKS=$(grep -c "meta=FALLBACK" ~/ai-narrator/narration.log 2>/dev/null || echo 0)
echo "⚠️  $FALLBACKS narrações de fallback"

# Última narração
LAST=$(awk '/^\[NARRATION\]$/{flag=1;next}/^\[\/NARRATION\]$/{flag=0}flag' ~/ai-narrator/narration.log | tail -n 5)
echo "Última narração:"
echo "$LAST"
```

### 9.4 Validar latência
```bash
# Pega última entrada e calcula latência
LAST_T=$(jq -r '.timestamp' ~/ai-narrator/prompt.json)
NOW_MS=$(date +%s%3N)
LATENCY_S=$(( (NOW_MS - LAST_T) / 1000 ))
echo "⏱️  Último ciclo processado há ${LATENCY_S}s"

# Latência média (do bridge.log)
AVG_LATENCY=$(grep "concluído em" ~/ai-narrator/bridge.log | tail -20 | \
  grep -oP 'em \K\d+ms' | grep -oP '\d+' | \
  awk '{sum+=$1; count++} END {if(count>0) print int(sum/count)}')
echo "⏱️  Latência média dos últimos 20 ciclos: ${AVG_LATENCY}ms"
```

---

## 10. Critérios de Aceitação

A implementação está **pronta** quando TODOS os itens abaixo estão ✅:

1. ✅ Mod carrega sem erros em Minecraft 1.21.1 com Fabric.
2. ✅ Python bridge roda sem crash por 1h contínua.
3. ✅ `state.json` e `window.json` seguem o schema de `SCHEMA.md` (incluindo `combat_window`).
4. ✅ Fluxo de trigger → JSON → prompt → LLM → narração → trigger funciona ponta-a-ponta.
5. ✅ Latência end-to-end < 5s (evento → narração gravada).
6. ✅ LLM falha com graceful degradation (fallback de templates).
7. ✅ Mod morto detectado e Python pausa corretamente.
8. ✅ Player pode pausar via `/narrator pause`.
9. ✅ Logs (`bridge.log`, `narration.log`) são informativos e parseáveis.
10. ✅ Documentação completa e atualizada.

Se algum item estiver ❌, **NÃO** marcar a tarefa como pronta — corrigir primeiro.
