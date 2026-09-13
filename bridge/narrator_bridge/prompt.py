import re
from pathlib import Path
from typing import List, Dict, Optional
from loguru import logger
from .config import PromptConfig
from .schemas import State, Window, Highlight

STYLE_DESCRIPTIONS = {
    "documentary": "documental, distante, factual, voz neutra de narrador de documentário",
    "storyteller": "literário, evocativo, terceira pessoa, romance picaresco",
    "playful": "leve, bem-humorado, irônico, com toques de cumplicidade",
    "epic": "grandioso, heroico, fantasia épica, com cadência de saga",
}

FORBIDDEN_WORDS = [
    "sistema de narração",
    "como uma ia",
    "como um modelo",
    "você caminha",
    "você encontra",
]

class PromptBuilder:
    def __init__(self, config: PromptConfig):
        self.config = config
        self.system_prompt_template = self._load_system_prompt()

    def build(self, state: State, window: Window, last_narration: str) -> List[Dict[str, str]]:
        system = self._render_system(state, last_narration)
        user = self._render_user(state, window, last_narration)
        return [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ]

    def _load_system_prompt(self) -> str:
        if self.config.system_prompt_file and Path(self.config.system_prompt_file).exists():
            return Path(self.config.system_prompt_file).read_text(encoding="utf-8")
        return self._default_system_prompt()

    def _default_system_prompt(self) -> str:
        return """Você é um narrador literário que acompanha as aventuras de um jogador de Minecraft em tempo real. Sua voz é evocativa, contemplativa, em terceira pessoa, no estilo de um romance de fantasia picaresco.

REGRAS:
- Escreva em português brasileiro culto, com fluxo literário natural.
- Use entre {min_chars} e {max_chars} caracteres por narração (uma a três frases).
- Conecte-se à narração anterior quando fizer sentido, sem repeti-la.
- Adapte o tom ao contexto: combate é tenso, exploração é curioso, descanso é contemplativo, morte é solene.
- Refira-se ao jogador pelo nome "{player_name}" ou por apelidos naturais derivados (ex: "{player_alias}").
- NUNCA mencione: "sistema", "AI", "mod", "script", "JSON", "código", "prompt", "tokens", "API".
- NUNCA dê dicas de gameplay, conselhos ou instruções.
- NUNCA use segunda pessoa ("você"). Sempre terceira pessoa.
- NUNCA mencione números concretos — use linguagem qualitativa.
- Varie o vocabulário entre narrações para evitar repetição.

ESTILO: {style_description}"""

    def _default_few_shot(self) -> str:
        return """EXEMPLOS:

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
"""

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

        prompt += "\n\n" + self._default_few_shot()
        return prompt

    def _derive_alias(self, name: str) -> str:
        parts = re.findall(r"[A-Z][a-z]+|[a-z]+", name)
        if not parts:
            alias = name.lower()
        else:
            alias = parts[-1].lower()

        alias_map = {
            "capybara": "capivara",
        }
        return alias_map.get(alias, alias)

    def _infer_mood_hint(self, state: State, window: Window) -> str:
        cw = window.activity.combat_window
        if cw.player_death:
            return "solene, melancólico (o jogador morreu)"
        if cw.kills or cw.damage_taken:
            return "tenso, ação (combate ativo)"
        if state.player.health < 6 or state.player.food < 5:
            return "preocupado, tenso (saúde ou comida baixa)"
        if any(h.type == "biome_change" for h in window.highlights):
            return "curioso, descoberta (bioma novo)"
        if any(h.type == "advancement" for h in window.highlights):
            return "de realização, vitória (conquista desbloqueada)"
        if state.player.period == "noite":
            return "cauteloso, atmosférico (noite)"

        total_act = (
            sum(window.activity.blocks_broken.values())
            + sum(window.activity.blocks_placed.values())
            + sum(window.activity.items_gained.values())
            + sum(window.activity.mobs_killed.values())
        )
        if total_act < 3:
            return "contemplativo, pausado (pouca atividade)"

        return "neutro, narrativo"

    def _render_user(self, state: State, window: Window, last_narration: str) -> str:
        lines = []
        lines.append("=== ESTADO ATUAL ===")
        lines.append(f"Jogador: {state.player.name}")
        lines.append(f"Dimensão: {state.player.dimension}")
        lines.append(self._fmt_biome(state))
        lines.append(f"Posição: ({state.player.pos.get('x')}, {state.player.pos.get('y')}, {state.player.pos.get('z')})")
        lines.append(f"Saúde: {state.player.health:.1f}/20 | Comida: {state.player.food}/20")
        lines.append(f"Horário: {state.player.period} (day_time={state.player.day_time})")
        lines.append(f"Clima: {'com chuva' if state.player.weather.raining else 'sem chuva'}"
                     + (", com trovoada" if state.player.weather.thundering else ""))
        lines.append(self._fmt_spawn(state))
        lines.append(self._fmt_held_item(state))
        lines.append(self._fmt_entities(state))
        lines.append("")

        dur_s = max(0, (window.window_end - window.window_start) // 1000)
        lines.append(f"=== JANELA (últimos {dur_s}s) ===")
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

        mood = self._infer_mood_hint(state, window)
        lines.append(f"=== TOM SUGERIDO ===\n{mood}\n")

        if last_narration:
            lines.append(f"=== NARRAÇÃO ANTERIOR ===\n\"{last_narration}\"\n")

        min_chars = max(100, self.config.max_chars // 3)
        lines.append(f"=== INSTRUÇÃO ===\nEscreva a próxima narração ({min_chars}–{self.config.max_chars} caracteres) que continue a história acompanhando os eventos desta janela.")

        return "\n".join(lines)

    def _fmt_biome(self, state: State) -> str:
        b = state.player.biome
        age_ms = state.updated_at - b.current.since_t
        age_min = max(0, age_ms // 60000)
        line = f"Bioma: {b.current.id} (desde há {age_min} minutos"
        if b.previous:
            line += f"; antes: {b.previous.id}"
        line += ")"
        return line

    def _fmt_spawn(self, state: State) -> str:
        s = state.player.spawn.current
        px = s.pos.get('x', 0)
        py = s.pos.get('y', 0)
        pz = s.pos.get('z', 0)
        if s.is_home:
            return f"Spawn: casa principal (pos=({px},{py},{pz}), dwell={s.dwell_ms // 60000}min)"
        return f"Spawn: temporário (pos=({px},{py},{pz}))"

    def _fmt_held_item(self, state: State) -> str:
        if not state.player.held_items_recent:
            return "Item em mãos: (vazio)"
        item = state.player.held_items_recent[0]
        age_ms = state.updated_at - item.since_t
        age_sec = max(0, age_ms // 1000)
        return f"Item em mãos: {item.item} (há {age_sec}s)"

    def _fmt_entities(self, state: State) -> str:
        e = state.player.entities_nearby
        parts = []
        if e.hostil:
            parts.append(self._fmt_dict(e.hostil) + " (hostis)")
        if e.passivo:
            parts.append(self._fmt_dict(e.passivo) + " (passivos)")
        if e.neutro:
            parts.append(self._fmt_dict(e.neutro) + " (neutros)")
        if e.players > 0:
            parts.append(f"{e.players} outros players")
        if not parts:
            return "Entidades próximas: nenhuma"
        return f"Entidades próximas: {', '.join(parts)}"

    def _fmt_activity(self, window: Window) -> List[str]:
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

    def _fmt_combat(self, window: Window) -> List[str]:
        lines = []
        cw = window.activity.combat_window
        if cw.damage_dealt:
            lines.append(f"- Dano causado: {self._fmt_float_dict(cw.damage_dealt)}")
        if cw.damage_taken:
            lines.append(f"- Dano recebido: {self._fmt_float_dict(cw.damage_taken)}")
        if cw.kills:
            kills_str = ", ".join(f"{k.entity} (com {k.weapon})" for k in cw.kills)
            lines.append(f"- Abates: {kills_str}")
        if cw.deaths_nearby:
            lines.append(f"- Mortes próximas: {len(cw.deaths_nearby)}")
        if cw.player_death:
            pd = cw.player_death
            det = f"causa={pd.cause}"
            if pd.killer_entity:
                det += f", por {pd.killer_entity}"
            if pd.killer_player:
                det += f", por {pd.killer_player}"
            lines.append(f"- MORTE DO JOGADOR: {det}")
        if not lines:
            lines.append("- (sem combate na janela)")
        return lines

    def _fmt_dict(self, d: Dict[str, int]) -> str:
        return ", ".join(f"{v} {k.split(':')[-1]}" for k, v in d.items())

    def _fmt_float_dict(self, d: Dict[str, float]) -> str:
        return ", ".join(f"{v:.1f} {k.split(':')[-1]}" for k, v in d.items())

    def _fmt_highlight(self, h: Highlight) -> str:
        if h.type == "advancement":
            return f"advancement: {h.text}"
        if h.type == "effect_gained":
            return f"efeito ganho: {h.effect} (amplifier {h.amplifier})"
        if h.type == "effect_lost":
            return f"efeito perdido: {h.effect}"
        if h.type == "food_low":
            return f"comida baixa: {h.food}/20"
        if h.type == "health_low":
            val = f"{h.health:.1f}" if h.health is not None else "?"
            return f"saúde baixa: {val}/20"
        if h.type == "biome_change":
            return f"troca de bioma: {h.from_biome} → {h.to_biome}"
        if h.type == "dimension_change":
            return f"troca de dimensão: {h.from_dim} → {h.to_dim}"
        if h.type == "weather_change":
            return f"clima: {h.weather_event}"
        if h.type == "player_death":
            return f"MORTE: {h.text}"
        return h.type

    def clean_narration(self, raw: str) -> str:
        text = raw.strip()
        if text.startswith('"') and text.endswith('"'):
            text = text[1:-1]
        text = re.sub(r"^(Narração:\s*|Narrador:\s*|\*\s*)", "", text, flags=re.IGNORECASE)
        text = re.sub(r"\*\*(.+?)\*\*", r"\1", text)
        text = re.sub(r"\*(.+?)\*", r"\1", text)

        max_allowed = self.config.max_chars * 2
        if len(text) > max_allowed:
            truncated = text[:max_allowed]
            last_period = truncated.rfind(".")
            if last_period > self.config.max_chars:
                text = truncated[:last_period + 1]
            else:
                text = truncated + "..."
        return text.strip()

    def validate_narration(self, text: str) -> bool:
        if len(text) < 50:
            return False
        lower = text.lower()
        for w in FORBIDDEN_WORDS:
            if w in lower:
                logger.warning(f"Narração contém termo proibido: '{w}'")
                return False
        return True

    @staticmethod
    def word_jaccard(a: str, b: str) -> float:
        wa = set(a.lower().split())
        wb = set(b.lower().split())
        if not wa or not wb:
            return 0.0
        return len(wa & wb) / len(wa | wb)