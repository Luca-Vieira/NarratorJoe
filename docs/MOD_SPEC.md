# Especificação do Mod Fabric (Java)

> Este documento descreve a implementação completa do mod Fabric 1.21.1 que coleta eventos do Minecraft e gera os arquivos JSON para o bridge Python. **Toda implementação deve seguir esta spec à risca.**

---

## 1. Identificação do Mod

| Campo | Valor |
|---|---|
| Mod ID | `ai-narrator` |
| Nome | AI Narrator |
| Versão | 0.1.0 |
| Minecraft | 1.21.1 |
| Fabric Loader | ≥ 0.16.0 |
| Fabric API | ≥ 0.102.0+1.21.1 |
| Java | 21 |
| Entrypoint principal | `com.example.ainarrator.AINarratorMod` |
| Entrypoint client | `com.example.ainarrator.AINarratorClient` (opcional, só para comandos client-side) |

### 1.1 `fabric.mod.json` (template)

```json
{
  "schemaVersion": 1,
  "id": "ai-narrator",
  "version": "0.1.0",
  "name": "AI Narrator",
  "description": "Mod que coleta eventos do Minecraft para um narrador AI externo via Python bridge.",
  "authors": ["Your Name"],
  "contact": {},
  "license": "MIT",
  "icon": "assets/ai-narrator/icon.png",
  "environment": "*",
  "entrypoints": {
    "main": ["com.example.ainarrator.AINarratorMod"],
    "client": ["com.example.ainarrator.AINarratorClient"]
  },
  "mixins": ["ai-narrator.mixins.json"],
  "depends": {
    "fabricloader": ">=0.16.0",
    "minecraft": "~1.21.1",
    "java": ">=21",
    "fabric-api": "*"
  }
}
```

---

## 2. Estrutura de Pacotes

```
com.example.ainarrator/
├── AINarratorMod.java              # Entrypoint principal (ServerTickEvents, init)
├── AINarratorClient.java           # Entrypoint client (comandos client-side)
├── config/
│   └── ModConfig.java              # Carrega ai-narrator.json do config dir
├── collector/
│   ├── EventCollector.java         # Singleton, mantém ring buffer de eventos
│   ├── GameEvent.java              # Classe base de eventos
│   ├── BlockPlaceEvent.java
│   ├── BlockBreakEvent.java
│   ├── ItemPickupEvent.java
│   ├── ItemDropEvent.java
│   ├── MobKillEvent.java
│   ├── ContainerOpenEvent.java
│   ├── StationUseEvent.java
│   ├── DamageDealtEvent.java
│   ├── DamageTakenEvent.java
│   ├── PlayerDeathEvent.java
│   ├── AdvancementEvent.java
│   ├── EffectChangeEvent.java
│   └── FoodLowEvent.java
├── state/
│   ├── StateBuilder.java           # Constrói state.json snapshot
│   ├── PlayerState.java            # POJO serializado
│   ├── BiomeHistory.java
│   └── SpawnHistory.java
├── window/
│   ├── WindowBuilder.java          # Constrói window.json a partir do ring buffer
│   └── ActivitySummary.java
├── io/
│   ├── AtomicJsonWriter.java       # temp + rename atômico
│   ├── JsonReader.java             # leitura tolerante a arquivo faltante
│   ├── TriggerWatcher.java         # poll trigger.flag a cada 1s
│   └── PathProvider.java           # resolve ~/ai-narrator/
├── net/
│   └── (vazio — sem rede no mod)
└── command/
    ├── NarratorCommand.java        # /narrator pause|resume|status|reload
    └── NarratorCommandNode.java
```

---

## 3. Inicialização do Mod

### 3.1 `AINarratorMod.java` (esboço)

```java
package com.example.ainarrator;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AINarratorMod implements ModInitializer {
    public static final String MOD_ID = "ai-narrator";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static AINarratorMod instance;
    private EventCollector collector;
    private TriggerWatcher triggerWatcher;
    private ModConfig config;

    @Override
    public void onInitialize() {
        instance = this;
        LOGGER.info("[AI-Narrator] Inicializando mod...");

        // 1. Carrega config
        this.config = ModConfig.load();

        // 2. Cria coletor de eventos (singleton em memória)
        this.collector = EventCollector.getInstance();

        // 3. Registra handlers de eventos do Fabric
        registerEventHandlers();

        // 4. Registra tick handler para coleta + I/O
        ServerTickEvents.END_SERVER_TICK.register(this::onEndServerTick);

        // 5. Inicia trigger watcher em thread dedicada
        this.triggerWatcher = new TriggerWatcher(config.getBridgePath(), this::onTrigger);
        new Thread(triggerWatcher, "AI-Narrator-Trigger").start();

        // 6. Registra comandos
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) ->
            NarratorCommand.register(dispatcher, collector, config));

        LOGGER.info("[AI-Narrator] Mod inicializado. Bridge path: {}", config.getBridgePath());
    }

    private void onEndServerTick(MinecraftServer server) {
        // Coleta eventos leves (chamadas assíncronas ao collector)
        // NUNCA fazer I/O de disco aqui
        collector.tick(server);
    }

    private void onTrigger() {
        // Chamado pela thread do TriggerWatcher quando trigger.flag é detectado
        // Aqui sim: escreve JSONs, atualiza heartbeat
        // Implementação detalhada na seção 6
    }

    private void registerEventHandlers() {
        // Ver seção 4
    }

    public static AINarratorMod getInstance() { return instance; }
}
```

### 3.2 Ciclo de vida
1. ** onLoad** (mundo carregando): criar diretório `~/ai-narrator/` se não existir.
2. **onInitialize** (mod carregado): registrar handlers, iniciar TriggerWatcher.
3. **onServerStart** (mundo aberto): resetar `seq` para 1, zerar ring buffer, escrever `state.json` inicial.
4. **onServerStopping** (mundo fechando): escrever `state.json` final com `paused: true`, logar "Mod desligado".
5. **onUnload** (mod descarregado): limpar recursos, fechar threads.

---

## 4. Eventos do Minecraft a Monitorar

### 4.1 Eventos do ciclo do server
| Evento Fabric API | Coleta | Vai para |
|---|---|---|
| `ServerTickEvents.END_SERVER_TICK` | Tick atual do server, contagem de TPS | heartbeat |
| `ServerPlayerEvents.AFTER_RESPAWN` | Reset após morte | state + window highlight |
| `ServerPlayerEvents.PLAYER_DISCONNECT` | Player saiu | state.paused=true |

### 4.2 Eventos de bloco
| Evento Fabric API | Dados coletados | Vai para |
|---|---|---|
| `PlayerBlockBreakEvents.AFTER` | block id, posição, was dropped | window.blocks_broken |
| `BlockPlaceEvents.BEFORE` (ou mixin) | block id, posição, colocado por | window.blocks_placed |

> **Atenção:** `PlayerBlockBreakEvents.AFTER` é fornecido pela Fabric API. Para block place, pode ser necessário um mixin em `BlockItem.place(...)` ou usar o evento `PlayerBlockPlaceEvents` (verificar disponibilidade na 1.21.1).

### 4.3 Eventos de item
| Evento Fabric API | Dados coletados | Vai para |
|---|---|---|
| `ItemPickupEvent` (mixin em `PlayerEntity.sendPickup`) | item id, quantidade | window.items_gained |
| `ItemDropEvent` (mixin em `PlayerEntity.dropItem`) | item id, quantidade | window.items_lost |
| `UseItemCallback` | item id, contexto | window.items_used (opcional) |

### 4.4 Eventos de combate
| Evento Fabric API | Dados coletados | Vai para |
|---|---|---|
| `ServerLivingEntityEvents.AFTER_DEATH` | entity id, causa, foi killer = player? | window.combat_window.kills ou .deaths_nearby |
| `ServerLivingEntityEvents.ALLOW_DAMAGE` | source, amount, target, attacker | window.combat_window.damage_dealt / damage_taken |

### 4.5 Eventos de UI/container
| Evento Fabric API | Dados coletados | Vai para |
|---|---|---|
| `OpenContainer` (mixin em `PlayerEntity.openHandledScreen`) | container type | window.containers_opened |
| `UseBlockCallback` (quando target é station) | station type | window.stations_used |

### 4.6 Eventos de progressão
| Evento Fabric API | Dados coletados | Vai para |
|---|---|---|
| `AdvancementCriterion.Callback` (ou mixin) | advancement id, criterion | window.highlights (type=advancement) |
| `StatusEffectAdded` (mixin em `LivingEntity.addStatusEffect`) | effect id, amplifier, duração | window.highlights (type=effect_gained) |
| `StatusEffectRemoved` | effect id | window.highlights (type=effect_lost) |

### 4.7 Eventos derivados (não do Fabric API, calculados)
| Evento | Quando | Vai para |
|---|---|---|
| `food_low` | food cai abaixo de 5 (limiar configurável) | window.highlights |
| `health_low` | health cai abaixo de 6 (limiar configurável) | window.highlights |
| `biome_change` | player entra em bioma diferente do anterior | state.biome + window.highlights |
| `dimension_change` | player troca de dimensão | state.dimension + window.highlights |
| `weather_change` | começa a chover/trovoar | state.weather + window.highlights |

---

## 5. Coleta de Dados para `state.json`

### 5.1 Snapshot do jogador (tick a tick em memória, escrito em disco a cada trigger)

```java
public class PlayerState {
    private String name;                    // player.getUsername()
    private String dimension;               // world.getRegistryKey().getValue().toString()
    private BlockPos pos;                   // player.getBlockPos()
    private float health;                   // player.getHealth()
    private int food;                       // player.getHungerManager().getFoodLevel()
    private long dayTime;                   // world.getTimeOfDay()
    private String period;                  // "dia" | "noite" | "crepúsculo" (calculado de dayTime)
    private Weather weather;                // { raining: bool, thundering: bool }
    private BiomeHistory biome;             // current + previous (mantido em memória)
    private SpawnHistory spawn;             // current + previous (mantido em memória)
    private List<HeldItem> heldItemsRecent; // últimos 5 itens segurados (timestamp + item id)
    private EntitiesNearby entitiesNearby;  // contagem por categoria
    private boolean paused;                 // flag /narrator pause
}
```

### 5.2 Lógica de "period" (dia/noite/crepúsculo)
```java
public static String calculatePeriod(long dayTime) {
    // dayTime é um long de 0 a 24000
    if (dayTime >= 0 && dayTime < 2000) return "amanhecer";
    if (dayTime >= 2000 && dayTime < 10000) return "dia";
    if (dayTime >= 10000 && dayTime < 12000) return "entardecer";
    if (dayTime >= 12000 && dayTime < 23000) return "noite";
    return "madrugada";
}
```

### 5.3 Histórico de bioma
```java
public class BiomeHistory {
    private BiomeEntry current;   // { id, since_t, pos }
    private BiomeEntry previous;  // { id, since_t, until_t }
}

public class BiomeEntry {
    private String id;            // ex: "minecraft:savanna"
    private long since_t;         // timestamp ms quando entrou
    private BlockPos pos;         // posição quando entrou
    private Long until_t;         // null no current, preenchido no previous
}
```

**Lógica de transição:** a cada tick, comparar `world.getBiome(player.getBlockPos())` com o atual. Se mudou:
1. Mover `current` para `previous` (preencher `until_t` com `System.currentTimeMillis()`).
2. Criar novo `current` com `since_t = now`, `pos = current pos`.
3. Adicionar `biome_change` ao ring buffer do `window.json`.

### 5.4 Histórico de spawn (cama/respawn point)
```java
public class SpawnHistory {
    private SpawnEntry current;   // { pos, set_t, is_home, dwell_ms }
    private SpawnEntry previous;  // { pos, is_home }
}

public class SpawnEntry {
    private BlockPos pos;
    private long set_t;           // quando o player setou essa spawn
    private boolean is_home;      // true se player passou > X minutos aqui
    private long dwell_ms;        // tempo acumulado que o player ficou perto (raio 32 blocos)
}
```

**Lógica de `is_home`:** se `dwell_ms > 5 minutos` (configurável), marcar `is_home = true`.

**Lógica de `dwell_ms`:** a cada tick, se `player.distanceTo(spawn.pos) < 32`, somar 50ms (tick de 50ms) ao `dwell_ms`.

### 5.5 Itens segurados recentemente
```java
public class HeldItem {
    private String item;          // item id
    private long since_t;         // quando o player segurou
}

// Em memória: manter uma LinkedList de no máximo 5 entradas.
// A cada tick, verificar o item na mão principal do player.
// Se mudou, adicionar nova entrada no topo da lista.
```

### 5.6 Entidades próximas
```java
public class EntitiesNearby {
    private Map<String, Integer> hostil;   // minecraft:zombie -> 3
    private Map<String, Integer> passivo;  // minecraft:cow -> 2
    private Map<String, Integer> neutro;   // minecraft:enderman -> 1
    private int players;                    // outros players no raio
}
```

**Lógica:** a cada 20 ticks (1s), escanear entidades no raio de 32 blocos do player. Classificar:
- **Hostil:** `HostileEntity` e subclasses (Zombie, Skeleton, Creeper, Spider, etc.)
- **Passivo:** `AnimalEntity` e subclasses (Cow, Pig, Sheep, etc.)
- **Neutro:** `MobEntity` que não é hostil nem passivo (Enderman, Zombie Piglin, etc.)
- **Players:** `PlayerEntity` excluindo o próprio.

> **Dica:** usar `world.getEntitiesByClass(...)` com bounding box centrada no player. Limitar a 64 entidades para não sobrecarregar.

---

## 6. Coleta de Dados para `window.json`

### 6.1 Ring buffer de eventos

```java
public class EventCollector {
    private static final EventCollector INSTANCE = new EventCollector();
    private final ConcurrentLinkedQueue<GameEvent> events = new ConcurrentLinkedQueue<>();
    private long windowStart = System.currentTimeMillis();

    public static EventCollector getInstance() { return INSTANCE; }

    public void add(GameEvent event) {
        events.add(event);
        // Limitar a 500 eventos para evitar estouro de memória
        while (events.size() > 500) events.poll();
    }

    public void tick(MinecraftServer server) {
        // Coleta derivada (não dependente de eventos do Fabric API)
        // Ex: verificar food_low, health_low, biome_change, weather_change
    }

    public WindowSnapshot buildWindow() {
        long now = System.currentTimeMillis();
        WindowSnapshot snapshot = new WindowSnapshot(windowStart, now);

        for (GameEvent e : events) {
            snapshot.addEvent(e);
        }

        return snapshot;
    }

    public void reset() {
        events.clear();
        windowStart = System.currentTimeMillis();
    }
}
```

### 6.2 Construção de `window.json`

O `WindowBuilder` itera sobre o ring buffer e agrega eventos em contadores:

```java
public class WindowBuilder {
    public JsonObject build(WindowSnapshot snapshot) {
        JsonObject root = new JsonObject();
        root.addProperty("seq", ModState.getSeq());
        root.addProperty("window_start", snapshot.getWindowStart());
        root.addProperty("window_end", snapshot.getWindowEnd());

        JsonObject activity = new JsonObject();
        activity.add("blocks_placed", aggregateByItem(snapshot, BlockPlaceEvent.class));
        activity.add("blocks_broken", aggregateByItem(snapshot, BlockBreakEvent.class));
        activity.add("items_gained", aggregateByItem(snapshot, ItemPickupEvent.class));
        activity.add("items_lost", aggregateByItem(snapshot, ItemDropEvent.class));
        activity.add("mobs_killed", aggregateByEntity(snapshot, MobKillEvent.class));
        activity.add("containers_opened", aggregateByContainer(snapshot, ContainerOpenEvent.class));
        activity.add("stations_used", aggregateByStation(snapshot, StationUseEvent.class));

        // NOVO: combat_window (adição solicitada pelo usuário)
        activity.add("combat_window", buildCombatWindow(snapshot));

        root.add("activity", activity);
        root.add("highlights", buildHighlights(snapshot));
        return root;
    }

    private JsonObject buildCombatWindow(WindowSnapshot snapshot) {
        JsonObject combat = new JsonObject();

        // damage_dealt: { entity_id: total_damage }
        combat.add("damage_dealt", aggregateDamageByEntity(snapshot, DamageDealtEvent.class));

        // damage_taken: { source: total_damage }
        combat.add("damage_taken", aggregateDamageBySource(snapshot, DamageTakenEvent.class));

        // kills: [ { entity, t, weapon } ]
        combat.add("kills", buildKillsArray(snapshot));

        // deaths_nearby: [ { entity, cause, t, distance } ]
        combat.add("deaths_nearby", buildDeathsArray(snapshot));

        // player_death: { cause, t, pos } | null se não morreu
        JsonObject playerDeath = buildPlayerDeath(snapshot);
        if (playerDeath != null) combat.add("player_death", playerDeath);

        return combat;
    }
}
```

### 6.3 Schema detalhado de `combat_window` (adição solicitada)

Veja `SCHEMA.md` para o schema JSON completo. Resumo dos campos:

| Campo | Tipo | Descrição |
|---|---|---|
| `damage_dealt` | `Map<String, Number>` | Dano total causado pelo player, agrupado por entity id |
| `damage_taken` | `Map<String, Number>` | Dano total recebido pelo player, agrupado por source (fall, mob, lava, etc.) |
| `kills` | `Array<KillEntry>` | Lista de mortes causadas pelo player |
| `deaths_nearby` | `Array<DeathEntry>` | Morte de outras entidades perto (não causadas pelo player) |
| `player_death` | `DeathEntry \| null` | Se o player morreu na janela, detalhes da morte |

### 6.4 Highlights (eventos notáveis)

Eventos que não são agregados mas são listados individualmente como destaques:

```java
private JsonArray buildHighlights(WindowSnapshot snapshot) {
    JsonArray highlights = new JsonArray();

    for (GameEvent e : snapshot.getEvents()) {
        if (e instanceof AdvancementEvent ae) {
            highlights.add(simpleHighlight("advancement", ae.text(), ae.t()));
        }
        if (e instanceof EffectChangeEvent ee && ee.added()) {
            highlights.add(effectHighlight(ee.effectId(), ee.amplifier(), ee.t()));
        }
        if (e instanceof FoodLowEvent fe) {
            highlights.add(foodLowHighlight(fe.food(), fe.t()));
        }
        if (e instanceof HealthLowEvent he) {
            highlights.add(healthLowHighlight(he.health(), he.t()));
        }
        if (e instanceof BiomeChangeEvent bc) {
            highlights.add(biomeChangeHighlight(bc.from(), bc.to(), bc.t()));
        }
        if (e instanceof WeatherChangeEvent wc) {
            highlights.add(weatherHighlight(wc.event(), wc.t()));
        }
        if (e instanceof DimensionChangeEvent dc) {
            highlights.add(dimensionChangeHighlight(dc.from(), dc.to(), dc.t()));
        }
        if (e instanceof PlayerDeathEvent pe) {
            highlights.add(playerDeathHighlight(pe.cause(), pe.t()));
        }
    }

    return highlights;
}
```

---

## 7. Escrita Atômica de Arquivos

### 7.1 `AtomicJsonWriter`

```java
public class AtomicJsonWriter {
    private static final Logger LOG = LoggerFactory.getLogger("AI-Narrator-IO");

    /**
     * Escreve JSON atomicamente: temp file + fsync + rename.
     *
     * @param target caminho final
     * @param json conteúdo a escrever
     */
    public static void write(Path target, JsonObject json) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");

        // 1. Escreve no temp
        String content = new GsonBuilder().setPrettyPrinting().create().toJson(json);
        Files.writeString(tmp, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

        // 2. fsync (garante que o conteúdo está em disco)
        try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.READ)) {
            ch.force(true);
        }

        // 3. Rename atômico
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // Fallback: rename não-atômico (apenas em filesystems exóticos)
            LOG.warn("ATOMIC_MOVE não suportado, usando replace não-atômico");
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Deleta um arquivo se ele existir. Não lança erro se não existe.
     */
    public static void deleteIfExists(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            LOG.error("Falha ao deletar {}: {}", target, e.getMessage());
        }
    }

    /**
     * Cria um arquivo vazio (para flags).
     */
    public static void touch(Path target) throws IOException {
        Files.createFile(target);
    }
}
```

### 7.2 Cuidados especiais
- **Não** usar `FileWriter` ou `PrintWriter` direto — eles não garantem atomicidade.
- **Não** usar `Files.writeString` direto no target — ele não é atômico (trunca primeiro, escreve depois).
- **Sempre** usar o padrão temp + fsync + rename.
- **Encoding:** sempre `StandardCharsets.UTF_8` (nunca usar o encoding default do sistema).

---

## 8. TriggerWatcher (Detecção da Flag do Python)

### 8.1 Implementação

```java
public class TriggerWatcher implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger("AI-Narrator-Trigger");

    private final Path bridgePath;
    private final Runnable onTrigger;
    private final Path triggerFlag;

    public TriggerWatcher(Path bridgePath, Runnable onTrigger) {
        this.bridgePath = bridgePath;
        this.onTrigger = onTrigger;
        this.triggerFlag = bridgePath.resolve("trigger.flag");
    }

    @Override
    public void run() {
        LOG.info("TriggerWatcher iniciado. Monitorando: {}", triggerFlag);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (Files.exists(triggerFlag)) {
                    LOG.debug("trigger.flag detectado!");
                    AtomicJsonWriter.deleteIfExists(triggerFlag);
                    onTrigger.run();
                }
                Thread.sleep(1000); // poll a cada 1s
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOG.error("Erro no TriggerWatcher: {}", e.getMessage(), e);
                try { Thread.sleep(5000); } catch (InterruptedException ie) { break; }
            }
        }
        LOG.info("TriggerWatcher finalizado.");
    }
}
```

### 8.2 Fluxo do `onTrigger`
1. Zerar ring buffer do `EventCollector`.
2. Atualizar `windowStart = now`.
3. Construir novo `state.json` (snapshot atual).
4. Construir novo `window.json` (a partir do ring buffer recém-zerado — vazio no primeiro instante, mas o Python já consumiu o anterior).

> **Atenção:** o `onTrigger` é chamado pela **thread do TriggerWatcher**, não pela thread principal do server. Acesso ao `EventCollector` deve ser thread-safe (já é, via `ConcurrentLinkedQueue`). Acesso ao `MinecraftServer` ou `ServerPlayer` deve ser sincronizado ou feito via agendamento no tick.

**Alternativa segura:** ao invés de escrever JSONs no `onTrigger` direto, setar um `volatile boolean triggerPending = true` e tratar isso no `END_SERVER_TICK` (onde se tem acesso seguro ao server).

```java
private volatile boolean triggerPending = false;

private void onTrigger() {
    triggerPending = true;
}

private void onEndServerTick(MinecraftServer server) {
    if (triggerPending) {
        triggerPending = false;
        // Agora seguro: estamos na thread do server
        processTrigger(server);
    }
    collector.tick(server);
}

private void processTrigger(MinecraftServer server) {
    // 1. Resetar ring buffer
    collector.reset();

    // 2. Construir snapshots
    JsonObject state = StateBuilder.build(server, config);
    JsonObject window = WindowBuilder.build(collector.buildWindow());

    // 3. Escrever atomicamente
    try {
        AtomicJsonWriter.write(config.getBridgePath().resolve("state.json"), state);
        AtomicJsonWriter.write(config.getBridgePath().resolve("window.json"), window);
        writeHeartbeat(server);
        ModState.incrementSeq();
    } catch (IOException e) {
        LOG.error("Falha ao escrever JSONs: {}", e.getMessage(), e);
    }
}
```

---

## 9. Heartbeat

### 9.1 Escrita do `heartbeat.json`
A cada 5 segundos (configurável), o mod escreve:
```json
{
  "last_seen": 1788398627884,
  "tps": 19.8,
  "player_count": 1,
  "mod_version": "0.1.0",
  "seq": 42
}
```

### 9.2 Lógica
- Thread dedicada ou agendado no tick (a cada 100 ticks = 5s).
- Se o Python não receber heartbeat por 30s, considera o mod "morto" e pausa processamento.

---

## 10. Comandos Admin

### 10.1 `/narrator pause`
- Seta `paused = true` no `state.json`.
- Para de coletar eventos (ring buffer não recebe novos).
- Log: "Narrador pausado pelo jogador."

### 10.2 `/narrator resume`
- Seta `paused = false`.
- Log: "Narrador retomado."

### 10.3 `/narrator status`
- Mostra no chat:
  - Estado atual (pausado/ativo)
  - Último seq processado
  - Quantidade de eventos no ring buffer
  - Tempo desde último trigger
  - Caminho do bridge
  - Versão do mod

### 10.4 `/narrator reload`
- Recarrega `ai-narrator.json` do diretório de config sem reiniciar o mod.

### 10.5 Implementação
```java
public class NarratorCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                  EventCollector collector,
                                  ModConfig config) {
        dispatcher.register(CommandManager.literal("narrator")
            .requires(src -> src.hasPermissionLevel(2)) // op level 2+
            .then(CommandManager.literal("pause")
                .executes(ctx -> pause(ctx.getSource(), collector)))
            .then(CommandManager.literal("resume")
                .executes(ctx -> resume(ctx.getSource(), collector)))
            .then(CommandManager.literal("status")
                .executes(ctx -> status(ctx.getSource(), collector, config)))
            .then(CommandManager.literal("reload")
                .executes(ctx -> reload(ctx.getSource(), config)))
        );
    }
    // ...
}
```

---

## 11. Configuração do Mod

### 11.1 `config/ai-narrator.json` (lido do diretório de config do Minecraft)

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

### 11.2 `ModConfig.java`

```java
public class ModConfig {
    private Path bridgePath;
    private long heartbeatIntervalMs;
    private long triggerPollMs;
    private int maxRingBufferEvents;
    private int entitiesScanRadius;
    private int entitiesScanIntervalTicks;
    private int homeRadius;
    private long homeDwellThresholdMs;
    private int foodLowThreshold;
    private int healthLowThreshold;
    private String logLevel;

    public static ModConfig load() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path configFile = configDir.resolve("ai-narrator.json");

        ModConfig config = new ModConfig();
        // Defaults
        config.bridgePath = Paths.get(System.getProperty("user.home"), "ai-narrator");
        config.heartbeatIntervalMs = 5000;
        // ... outros defaults

        if (Files.exists(configFile)) {
            // Sobrescreve defaults com JSON
            JsonObject json = JsonParser.parseString(Files.readString(configFile)).getAsJsonObject();
            if (json.has("bridge_path")) {
                String p = json.get("bridge_path").getAsString();
                if (p.startsWith("~")) p = System.getProperty("user.home") + p.substring(1);
                config.bridgePath = Paths.get(p);
            }
            // ... outros campos
        } else {
            // Salva defaults
            save(config, configFile);
        }

        // Garante que o diretório do bridge existe
        Files.createDirectories(config.bridgePath);
        return config;
    }

    public Path getBridgePath() { return bridgePath; }
    // ... outros getters
}
```

---

## 12. Mixins Necessários

Para eventos que não têm callback na Fabric API, serão necessários mixins:

### 12.1 `ai-narrator.mixins.json`
```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.example.ainarrator.mixin",
  "compatibilityLevel": "JAVA_21",
  "mixins": [
    "PlayerEntityMixin",
    "LivingEntityMixin",
    "ServerPlayerEntityMixin"
  ],
  "client": [],
  "injectors": {
    "defaultRequire": 1
  }
}
```

### 12.2 Exemplo: `PlayerEntityMixin` para detectar pickup de itens

```java
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "sendPickup", at = @At("HEAD"))
    private void onPickup(ItemEntity item, int count, CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self.getWorld().isClient) return; // server side only

        String itemId = Registries.ITEM.getId(item.getStack().getItem()).toString();
        EventCollector.getInstance().add(new ItemPickupEvent(itemId, count, System.currentTimeMillis()));
    }
}
```

### 12.3 Outros mixins necessários
- `LivingEntityMixin` → interceptar `addStatusEffect` (effect_gained) e `removeStatusEffect` (effect_lost).
- `ServerPlayerEntityMixin` → interceptar `teleport` para dimension_change.
- `PlayerEntityMixin` → interceptar `dropItem` (items_lost) e `openHandledScreen` (containers_opened).

> **Atenção:** verificar os nomes exatos dos métodos na 1.21.1 usando o `yarn` mappings. Os nomes podem mudar entre versões.

---

## 13. Considerações de Performance

- **Tick do server (20 Hz):** apenas operações em memória. Custo: < 0.1ms por tick.
- **Escrita de JSON (a cada trigger):** ~1-3ms para arquivos de 2-5KB em SSD.
- **Scan de entidades (a cada 1s):** ~0.5-2ms para raio de 32 blocos.
- **Heartbeat (a cada 5s):** ~0.5ms.
- **TriggerWatcher (a cada 1s):** ~0.1ms (apenas `Files.exists`).

### 13.1 O que NÃO fazer
- ❌ I/O de disco na thread do tick.
- ❌ Chamadas de rede (LLM, etc.) no mod — isso é trabalho do Python.
- ❌ Iterar sobre todas as entidades carregadas no mundo — usar bounding box.
- ❌ Serializar JSON com reflection — usar Gson com classes POJO pré-mapeadas.

### 13.2 O que fazer
- ✅ Cachear valores calculados frequentemente (ex: `period` do `day_time`).
- ✅ Usar `ConcurrentLinkedQueue` para o ring buffer.
- ✅ Limitar o tamanho do ring buffer (500 eventos) para evitar OOM.
- ✅ Usar `volatile` para flags entre threads (`triggerPending`).

---

## 14. Testes

### 14.1 Testes manuais
1. Iniciar mundo plano com `/give @p minecraft:diamond_sword`.
2. Quebrar 5 blocos de grama, colocar 3 de pedra, matar 1 porco.
3. Aguardar 60s e verificar `window.json` (deve conter esses eventos).
4. Verificar `state.json` (deve conter a posição atual, biome "plains", health 20).
5. Criar `trigger.flag` manualmente (`touch ~/ai-narrator/trigger.flag`).
6. Verificar se o mod detectou (logs) e gerou novos JSONs.

### 14.2 Testes automatizados (futuro)
- Mock do `MinecraftServer` para validar `StateBuilder` e `WindowBuilder`.
- Teste do `AtomicJsonWriter` em disco real.
- Teste do `TriggerWatcher` com flag criada manualmente.

---

## 15. Próximos Passos

Após implementar o mod, valide seguindo o `CHECKLIST.md` e parta para o `PYTHON_BRIDGE_SPEC.md` para o lado Python do bridge.
