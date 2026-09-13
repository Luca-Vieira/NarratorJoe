# Schema de Arquivos

> Este documento é a **referência definitiva** de todos os arquivos transitados entre o mod Fabric e o bridge Python. Toda implementação deve produzir/consumir arquivos exatamente conforme descrito aqui.

> **Prioridade:** em caso de conflito entre este documento e qualquer outro, **este prevalece**.

---

## Índice

1. [`state.json`](#1-statejson) — estado persistente do jogador/mundo
2. [`window.json`](#2-windowjson) — eventos da janela atual
3. [`heartbeat.json`](#3-heartbeatjson) — sinal de vida do mod
4. [`trigger.flag`](#4-triggerflag) — sinal do Python para o mod
5. [`last_narration.txt`](#5-last_narrationtxt) — última narração gerada
6. [`prompt.json`](#6-promptjson) — prompt gerado (debug)
7. [`narration.log`](#7-narrationlog) — log append-only de narrações
8. [`bridge.log`](#8-bridgelog) — log do bridge Python
9. [`narrator_config.toml`](#9-narrator_configtoml) — config editável
10. [Convenções Gerais](#10-convenções-gerais)

---

## 1. `state.json`

### 1.1 Responsabilidade
Snapshot do estado atual do jogador e do mundo. **Sempre atualizado** a cada trigger (não é deletado). Contém dados persistentes (histórico de bioma, spawn) e snapshot atual (health, food, pos).

### 1.2 Schema completo

```json
{
  "seq": 42,
  "updated_at": 1788398627884,
  "player": {
    "name": "BusinessCapybara",
    "dimension": "minecraft:overworld",
    "pos": { "x": 119, "y": 81, "z": 644 },
    "health": 20.0,
    "food": 20,
    "day_time": 13400,
    "period": "noite",
    "weather": { "raining": false, "thundering": false },
    "biome": {
      "current":  {
        "id": "minecraft:savanna",
        "since_t": 1788398000000,
        "pos": { "x": 119, "y": 81, "z": 644 }
      },
      "previous": {
        "id": "minecraft:plains",
        "since_t": 1788397000000,
        "until_t": 1788398000000
      }
    },
    "spawn": {
      "current":  {
        "pos": { "x": 120, "y": 80, "z": 650 },
        "set_t": 1788397000000,
        "is_home": true,
        "dwell_ms": 650000
      },
      "previous": {
        "pos": { "x": 10, "y": 65, "z": 10 },
        "is_home": false
      }
    },
    "held_items_recent": [
      { "item": "minecraft:netherite_sword", "since_t": 1788398520000 },
      { "item": "minecraft:iron_ingot", "since_t": 1788398500000 }
    ],
    "entities_nearby": {
      "hostil": {},
      "passivo": { "minecraft:cow": 2 },
      "neutro": {},
      "players": 0
    },
    "paused": false
  }
}
```

### 1.3 Tabela de campos

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `seq` | `number` (int) | ✅ | Sequencial monotônico crescente (1, 2, 3, ...). Reinicia em 1 quando o mod recarrega. |
| `updated_at` | `number` (ms epoch) | ✅ | Timestamp em ms UTC da última escrita. |
| `player` | `object` | ✅ | Snapshot do jogador. |
| `player.name` | `string` | ✅ | Username do jogador. |
| `player.dimension` | `string` | ✅ | ID da dimensão, ex: `minecraft:overworld`, `minecraft:the_nether`, `minecraft:the_end`. |
| `player.pos` | `object {x, y, z}` | ✅ | Posição em blocos (inteiros). |
| `player.health` | `number` (float) | ✅ | Health atual (0–20, pode passar com buffs). |
| `player.food` | `number` (int) | ✅ | Food level (0–20). |
| `player.day_time` | `number` (int) | ✅ | `world.getTimeOfDay()` (0–24000). |
| `player.period` | `string` | ✅ | Um de: `"amanhecer"`, `"dia"`, `"entardecer"`, `"noite"`, `"madrugada"`. Derivado de `day_time`. |
| `player.weather` | `object {raining, thundering}` | ✅ | Clima atual. |
| `player.biome` | `object` | ✅ | Histórico de bioma. |
| `player.biome.current` | `BiomeEntry` | ✅ | Bioma atual. |
| `player.biome.previous` | `BiomeEntry \| null` | ✅ | Bioma anterior (null se nunca trocou). |
| `player.spawn` | `object` | ✅ | Histórico de spawn point. |
| `player.spawn.current` | `SpawnEntry` | ✅ | Spawn point atual. |
| `player.spawn.previous` | `SpawnEntry \| null` | ✅ | Spawn anterior. |
| `player.held_items_recent` | `array<HeldItem>` | ✅ | Últimos 5 itens segurados (mais recente primeiro). |
| `player.entities_nearby` | `object` | ✅ | Entidades no raio configurado (32 blocos default). |
| `player.entities_nearby.hostil` | `map<string, number>` | ✅ | Ex: `{"minecraft:zombie": 3}`. Vazio se nenhuma. |
| `player.entities_nearby.passivo` | `map<string, number>` | ✅ | Ex: `{"minecraft:cow": 2}`. |
| `player.entities_nearby.neutro` | `map<string, number>` | ✅ | Ex: `{"minecraft:enderman": 1}`. |
| `player.entities_nearby.players` | `number` (int) | ✅ | Outros players no raio (exclui o próprio). |
| `player.paused` | `boolean` | ✅ | True se o player pausou via `/narrator pause`. |

### 1.4 Sub-tipos

#### `BiomeEntry`
| Campo | Tipo | Descrição |
|---|---|---|
| `id` | `string` | ex: `"minecraft:savanna"`. |
| `since_t` | `number` (ms) | Quando entrou neste bioma. |
| `pos` | `{x, y, z}` | Posição quando entrou. |
| `until_t` | `number \| null` | Quando saiu. `null` no `current`. |

#### `SpawnEntry`
| Campo | Tipo | Descrição |
|---|---|---|
| `pos` | `{x, y, z}` | Posição do spawn point. |
| `set_t` | `number` (ms) | Quando o player setou esse spawn. |
| `is_home` | `boolean` | True se o player passou mais de X minutos perto (X default = 5 min). |
| `dwell_ms` | `number` | Tempo acumulado perto do spawn (raio configurável, default 32 blocos). |

#### `HeldItem`
| Campo | Tipo | Descrição |
|---|---|---|
| `item` | `string` | ex: `"minecraft:netherite_sword"`. |
| `since_t` | `number` (ms) | Quando o player passou a segurar. |

### 1.5 Invariantes
- `seq` é monotônico crescente dentro de uma sessão do mod.
- `updated_at` sempre avança (não pode ser menor que o anterior).
- `pos` é a posição no momento da escrita, não no início da janela.
- `held_items_recent[0]` é o item segurado **agora** (mais recente).
- Se `paused == true`, os outros campos podem estar desatualizados (o mod para de coletar).

---

## 2. `window.json`

### 2.1 Responsabilidade
Eventos acumulados durante a janela atual (desde o último trigger). **Deletado** pelo Python após leitura. O mod só escreve um novo quando recebe `trigger.flag`.

### 2.2 Schema completo (com `combat_window` adicionado — modificação solicitada)

```json
{
  "seq": 42,
  "window_start": 1788398567884,
  "window_end": 1788398627884,
  "activity": {
    "blocks_placed":   { "minecraft:sand": 12 },
    "blocks_broken":   { "minecraft:short_grass": 14 },
    "items_gained":    { "minecraft:iron_ingot": 5 },
    "items_lost":      { "minecraft:acacia_planks": 264 },
    "mobs_killed":     { "minecraft:pig": 1, "minecraft:villager": 4 },
    "containers_opened": { "minecraft:chest": 5 },
    "stations_used":   { "minecraft:furnace": 1, "minecraft:brewing_stand": 3 },
    "combat_window": {
      "damage_dealt":    { "minecraft:zombie": 18.5, "minecraft:pig": 6.0 },
      "damage_taken":    { "fall": 4.0, "minecraft:zombie": 8.0 },
      "kills": [
        { "entity": "minecraft:zombie", "t": 1788398583000, "weapon": "minecraft:netherite_sword", "damage": 18.5 },
        { "entity": "minecraft:pig", "t": 1788398590000, "weapon": "minecraft:netherite_sword", "damage": 6.0 }
      ],
      "deaths_nearby": [
        { "entity": "minecraft:villager", "cause": "mob", "t": 1788398595000, "pos": {"x": 125, "y": 80, "z": 640}, "distance": 7.2 }
      ],
      "player_death": null
    }
  },
  "highlights": [
    { "type": "advancement", "text": "Acquire Hardware", "t": 1788398583816 },
    { "type": "effect_gained", "effect": "minecraft:poison", "amplifier": 1, "t": 1788398583000 },
    { "type": "food_low", "food": 4, "t": 1788398590000 }
  ]
}
```

### 2.3 Tabela de campos principais

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `seq` | `number` (int) | ✅ | Mesmo `seq` do `state.json` do mesmo ciclo. |
| `window_start` | `number` (ms) | ✅ | Início da janela (quando o mod recebeu o trigger anterior). |
| `window_end` | `number` (ms) | ✅ | Fim da janela (quando o mod escreveu este JSON). |
| `activity` | `object` | ✅ | Agregados de atividade. |
| `activity.blocks_placed` | `map<string, number>` | ✅ | Blocos colocados pelo player. |
| `activity.blocks_broken` | `map<string, number>` | ✅ | Blocos quebrados. |
| `activity.items_gained` | `map<string, number>` | ✅ | Itens coletados do chão (não fabricados). |
| `activity.items_lost` | `map<string, number>` | ✅ | Itens descartados ou consumidos. |
| `activity.mobs_killed` | `map<string, number>` | ✅ | Entidades mortas pelo player (exclui suicídio). |
| `activity.containers_opened` | `map<string, number>` | ✅ | Containers abertos (chest, barrel, shulker_box, etc.). |
| `activity.stations_used` | `map<string, number>` | ✅ | Estações usadas (furnace, brewing_stand, etc.). |
| `activity.combat_window` | `object` | ✅ | **(NOVO)** Detalhes de combate da janela. |
| `highlights` | `array<Highlight>` | ✅ | Eventos notáveis (não agregados). Pode ser vazio. |

### 2.4 Sub-schema `combat_window` (adição solicitada pelo usuário)

| Campo | Tipo | Default | Descrição |
|---|---|---|---|
| `damage_dealt` | `map<string, number>` | `{}` | Dano total causado pelo player, agrupado por **entity id** (ex: `{"minecraft:zombie": 18.5}`). |
| `damage_taken` | `map<string, number>` | `{}` | Dano total recebido pelo player, agrupado por **source** (ex: `{"fall": 4.0, "minecraft:zombie": 8.0}`). |
| `kills` | `array<KillEntry>` | `[]` | Lista de abates do player, ordenada por `t` crescente. |
| `deaths_nearby` | `array<DeathEntry>` | `[]` | Mortes de outras entidades (não causadas pelo player) num raio de 32 blocos. |
| `player_death` | `PlayerDeath \| null` | `null` | Detalhes da morte do player, se ocorreu na janela. |

#### `KillEntry`
| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `entity` | `string` | ✅ | ex: `"minecraft:zombie"`. |
| `t` | `number` (ms) | ✅ | Quando ocorreu o abate. |
| `weapon` | `string \| null` | ✅ | Item na mão principal do player no momento (ex: `"minecraft:netherite_sword"`). `null` se mãos vazias. |
| `damage` | `number \| null` | ✅ | Dano total causado a esta entidade antes da morte (soma de todos os hits). |

#### `DeathEntry` (mortes próximas)
| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `entity` | `string \| null` | ✅ | ID da entidade que morreu. `null` se não era uma entidade (ex: item frame). |
| `cause` | `string` | ✅ | Tipo de causa: `"mob"`, `"fall"`, `"fire"`, `"lava"`, `"drown"`, `"player"`, `"other"`. |
| `t` | `number` (ms) | ✅ | Quando ocorreu. |
| `pos` | `{x, y, z} \| null` | ✅ | Posição da morte. |
| `distance` | `number \| null` | ✅ | Distância do player até a morte (blocos). |

#### `PlayerDeath`
| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `cause` | `string` | ✅ | Causa: `"mob"`, `"fall"`, `"fire"`, `"lava"`, `"drown"`, `"void"`, `"player"`, `"other"`. |
| `t` | `number` (ms) | ✅ | Quando morreu. |
| `pos` | `{x, y, z}` | ✅ | Onde morreu. |
| `killer_entity` | `string \| null` | ⚠️ Opcional | Se `cause == "mob"`, qual mob matou. |
| `killer_player` | `string \| null` | ⚠️ Opcional | Se `cause == "player"`, nome do player assassino. |

### 2.5 Sub-schema `Highlight`

Cada highlight tem um `type` e campos opcionais dependendo do tipo:

#### Tipos suportados

| `type` | Campos extras | Descrição |
|---|---|---|
| `advancement` | `text: string` | Conquista desbloqueada. |
| `effect_gained` | `effect: string`, `amplifier: number` | Efeito de status ganho. |
| `effect_lost` | `effect: string` | Efeito de status perdido. |
| `food_low` | `food: number` | Food caiu abaixo do limiar (default 5). |
| `health_low` | `health: number` | Health caiu abaixo do limiar (default 6). |
| `biome_change` | `from_biome: string`, `to_biome: string` | Entrou em bioma novo. |
| `dimension_change` | `from_dim: string`, `to_dim: string` | Trocou de dimensão. |
| `weather_change` | `weather_event: string` | `"rain_start"`, `"rain_stop"`, `"thunder_start"`, `"thunder_stop"`. |
| `player_death` | `text: string` (resumo da causa) | Player morreu. |
| `level_up` | `new_level: number` | (Opcional, futuro) Subiu de nível. |

#### Exemplos
```json
{ "type": "advancement", "text": "Acquire Hardware", "t": 1788398583816 }
{ "type": "effect_gained", "effect": "minecraft:poison", "amplifier": 1, "t": 1788398583000 }
{ "type": "food_low", "food": 4, "t": 1788398590000 }
{ "type": "biome_change", "from_biome": "minecraft:plains", "to_biome": "minecraft:savanna", "t": 1788398580000 }
{ "type": "dimension_change", "from_dim": "minecraft:overworld", "to_dim": "minecraft:the_nether", "t": 1788398600000 }
{ "type": "weather_change", "weather_event": "rain_start", "t": 1788398610000 }
{ "type": "player_death", "text": "morto por zombie", "t": 1788398620000 }
```

### 2.6 Invariantes
- `window_end > window_start` sempre.
- `seq` deve bater com o `seq` do `state.json` escrito no mesmo ciclo.
- Todos os maps (`blocks_placed`, etc.) podem ser vazios `{}` se nenhuma atividade.
- `combat_window` é **sempre** presente (mesmo que vazio), para evitar `null` checks no Python.
- `highlights` é ordenado por `t` crescente.
- `kills` é ordenado por `t` crescente.

---

## 3. `heartbeat.json`

### 3.1 Schema
```json
{
  "last_seen": 1788398627884,
  "tps": 19.8,
  "player_count": 1,
  "mod_version": "0.1.0",
  "seq": 42
}
```

### 3.2 Campos

| Campo | Tipo | Descrição |
|---|---|---|
| `last_seen` | `number` (ms) | Timestamp da última escrita. |
| `tps` | `number` (float) | TPS atual do server (20 = ideal). |
| `player_count` | `number` (int) | Players conectados. |
| `mod_version` | `string` | Versão do mod. |
| `seq` | `number` (int) | Último seq escrito. |

### 3.3 Frequência
- Escrito a cada **5 segundos** (configurável em `heartbeat_interval_ms`).
- Python considera o mod "morto" se `last_seen` > 30s atrás.

---

## 4. `trigger.flag`

### 4.1 Schema
**Arquivo vazio.** Apenas a existência importa.

### 4.2 Convenções
- Criado pelo Python após salvar a narração.
- Deletado pelo mod assim que detectado (no próximo poll de 1s).
- Se já existe quando o Python tenta criar, logar warning (mod está lento).
- Conteúdo: **vazio** (0 bytes). Não escrever nada dentro.

---

## 5. `last_narration.txt`

### 5.1 Schema
Texto puro (UTF-8), uma narração por arquivo (sobrescreve a cada ciclo).

### 5.2 Convenções
- Escrito atomicamente pelo Python após cada narração.
- Lido pelo Python no início do próximo ciclo para incluir como `previous narration` no prompt.
- Tamanho típico: 200–500 caracteres.
- Pode conter quebras de linha (`\n`) se a narração tiver múltiplos parágrafos.
- No encoding: UTF-8 sem BOM.

### 5.3 Exemplo
```
O capivara avança pela savana sob a lua minguante, suas mãos calejadas já familiarizadas com o peso do ferro recém-adquirido. Ao redor, o capim se rende à sua passagem, e um porco selvagem cai sob sua lâmina — jantar garantido para a noite que se avizinha.
```

---

## 6. `prompt.json`

### 6.1 Schema
```json
{
  "seq": 42,
  "timestamp": 1788398627884,
  "messages": [
    { "role": "system", "content": "Você é um narrador..." },
    { "role": "user", "content": "Estado atual:\n..." }
  ],
  "provider": "openai",
  "model": "gpt-4o-mini"
}
```

### 6.2 Propósito
Apenas para **debug**. Permite re-rodar um ciclo manualmente com:
```bash
cat ~/ai-narrator/prompt.json | jq -r '.messages' | python -m narrator_bridge.replay
```

### 6.3 Convenções
- Sobrescrito a cada ciclo (não append).
- Não é lido por nenhum processo em runtime.

---

## 7. `narration.log`

### 7.1 Formato
Append-only. Cada entrada tem cabeçalho + narração. Separadas por linha em branco.

### 7.2 Exemplo de entrada

```
=== Ciclo #42 | 2024-01-15T14:23:00.123Z | seq=42 | provider=openai | model=gpt-4o-mini | meta=LLM ===
[STATE] BusinessCapybara em minecraft:overworld, noite, health=20.0/20, biome=minecraft:savanna
[WINDOW] 60000ms: quebrou 14 short_grass, ganhou 5 iron_ingot, matou 1 pig, 4 villager, abriu 5 chest, usou 1 furnace, 3 brewing_stand, causou 18.5 zombie, 6 pig de dano, recebeu 4 fall, 8 zombie de dano, 2 abates
[HIGHLIGHTS] advancement:Acquire Hardware | effect:minecraft:poison(amp=1) | food_low:4
[PREVIOUS] "...o capivara encontra ferro pela primeira vez..."
[NARRATION]
O capivara avança pela savana sob a lua minguante, suas mãos calejadas já familiarizadas com o peso do ferro recém-adquirido. Ao redor, o capim se rende à sua passagem, e um porco selvagem cai sob sua lâmina — jantar garantido para a noite que se avizinha.
[/NARRATION]

```

### 7.3 Convenções
- Encoding UTF-8.
- Cada entrada termina com `\n\n` (linha em branco separadora).
- Tags: `===` (header), `[STATE]`, `[WINDOW]`, `[HIGHLIGHTS]`, `[PREVIOUS]`, `[NARRATION]`, `[/NARRATION]`.
- `meta` é `LLM` ou `FALLBACK`.
- Rotação: **não rotacionar** (manter histórico completo). Se ficar muito grande, o usuário move manualmente.

---

## 8. `bridge.log`

### 8.1 Formato
Append-only com rotação (default: 10 MB) e retenção (default: 30 dias).

### 8.2 Exemplo de linhas
```
2024-01-15T14:23:00.123Z | INFO     | narrator_bridge.loop | Ciclo #42 iniciado
2024-01-15T14:23:00.124Z | DEBUG    | narrator_bridge.io | Lendo state.json (3.2KB)
2024-01-15T14:23:00.125Z | DEBUG    | narrator_bridge.io | Lendo window.json (1.8KB)
2024-01-15T14:23:00.126Z | DEBUG    | narrator_bridge.io | Lendo last_narration.txt (245B)
2024-01-15T14:23:00.127Z | DEBUG    | narrator_bridge.io | Deletando window.json
2024-01-15T14:23:00.130Z | INFO     | narrator_bridge.prompt | Prompt montado (1.4KB, 312 tokens)
2024-01-15T14:23:00.131Z | INFO     | narrator_bridge.llm | Chamando provider=openai model=gpt-4o-mini
2024-01-15T14:23:02.245Z | INFO     | narrator_bridge.llm | Resposta em 2114ms (87 tokens)
2024-01-15T14:23:02.246Z | INFO     | narrator_bridge.loop | Ciclo #42 concluído em 2123ms
```

---

## 9. `narrator_config.toml`

> Já detalhado em `PYTHON_BRIDGE_SPEC.md` seção 3.2. Repetido aqui para centralidade.

```toml
[bridge]
path = "~/ai-narrator"
cycle_interval_s = 60
poll_interval_s = 1
mod_dead_timeout_s = 30

[llm]
provider = "openai"           # openai | anthropic | gemini | ollama
model = "gpt-4o-mini"
temperature = 0.7
max_tokens = 300
timeout_s = 30
max_retries = 3
retry_base_delay_s = 1.0
retry_max_delay_s = 8.0
enable_fallback = true

[prompt]
system_prompt_file = ""
language = "pt-BR"
style = "storyteller"         # documentary | storyteller | playful | epic
max_chars = 400

[logging]
level = "INFO"                # DEBUG | INFO | WARNING | ERROR
rotation = "10 MB"
retention = "30 days"
ring_buffer_size = 500
```

### 9.1 Config do mod (paralelo)
O mod tem seu próprio config em `.minecraft/config/ai-narrator.json`:
```json
{
  "bridge_path": "~/ai-narrator",
  "heartbeat_interval_ms": 5000,
  "trigger_poll_ms": 1000,
  "max_ring_buffer_events": 500,
  "entities_scan_radius": 32,
  "entities_scan_interval_ticks": 20,
  "home_radius": 32,
  "home_dwell_threshold_ms": 300000,
  "food_low_threshold": 5,
  "health_low_threshold": 6,
  "log_level": "INFO"
}
```

> **Crítico:** o `bridge_path` deve ser **idêntico** em ambos os configs. Se diferente, mod e Python não se encontram.

---

## 10. Convenções Gerais

### 10.1 Encoding
- **Todos os arquivos de texto:** UTF-8 sem BOM.
- **JSONs:** indentação de 2 espaços, sem trailing newline extra (apenas `\n` no final).
- **Logs:** UTF-8, uma linha por evento (exceto `narration.log` que é multi-linha).

### 10.2 Timestamps
- **Sempre** em milissegundos desde epoch Unix UTC (number, não string).
- Campo padrão: `t` para eventos pontuais, `updated_at` / `window_start` / `window_end` / `last_seen` / `set_t` / `since_t` / `until_t` para intervalos/pontos.
- Range válido: 0 a `2^53 - 1` (suportado por JSON).

### 10.3 IDs de entidades/blocos/itens
- **Sempre** com namespace completo: `minecraft:stone`, `minecraft:zombie`, `minecraft:netherite_sword`.
- **Nunca** traduzir (não usar `"pedra"` no lugar de `"minecraft:stone"`).
- Se vier de outro mod (não-vanilla), manter o namespace original (`modid:item`).

### 10.4 Números
- **Health/damage:** float com 1 casa decimal (ex: `18.5`).
- **Posições:** inteiros (blocos, não pixels).
- **Contagens (kills, blocos, etc.):** inteiros positivos.
- **Durations:** em ms (int).

### 10.5 Booleanos
- Sempre `true` ou `false` (minúsculo), nunca `1`/`0` ou `yes`/`no`.

### 10.6 Nulls
- Usar `null` explicitamente quando um campo opcional está ausente.
- **Não omitir campos obrigatórios** — se não houver valor, use `null` (para objects) ou `{}`/`[]` (para maps/arrays).

### 10.7 Ordem de campos
- Manter a ordem definida nos exemplos deste documento (importante para diff e revisão humana).
- Em Java: usar `GsonBuilder().setPrettyPrinting()` que preserva ordem de inserção do `JsonObject`.
- Em Python: pydantic v2 preserva ordem de declaração dos campos.

### 10.8 Tamanho máximo
- `state.json`: ~3–5 KB.
- `window.json`: ~2–4 KB (até 10 KB se ciclo muito ativo).
- `heartbeat.json`: ~150 bytes.
- `prompt.json`: ~2–4 KB.
- `last_narration.txt`: ~500 bytes (max 2 KB).
- `narration.log`: cresce indefinidamente (sem rotação).

### 10.9 Escrita atômica
- **Todos** os arquivos (exceto logs append-only) devem ser escritos atomicamente: temp + fsync + rename.
- Em Java: `Files.move(tmp, target, REPLACE_EXISTING, ATOMIC_MOVE)`.
- Em Python: `os.replace(tmp, target)`.
- Logs (`narration.log`, `bridge.log`) usam append simples (não atômico, mas aceitável).

### 10.10 Detecção de arquivos novos
- O Python detecta novos ciclos comparando `seq` do `state.json` (não por timestamp do arquivo).
- O mod detecta trigger pela **existência** do arquivo `trigger.flag` (não por conteúdo).

---

## 11. Apêndice: Exemplos Completos

### 11.1 `state.json` (cenário de exploração noturna)
```json
{
  "seq": 17,
  "updated_at": 1788398627884,
  "player": {
    "name": "BusinessCapybara",
    "dimension": "minecraft:overworld",
    "pos": { "x": 119, "y": 81, "z": 644 },
    "health": 16.0,
    "food": 14,
    "day_time": 18400,
    "period": "noite",
    "weather": { "raining": false, "thundering": false },
    "biome": {
      "current": { "id": "minecraft:savanna", "since_t": 1788398000000, "pos": { "x": 119, "y": 81, "z": 644 } },
      "previous": { "id": "minecraft:plains", "since_t": 1788397000000, "until_t": 1788398000000 }
    },
    "spawn": {
      "current": { "pos": { "x": 120, "y": 80, "z": 650 }, "set_t": 1788397000000, "is_home": true, "dwell_ms": 650000 },
      "previous": { "pos": { "x": 10, "y": 65, "z": 10 }, "is_home": false }
    },
    "held_items_recent": [
      { "item": "minecraft:netherite_sword", "since_t": 1788398520000 },
      { "item": "minecraft:torch", "since_t": 1788398500000 },
      { "item": "minecraft:cooked_beef", "since_t": 1788398480000 }
    ],
    "entities_nearby": {
      "hostil": { "minecraft:zombie": 2, "minecraft:skeleton": 1 },
      "passivo": {},
      "neutro": {},
      "players": 0
    },
    "paused": false
  }
}
```

### 11.2 `window.json` (ciclo com combate e morte)
```json
{
  "seq": 17,
  "window_start": 1788398567884,
  "window_end": 1788398627884,
  "activity": {
    "blocks_placed":   { "minecraft:torch": 4 },
    "blocks_broken":   {},
    "items_gained":    {},
    "items_lost":      { "minecraft:cooked_beef": 1, "minecraft:torch": 4 },
    "mobs_killed":     { "minecraft:zombie": 2 },
    "containers_opened": {},
    "stations_used":   {},
    "combat_window": {
      "damage_dealt":    { "minecraft:zombie": 36.5, "minecraft:skeleton": 12.0 },
      "damage_taken":    { "minecraft:zombie": 6.0, "minecraft:skeleton": 4.0, "fall": 2.0 },
      "kills": [
        { "entity": "minecraft:zombie", "t": 1788398575000, "weapon": "minecraft:netherite_sword", "damage": 18.5 },
        { "entity": "minecraft:zombie", "t": 1788398600000, "weapon": "minecraft:netherite_sword", "damage": 18.0 },
        { "entity": "minecraft:skeleton", "t": 1788398615000, "weapon": "minecraft:netherite_sword", "damage": 12.0 }
      ],
      "deaths_nearby": [],
      "player_death": null
    }
  },
  "highlights": [
    { "type": "food_low", "food": 4, "t": 1788398585000 },
    { "type": "health_low", "health": 5.0, "t": 1788398605000 }
  ]
}
```

### 11.3 `window.json` (ciclo com morte do player)
```json
{
  "seq": 23,
  "window_start": 1788398667884,
  "window_end": 1788398727884,
  "activity": {
    "blocks_placed":   {},
    "blocks_broken":   {},
    "items_gained":    {},
    "items_lost":      { "minecraft:netherite_sword": 1, "minecraft:netherite_helmet": 1 },
    "mobs_killed":     {},
    "containers_opened": {},
    "stations_used":   {},
    "combat_window": {
      "damage_dealt":    { "minecraft:creeper": 8.0 },
      "damage_taken":    { "minecraft:creeper": 12.0, "fall": 8.0 },
      "kills": [],
      "deaths_nearby": [],
      "player_death": {
        "cause": "mob",
        "killer_entity": "minecraft:creeper",
        "t": 1788398700000,
        "pos": { "x": 119, "y": 81, "z": 644 }
      }
    }
  },
  "highlights": [
    { "type": "health_low", "health": 3.0, "t": 1788398698000 },
    { "type": "player_death", "text": "morto por creeper", "t": 1788398700000 }
  ]
}
```

### 11.4 `window.json` (ciclo idle/AFK)
```json
{
  "seq": 8,
  "window_start": 1788398667884,
  "window_end": 1788398727884,
  "activity": {
    "blocks_placed":   {},
    "blocks_broken":   {},
    "items_gained":    {},
    "items_lost":      {},
    "mobs_killed":     {},
    "containers_opened": {},
    "stations_used":   {},
    "combat_window": {
      "damage_dealt": {},
      "damage_taken": {},
      "kills": [],
      "deaths_nearby": [],
      "player_death": null
    }
  },
  "highlights": []
}
```

---

## 12. Sugestões de Extensões Futuras (não implementar agora)

> Estas modificações não foram aprovadas pelo usuário, mas estão documentadas como ideias para o futuro. **Não implementar sem confirmação.**

- **`inventory_snapshot`** em `state.json`: lista de itens no inventário (top 20 por quantidade) para contexto de riqueza.
- **`chat_events`** em `window.json`: mensagens de outros players, comandos executados, trades.
- **`build_context`** em `window.json`: área construída aproximada (bounding box), tipo de estrutura detectada (casa, fazenda, etc.).
- **`current_goal`** em `state.json`: advancement em andamento inferido pelo mod (ex: "smelt_iron" se o player está coletando ferro).
- **`mood_hint`** em `state.json`: enum ("tensao", "descoberta", "conforto", "tedio") para o LLM calibrar o tom.

Cada uma dessas extensões pode ser adicionada sem quebrar compatibilidade, pois o Python usa pydantic com defaults flexíveis.

---

## 13. Validação

Para validar que os JSONs gerados batem com este schema:

```bash
# Validar state.json
cat ~/ai-narrator/state.json | jq .

# Validar window.json
cat ~/ai-narrator/window.json | jq .

# Comparar seq entre state e window
diff <(jq .seq ~/ai-narrator/state.json) <(jq .seq ~/ai-narrator/window.json)

# Verificar timestamps coerentes
jq '.window_end - .window_start' ~/ai-narrator/window.json  # deve ser ~60000 (60s)
```

Para validação estrita com pydantic (no Python):
```python
from narrator_bridge.schemas import State, Window
import json

state = State.model_validate_json(open("~/ai-narrator/state.json").read())
window = Window.model_validate_json(open("~/ai-narrator/window.json").read())

assert state.seq == window.seq
assert window.window_end > window.window_start
print("OK")
```
