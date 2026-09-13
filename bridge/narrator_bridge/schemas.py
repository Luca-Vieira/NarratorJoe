from typing import Optional, Dict, List
from pydantic import BaseModel, Field

class Weather(BaseModel):
    raining: bool
    thundering: bool

class BiomeEntry(BaseModel):
    id: str
    since_t: int
    pos: Optional[Dict[str, int]] = None
    until_t: Optional[int] = None

class BiomeHistory(BaseModel):
    current: BiomeEntry
    previous: Optional[BiomeEntry] = None

class SpawnEntry(BaseModel):
    pos: Dict[str, int]
    set_t: int
    is_home: bool = False
    dwell_ms: int = 0

class SpawnHistory(BaseModel):
    current: SpawnEntry
    previous: Optional[SpawnEntry] = None

class HeldItem(BaseModel):
    item: str
    since_t: int

class EntitiesNearby(BaseModel):
    hostil: Dict[str, int] = Field(default_factory=dict)
    passivo: Dict[str, int] = Field(default_factory=dict)
    neutro: Dict[str, int] = Field(default_factory=dict)
    players: int = 0

class PlayerState(BaseModel):
    name: str
    dimension: str
    pos: Dict[str, int]
    health: float
    food: int
    day_time: int
    period: str
    weather: Weather
    biome: BiomeHistory
    spawn: SpawnHistory
    held_items_recent: List[HeldItem] = Field(default_factory=list)
    entities_nearby: EntitiesNearby = Field(default_factory=EntitiesNearby)
    paused: bool = False

class State(BaseModel):
    seq: int
    updated_at: int
    player: PlayerState

# ---- Window & Combat Window ----

class KillEntry(BaseModel):
    entity: str
    t: int
    weapon: Optional[str] = None
    damage: Optional[float] = None

class DeathEntry(BaseModel):
    entity: Optional[str] = None
    cause: str
    t: int
    pos: Optional[Dict[str, int]] = None
    distance: Optional[float] = None

class PlayerDeath(BaseModel):
    cause: str
    t: int
    pos: Optional[Dict[str, int]] = None
    killer_entity: Optional[str] = None
    killer_player: Optional[str] = None

class CombatWindow(BaseModel):
    damage_dealt: Dict[str, float] = Field(default_factory=dict)
    damage_taken: Dict[str, float] = Field(default_factory=dict)
    kills: List[KillEntry] = Field(default_factory=list)
    deaths_nearby: List[DeathEntry] = Field(default_factory=list)
    player_death: Optional[PlayerDeath] = None

class Highlight(BaseModel):
    type: str
    t: int
    text: Optional[str] = None
    effect: Optional[str] = None
    amplifier: Optional[int] = None
    food: Optional[int] = None
    health: Optional[float] = None
    from_biome: Optional[str] = None
    to_biome: Optional[str] = None
    from_dim: Optional[str] = None
    to_dim: Optional[str] = None
    weather_event: Optional[str] = None

class Activity(BaseModel):
    blocks_placed: Dict[str, int] = Field(default_factory=dict)
    blocks_broken: Dict[str, int] = Field(default_factory=dict)
    items_gained: Dict[str, int] = Field(default_factory=dict)
    items_lost: Dict[str, int] = Field(default_factory=dict)
    mobs_killed: Dict[str, int] = Field(default_factory=dict)
    containers_opened: Dict[str, int] = Field(default_factory=dict)
    stations_used: Dict[str, int] = Field(default_factory=dict)
    combat_window: CombatWindow = Field(default_factory=CombatWindow)

class Window(BaseModel):
    seq: int
    window_start: int
    window_end: int
    activity: Activity
    highlights: List[Highlight] = Field(default_factory=list)

class Heartbeat(BaseModel):
    last_seen: int
    tps: float
    player_count: int
    mod_version: str
    seq: int

class PromptMessage(BaseModel):
    role: str
    content: str

class PromptDebug(BaseModel):
    seq: int
    timestamp: int
    messages: List[PromptMessage]
    provider: str
    model: str