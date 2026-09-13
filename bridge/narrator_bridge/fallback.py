from pathlib import Path
from jinja2 import Environment, FileSystemLoader, select_autoescape
from .schemas import State, Window
from .config import PromptConfig

class FallbackManager:
    def __init__(self, config: PromptConfig):
        self.config = config
        templates_dir = Path(__file__).parent / "templates"
        self.env = Environment(
            loader=FileSystemLoader(templates_dir),
            autoescape=select_autoescape(["html", "xml"]),
        )

    def generate(self, state: State, window: Window) -> str:
        template_name = self._choose_template(state, window)
        template = self.env.get_template(template_name)
        rendered = template.render(
            state=state,
            window=window,
            player=state.player,
            activity=window.activity,
        ).strip()
        # Garante tamanho mínimo de 50 caracteres
        if len(rendered) < 50:
            rendered = "O capivara observa o mundo ao seu redor com atenção, contemplando os acontecimentos recentes e preparando os próximos passos de sua longa jornada."
        return rendered

    def _choose_template(self, state: State, window: Window) -> str:
        a = window.activity
        cw = a.combat_window
        if cw.kills or cw.player_death or cw.damage_dealt or cw.damage_taken:
            return "combat.txt.j2"

        total_blocks = sum(a.blocks_placed.values()) + sum(a.blocks_broken.values())
        if total_blocks >= 10:
            return "build.txt.j2"

        if state.player.biome.previous and state.player.biome.current.id != state.player.biome.previous.id:
            return "explore.txt.j2"

        return "idle.txt.j2"