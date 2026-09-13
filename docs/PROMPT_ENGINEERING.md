# Prompt Engineering

> Este documento descreve como o bridge Python monta o prompt enviado ao LLM para gerar a narração. **Toda implementação do `PromptBuilder` deve seguir estas diretrizes.**

---

## 1. Objetivo da Narração

O narrador deve produzir **uma narração curta (200–400 caracteres)** que:

1. **Acompanhe** o que o jogador está fazendo no momento (sem ser repetitiva).
2. **Conecte-se** à narração anterior (continuidade narrativa).
3. **Adapte o tom** ao contexto (combate = tenso, exploração = curioso, idle = contemplativo).
4. **Seja imersiva** — segunda ou terceira pessoa, voz literária, sem mencoes a "sistema", "AI", "mod".
5. **Use o nome do jogador** (ex: "BusinessCapybara") ou um apelido natural (ex: "o capivara").
6. **Nunca** dê dicas de gameplay, conselhos, ou mencione a existência da narração.

---

## 2. Persona do Narrador

### 2.1 Estilos suportados (configurável em `narrator_config.toml`)

| Estilo | Voz | Exemplo |
|---|---|---|
| `documentary` | Voz de documentário, distante, factual. | "O capivara atravessou a savana, coletando ferro em seu trajeto." |
| `storyteller` | Narrador literário, evocativo, terceira pessoa. (default) | "Sob a lua minguante, o capivara avança pela savana, o peso do ferro ainda familiar em suas mãos." |
| `playful` | Leve, bem-humorado, com traços de ironia. | "Lá vai o capivara de novo, espada em riste, atrás de mais um porco inocente para o jantar." |
| `epic` | Tom grandioso, fantasia heroica. | "Com o aço recém-forjado em punho, o capivara marchou contra a noite que avançava, e o porco caiu — primeiro de muitos." |

### 2.2 Regras de voz
- **Sempre** em português brasileiro.
- **Sempre** presente do indicativo ou pretérito perfeito (não usar futuro).
- **Nunca** usar segunda pessoa ("você fez isso") no estilo padrão. Usar terceira pessoa ("o capivara fez").
- **Nunca** usar gírias regionais excessivas (mantém acessível).
- **Nunca** quebrar a quarta parede (não mencionar o jogo, o mod, a IA, etc.).

---

## 3. System Prompt (template base)

### 3.1 Template padrão (`storyteller`)

```
Você é um narrador literário que acompanha as aventuras de um jogador de Minecraft em tempo real. Sua voz é evocativa, contemplativa, em terceira pessoa, no estilo de um romance de fantasia picaresco.

REGRAS:
- Escreva em português brasileiro culto, com fluxo literário natural.
- Use entre 200 e 400 caracteres por narração (uma a três frases).
- Conecte-se à narração anterior quando fizer sentido, sem repeti-la.
- Adapte o tom ao contexto: combate é tenso, exploração é curioso, descanso é contemplativo, morte é solene.
- Refira-se ao jogador pelo nome "{player_name}" ou por apelidos naturais derivados (ex: "{player_alias}").
- NUNCA mencione: "sistema", "AI", "mod", "script", "JSON", "código", "prompt", "tokens", "API".
- NUNCA dê dicas de gameplay, conselhos ou instruções.
- NUNCA use segunda pessoa ("você"). Sempre terceira pessoa.
- NUNCA mencione números concretos (ex: "destruiu 14 blocos") — use linguagem qualitativa ("destruiu alguns blocos", "uma dúzia de blocos").
- Varie o vocabulário entre narrações para evitar repetição.

CONTEXTO DA NARRAÇÃO ANTERIOR:
{previous_narration}

ESTILO: {style_description}
```

### 3.2 Variáveis do template

| Variável | Origem | Exemplo |
|---|---|---|
| `{player_name}` | `state.player.name` | `"BusinessCapybara"` |
| `{player_alias}` | Derivado do nome (split por camelCase, pegar última parte lowercase) | `"capivara"` |
| `{previous_narration}` | `last_narration.txt` (ou string vazia se primeira) | `"O capivara encontra ferro..."` |
| `{style_description}` | Mapeamento do `style` config | `"literário, evocativo, terceira pessoa, romance picaresco"` |

### 3.3 Mapeamento de `style` para descrição

```python
STYLE_DESCRIPTIONS = {
    "documentary": "documental, distante, factual, voz neutra de narrador de documentário",
    "storyteller": "literário, evocativo, terceira pessoa, romance picaresco",
    "playful":     "leve, bem-humorado, irônico, com toques de cumplicidade",
    "epic":        "grandioso, heroico, fantasia épica, com cadência de saga",
}
```

---

## 4. User Message — Formato do Contexto

### 4.1 Estrutura

A user message é montada a partir do `state.json` e `window.json`, formatada como texto estruturado legível:

```
=== ESTADO ATUAL ===
Jogador: BusinessCapybara
Dimensão: minecraft:overworld
Bioma: minecraft:savanna (desde há 10 minutos; antes: minecraft:plains)
Posição: (119, 81, 644)
Saúde: 16.0/20 | Comida: 14/20
Horário: noite (day_time=18400)
Clima: sem chuva
Spawn: casa principal (pos=(120,80,650), há 11 minutos)
Item em mãos: minecraft:netherite_sword (há 2 minutos)
Entidades próximas: 2 minecraft:zombie, 1 minecraft:skeleton (hostis)

=== JANELA (últimos 60s) ===
Atividade:
- Blocos quebrados: 14 minecraft:short_grass
- Blocos colocados: 4 minecraft:torch
- Itens ganhos: 5 minecraft:iron_ingot
- Itens perdidos: 1 minecraft:cooked_beef, 4 minecraft:torch
- Mobs mortos: 2 minecraft:zombie
- Containers abertos: 5 minecraft:chest
- Estações usadas: 1 minecraft:furnace

Combate:
- Dano causado: 36.5 em minecraft:zombie, 12.0 em minecraft:skeleton
- Dano recebido: 6.0 (minecraft:zombie), 4.0 (minecraft:skeleton), 2.0 (fall)
- Abates: 2 minecraft:zombie (com minecraft:netherite_sword), 1 minecraft:skeleton (com minecraft:netherite_sword)
- Mortes próximas: nenhuma
- Morte do jogador: não

Destaques:
- advancement: Acquire Hardware
- effect_gained: minecraft:poison (amplifier 1)
- food_low: comida em 4

=== NARRAÇÃO ANTERIOR ===
"O capivara encontra ferro pela primeira vez, e seus olhos brilham com a promessa de um futuro mais resistente."

=== INSTRUÇÃO ===
Escreva a próxima narração (200–400 caracteres) que continue a história acompanhando os eventos desta janela.
```

### 4.2 Implementação do `PromptBuilder`

```python
from .schemas import State, Window
from .config import PromptConfig

STYLE_DESCRIPTIONS = {
    "documentary": "documental, distante, factual, voz neutra de narrador de documentário",
    "storyteller": "literário, evocativo, terceira pessoa, romance picaresco",
    "playful":     "leve, bem-humorado, irônico, com toques de cumplicidade",
    "epic":        "grandioso, heroico, fantasia épica, com cadência de saga",
}

class PromptBuilder:
    def __init__(self, config: PromptConfig):
        self.config = config
        self.system_prompt_template = self._load_system_prompt()

    def build(self, state: State, window: Window, last_narration: str) -> list[dict]:
        system = self._render_system(state, last_narration)
        user = self._render_user(state, window, last_narration)
        return [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ]

    def _load_system_prompt(self) -> str:
        if self.config.system_prompt_file:
            from pathlib import Path
            return Path(self.config.system_prompt_file).read_text(encoding="utf-8")
        return self._default_system_prompt()

    def _default_system_prompt(self) -> str:
        return """Você é um narrador literário que acompanha as aventuras de um jogador de Minecraft em tempo real. Sua voz é evocativa, contemplativa, em terceira pessoa, no estilo de um romance de fantasia picaresco.

REGRAS:
- Escreva em português brasileiro culto, com fluxo literário natural.
- Use entre {min_chars} e {max_chars} caracteres por narração (uma a três frases).
- Conecte-se à narração anterior quando fizer sentido, sem repeti-la.
- Adapte o tom ao contexto: combate é tenso, exploração é curioso, descanso é contemplativo, morte é solene.
- Refira-se ao jogador pelo nome "{player_name}" ou por apelidos naturais derivados.
- NUNCA mencione: "sistema", "AI", "mod", "script", "JSON", "código", "prompt", "tokens", "API".
- NUNCA dê dicas de gameplay, conselhos ou instruções.
- NUNCA use segunda pessoa ("você"). Sempre terceira pessoa.
- NUNCA mencione números concretos — use linguagem qualitativa.
- Varie o vocabulário entre narrações para evitar repetição.

ESTILO: {style_description}"""

    def _render_system(self, state: State, last_narration: str) -> str:
        player_name = state.player.name
        player_alias = self._derive_alias(player_name)
        style_desc = STYLE_DESCRIPTIONS.get(self.config.style, STYLE_DESCRIPTIONS["storyteller"])
        min_chars = max(100, self.config.max_chars // 3)
        max_chars = self.config.max_chars

        prompt = self.system_prompt_template.format(
            player_name=player_name,
            player_alias=player_alias,
            style_description=style_desc,
            min_chars=min_chars,
            max_chars=max_chars,
        )

        if last_narration:
            prompt += f"\n\nCONTEXTO DA NARRAÇÃO ANTERIOR:\n\"{last_narration}\""

        return prompt

    def _derive_alias(self, name: str) -> str:
        # BusinessCapybara -> capivara
        import re
        parts = re.findall(r"[A-Z][a-z]+|[a-z]+", name)
        if not parts:
            return name.lower()
        return parts[-1].lower()

    def _render_user(self, state: State, window: Window, last_narration: str) -> str:
        lines = []
        lines.append("=== ESTADO ATUAL ===")
        lines.append(f"Jogador: {state.player.name}")
        lines.append(f"Dimensão: {state.player.dimension}")
        lines.append(self._fmt_biome(state))
        lines.append(f"Posição: ({state.player.pos['x']}, {state.player.pos['y']}, {state.player.pos['z']})")
        lines.append(f"Saúde: {state.player.health:.1f}/20 | Comida: {state.player.food}/20")
        lines.append(f"Horário: {state.player.period} (day_time={state.player.day_time})")
        lines.append(f"Clima: {'com chuva' if state.player.weather.raining else 'sem chuva'}"
                     + (", com trovoada" if state.player.weather.thundering else ""))
        lines.append(self._fmt_spawn(state))
        lines.append(self._fmt_held_item(state))
        lines.append(self._fmt_entities(state))
        lines.append("")

        lines.append(f"=== JANELA (últimos {(window.window_end - window.window_start)//1000}s) ===")
        lines.append("Atividade:")
        lines.extend(self._fmt_activity(window))
        lines.append("")
        lines.append("Combate:")
        lines.extend(self._fmt_combat(window))
        lines.append("")

        lines.append("Destaques:")
        if window.highlights:
            for h in window.highlights:
                lines.append(f"- {self._fmt_highlight(h)}")
        else:
            lines.append("- (nenhum destaque na janela)")
        lines.append("")

        if last_narration:
            lines.append("=== NARRAÇÃO ANTERIOR ===")
            lines.append(f"\"{last_narration}\"")
            lines.append("")

        lines.append("=== INSTRUÇÃO ===")
        lines.append(f"Escreva a próxima narração ({self.config.max_chars // 3}–{self.config.max_chars} caracteres) que continue a história acompanhando os eventos desta janela.")
        return "\n".join(lines)

    def _fmt_biome(self, state: State) -> str:
        b = state.player.biome
        age_ms = state.updated_at - b.current.since_t
        age_min = age_ms // 60000
        line = f"Bioma: {b.current.id} (desde há {age_min} minutos"
        if b.previous:
            line += f"; antes: {b.previous.id}"
        line += ")"
        return line

    def _fmt_spawn(self, state: State) -> str:
        s = state.player.spawn.current
        if s.is_home:
            return f"Spawn: casa principal (pos=({s.pos['x']},{s.pos['y']},{s.pos['z']}), dwell={s.dwell_ms//60000}min)"
        return f"Spawn: temporário (pos=({s.pos['x']},{s.pos['y']},{s.pos['z']}))"

    def _fmt_held_item(self, state: State) -> str:
        if not state.player.held_items_recent:
            return "Item em mãos: (vazio)"
        item = state.player.held_items_recent[0]
        age_ms = state.updated_at - item.since_t
        age_sec = age_ms // 1000
        return f"Item em mãos: {item.item} (há {age_sec}s)"

    def _fmt_entities(self, state: State) -> str:
        e = state.player.entities_nearby
        parts = []
        if e.hostil:
            parts.append(self._fmt_entity_count(e.hostil, "hostis"))
        if e.passivo:
            parts.append(self._fmt_entity_count(e.passivo, "passivos"))
        if e.neutro:
            parts.append(self._fmt_entity_count(e.neutro, "neutros"))
        if e.players > 0:
            parts.append(f"{e.players} outros players")
        if not parts:
            return "Entidades próximas: nenhuma"
        return f"Entidades próximas: {', '.join(parts)}"

    def _fmt_entity_count(self, m: dict[str, int], label: str) -> str:
        items = ", ".join(f"{v} {k}" for k, v in m.items())
        return f"{items} ({label})"

    def _fmt_activity(self, window: Window) -> list[str]:
        lines = []
        a = window.activity
        if a.blocks_broken:
            lines.append(f"- Blocos quebrados: {self._fmt_dict(a.blocks_broken)}")
        if a.blocks_placed:
            lines.append(f"- Blocos colocados: {self._fmt_dict(a.blocks_placed)}")
        if a.items_gained:
            lines.append(f"- Itens ganhos: {self._fmt_dict(a.items_gained)}")
        if a.items_lost:
            lines.append(f"- Itens perdidos: {self._fmt_dict(a.items_lost)}")
        if a.mobs_killed:
            lines.append(f"- Mobs mortos: {self._fmt_dict(a.mobs_killed)}")
        if a.containers_opened:
            lines.append(f"- Containers abertos: {self._fmt_dict(a.containers_opened)}")
        if a.stations_used:
            lines.append(f"- Estações usadas: {self._fmt_dict(a.stations_used)}")
        if not lines:
            lines.append("- (sem atividade)")
        return lines

    def _fmt_combat(self, window: Window) -> list[str]:
        lines = []
        cw = window.activity.combat_window
        if cw.damage_dealt:
            lines.append(f"- Dano causado: {self._fmt_dict(cw.damage_dealt)}")
        if cw.damage_taken:
            lines.append(f"- Dano recebido: {self._fmt_dict(cw.damage_taken)}")
        if cw.kills:
            kills_str = ", ".join(f"{len(cw.kills)} {k.entity} (com {k.weapon})" for k in cw.kills)
            lines.append(f"- Abates: {kills_str}")
        if cw.deaths_nearby:
            lines.append(f"- Mortes próximas: {len(cw.deaths_nearby)}")
        if cw.player_death:
            pd = cw.player_death
            lines.append(f"- MORTE DO JOGADOR: causa={pd.cause}" +
                        (f", por {pd.killer_entity}" if pd.killer_entity else "") +
                        (f", por {pd.killer_player}" if pd.killer_player else ""))
        if not lines:
            lines.append("- (sem combate na janela)")
        return lines

    def _fmt_dict(self, d: dict) -> str:
        return ", ".join(f"{v} {k}" for k, v in d.items())

    def _fmt_highlight(self, h) -> str:
        if h.type == "advancement":
            return f"advancement: {h.text}"
        if h.type == "effect_gained":
            return f"efeito ganho: {h.effect} (amplifier {h.amplifier})"
        if h.type == "effect_lost":
            return f"efeito perdido: {h.effect}"
        if h.type == "food_low":
            return f"comida baixa: {h.food}/20"
        if h.type == "health_low":
            return f"saúde baixa: {h.health:.1f}/20"
        if h.type == "biome_change":
            return f"troca de bioma: {h.from_biome} → {h.to_biome}"
        if h.type == "dimension_change":
            return f"troca de dimensão: {h.from_dim} → {h.to_dim}"
        if h.type == "weather_change":
            return f"clima: {h.weather_event}"
        if h.type == "player_death":
            return f"MORTE: {h.text}"
        return h.type
```

---

## 5. Few-shot Examples (opcional, para refinar a voz)

Para melhorar a qualidade da narração, especialmente com modelos menores (gpt-4o-mini, haiku, gemini-flash), pode-se adicionar exemplos no system prompt:

### 5.1 Quando adicionar few-shot
- Modelo returning narrações genéricas ou repetitivas.
- Modelo quebrando a quarta parede ("como jogador de Minecraft...").
- Modelo usando segunda pessoa ("você caminha...").
- Modelo sempre começando do mesmo jeito ("O capivara então...").

### 5.2 Exemplos para incluir (no system prompt, após as regras)

```
EXEMPLOS:

Estado: capivara minerando ferro à noite, sem mobs próximos, saúde cheia.
Narração anterior: "Subterrâneo, o som da picareta ecoa como um metrônomo paciente."
Narração: "A picareta encontra veios de ferro sob a rocha, e cada bloco que cede é uma promessa silenciosa de armadura melhor. Lá fora, a noite espera."

Estado: capivara fugindo de 3 zombies com 4 de saúde, sem comida, na savana.
Narração anterior: "O sol se põe e o capivara ainda não encontrou abrigo."
Narração: "Três pares de olhos brilham na escuridão, e o capivara corre — não por coragem, mas por falta de alternativa. A saúde racha, a comida acabou, e a savana de repente parece muito maior do que durante o dia."

Estado: capivara morto por creeper, perdeu netherite sword.
Narração anterior: "Um sibilo discreto. O capivara não ouve a tempo."
Narração: "Quando a fumaça se dissipa, o que resta é silêncio e o item mais valioso do capivara, agora jogado entre os blocos chamuscados. O respawn é frio, e a cama em casa não traz a espada de volta."

Estado: capivara parado em casa, sem atividade, dia ensolarado.
Narração anterior: "O capivara guardou o ferro no baú e respirou fundo."
Narração: "A manhã dourada entra pela janela da casa de terra. O capivara observa o horizonte sem pressa — alguns dias são só para respirar entre uma jornada e a próxima."
```

### 5.3 Implementação

```python
def _load_few_shot_examples(self) -> str:
    examples_path = Path(__file__).parent / "data" / "few_shot_examples.txt"
    if examples_path.exists():
        return examples_path.read_text(encoding="utf-8")
    return self._default_few_shot()

def _default_few_shot(self) -> str:
    return """
EXEMPLOS:

Estado: capivara minerando ferro à noite, sem mobs próximos, saúde cheia.
Narração: "A picareta encontra veios de ferro sob a rocha, e cada bloco que cede é uma promessa silenciosa de armadura melhor. Lá fora, a noite espera."

Estado: capivara fugindo de 3 zombies com 4 de saúde, sem comida, na savana.
Narração: "Três pares de olhos brilham na escuridão, e o capivara corre — não por coragem, mas por falta de alternativa. A saúde racha, a comida acabou, e a savana de repente parece muito maior do que durante o dia."

Estado: capivara morto por creeper, perdeu netherite sword.
Narração: "Quando a fumaça se dissipa, o que resta é silêncio e o item mais valioso do capivara, agora jogado entre os blocos chamuscados. O respawn é frio, e a cama em casa não traz a espada de volta."

Estado: capivara parado em casa, sem atividade, dia ensolarado.
Narração: "A manhã dourada entra pela janela da casa de terra. O capivara observa o horizonte sem pressa — alguns dias são só para respirar entre uma jornada e a próxima."
"""
```

Incluir no system prompt após as regras:
```python
prompt += "\n\n" + self._load_few_shot_examples()
```

---

## 6. Estratégia de Contexto (Janela Narrativa)

### 6.1 Narração anterior
- **Sempre** incluir a última narração no prompt (em `previous_narration`).
- Se for a primeira narração (sessão nova), omitir a seção.
- Se a última narração foi há muito tempo (mais de 5 ciclos), omitir também (contexto obsoleto).

### 6.2 Resumo de longa duração (futuro)
- A cada 10 ciclos, gerar um "resumo narrativo" via LLM que condensa as últimas 10 narrações em 2–3 frases.
- Usar esse resumo no system prompt em vez da narração bruta.
- Isso mantém contexto de sessões longas sem estourar o limite de tokens.

### 6.3 Prompt token budget
- System prompt: ~800–1500 tokens (com few-shot).
- User message: ~300–600 tokens (estado + janela).
- Total input: ~1500–2500 tokens.
- Output: ~80–150 tokens (200–400 caracteres).
- Total por ciclo: ~1600–2700 tokens.

### 6.4 Custo estimado por ciclo (gpt-4o-mini)
- Input: ~2000 tokens × $0.15/1M = $0.0003
- Output: ~100 tokens × $0.60/1M = $0.00006
- **Total: ~$0.0004 por ciclo**
- Por hora (60 ciclos): ~$0.024
- Por dia (8h de jogo): ~$0.19

---

## 7. Tom Adaptativo (heurísticas no `PromptBuilder`)

> O usuário não solicitou `mood_hint` no `state.json`, mas o `PromptBuilder` pode inferir o tom a partir dos dados disponíveis e adicionar uma dica no prompt.

### 7.1 Inferência de tom

```python
def _infer_mood_hint(self, state: State, window: Window) -> str:
    """Infere o tom sugerido para a narração com base no contexto."""
    cw = window.activity.combat_window

    # Morte do player = solene
    if cw.player_death:
        return "solene, melancólico (o jogador morreu)"

    # Combate ativo = tenso
    if cw.kills or cw.damage_taken:
        return "tenso, ação (combate ativo)"

    # Saúde/comida baixa = preocupação
    if state.player.health < 6 or state.player.food < 5:
        return "preocupado, tenso (saúde ou comida baixa)"

    # Troca de bioma = descoberta
    biome_changed = any(h.type == "biome_change" for h in window.highlights)
    if biome_changed:
        return "curioso, descoberta (bioma novo)"

    # Avançou conquista = realização
    if any(h.type == "advancement" for h in window.highlights):
        return "de realização, vitória (conquista desbloqueada)"

    # Mudou para noite = cautela
    if state.player.period == "noite":
        return "cauteloso, atmosférico (noite)"

    # Parado/idle = contemplativo
    total_activity = (
        sum(window.activity.blocks_broken.values()) +
        sum(window.activity.blocks_placed.values()) +
        sum(window.activity.items_gained.values()) +
        sum(window.activity.mobs_killed.values())
    )
    if total_activity < 3:
        return "contemplativo, pausado (pouca atividade)"

    # Default
    return "neutro, narrativo"
```

### 7.2 Incluir no prompt

Na user message, adicionar:
```
=== TOM SUGERIDO ===
{mood_hint}
```

Isso orienta o LLM sem forçar. O modelo ainda tem liberdade para adaptar.

---

## 8. Pós-processamento da Narração

### 8.1 Limpeza
Antes de salvar a narração, aplicar:

```python
def _clean_narration(self, raw: str) -> str:
    import re
    text = raw.strip()
    # Remover aspas extras
    if text.startswith('"') and text.endswith('"'):
        text = text[1:-1]
    # Remover prefixes comuns que o LLM adiciona
    text = re.sub(r"^(Narração:\s*|Narrador:\s*|\*\s*)", "", text, flags=re.IGNORECASE)
    # Remover markdown
    text = re.sub(r"\*\*(.+?)\*\*", r"\1", text)
    text = re.sub(r"\*(.+?)\*", r"\1", text)
    # Limitar tamanho
    if len(text) > self.config.max_chars * 2:
        # Truncar na última frase completa
        truncated = text[:self.config.max_chars * 2]
        last_period = truncated.rfind(".")
        if last_period > self.config.max_chars:
            text = truncated[:last_period + 1]
        else:
            text = truncated + "..."
    return text.strip()
```

### 8.2 Validação
- Se a narração tiver menos de 50 caracteres, considerar falha e usar fallback.
- Se a narração contiver palavras proibidas (`"AI"`, `"sistema"`, `"mod"`, `"você"`), logar warning mas aceitar (o LLM pode ter usado legit).

```python
FORBIDDEN_WORDS = ["sistema de narração", "como uma IA", "como um modelo", "você caminha", "você encontra"]

def _validate_narration(self, text: str) -> bool:
    if len(text) < 50:
        return False
    lower = text.lower()
    for w in FORBIDDEN_WORDS:
        if w in lower:
            logger.warning(f"Narração contém termo proibido: '{w}'")
            return False
    return True
```

### 8.3 Fluxo no `_process_cycle`
```python
raw_narration = self._call_llm_with_retry(messages, seq)
if raw_narration is None:
    # fallback
    narration = self.fallback.generate(state, window)
else:
    narration = self._clean_narration(raw_narration)
    if not self._validate_narration(narration):
        logger.warning(f"Ciclo #{seq}: narração rejeitada pela validação. Usando fallback.")
        narration = self.fallback.generate(state, window)
```

---

## 9. Variação e Anti-repetição

### 9.1 Comparação com narração anterior
- Antes de aceitar a narração, comparar com `last_narration`:
  - Se mais de 60% das palavras forem iguais (Jaccard similarity), rejeitar e regenerar (até 2 retries).
  - Se o modelo repetir a mesma frase, usar fallback.

```python
def _word_jaccard(a: str, b: str) -> float:
    wa = set(a.lower().split())
    wb = set(b.lower().split())
    if not wa or not wb:
        return 0.0
    return len(wa & wb) / len(wa | wb)

# No fluxo:
if self._word_jaccard(narration, self.last_narration) > 0.6:
    logger.warning(f"Ciclo #{seq}: narração muito similar à anterior (Jaccard > 0.6). Regenerando.")
    # Re-tentar com instrução adicional
    messages.append({"role": "assistant", "content": narration})
    messages.append({"role": "user", "content": "Sua narração está muito similar à anterior. Escreva uma narração completamente diferente, com palavras e estrutura distintas."})
    raw_narration = self._call_llm_with_retry(messages, seq)
    if raw_narration:
        narration = self._clean_narration(raw_narration)
```

### 9.2 Histórico de narrações recentes
- Manter um ring buffer das últimas 5 narrações em memória (não persistente).
- Incluir no prompt como "narrações recentes para evitar repetição":

```
=== NARRAÇÕES RECENTES (evite repetir) ===
1. "..."
2. "..."
3. "..."
```

Isso reduz drasticamente a repetição em sessões longas.

---

## 10. Testes e Avaliação

### 10.1 Testes unitários
- `test_prompt.py`:
  - Validar que `PromptBuilder.build()` retorna lista com 2 mensagens.
  - Validar que a user message contém "ESTADO ATUAL" e "JANELA".
  - Validar formatação de biome, entities, combat.
  - Validar inferência de mood_hint.

### 10.2 Testes de regressão de prompt
- Manter um conjunto de pares `(state.json, window.json)` de teste em `tests/fixtures/`.
- Para cada par, validar que o prompt gerado contém campos esperados.
- Usar um LLM mock (retorna string fixa) para validar o fluxo sem custo.

### 10.3 Avaliação qualitativa
- Rodar 10 ciclos sintéticos com cenários diversos (idle, combate, exploração, morte, pausa).
- Ler as 10 narrações geradas e avaliar:
  - Continuidade narrativa (liga com a anterior?).
  - Adaptação de tom (combate soa tenso? morte soa solene?).
  - Ausência de termos proibidos.
  - Variação de vocabulário.

---

## 11. Próximos Passos

Após implementar o `PromptBuilder`, validar com o `CHECKLIST.md`. Para detalhes do fluxo ponta-a-ponta, consulte `ARCHITECTURE.md` e `PYTHON_BRIDGE_SPEC.md`.
