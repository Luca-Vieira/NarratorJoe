# AI Narrator - Mod Fabric (Minecraft 1.21.1)

Mod Fabric responsável por coletar o estado do jogador e eventos de gameplay no Minecraft 1.21.1 e exportá-los atomicamente para consumo do AI Narrator Bridge.

## Requisitos

- Java 21 (JDK 21)
- Minecraft 1.21.1
- Fabric Loader >= 0.16.0
- Fabric API >= 0.102.0+1.21.1

## Como Compilar

No diretório `mod/`:

```bash
# Windows
.\gradlew.bat build

# Linux / macOS
./gradlew build
```

O arquivo compilado e remapeado será gerado em:
`build/libs/ai-narrator-mod-0.1.0.jar`

## Como Instalar

1. Copie `build/libs/ai-narrator-mod-0.1.0.jar` para a pasta `.minecraft/mods/`.
2. Certifique-se de que a `Fabric API` para 1.21.1 também está na pasta `mods/`.
3. Inicie o Minecraft com o perfil do Fabric Loader 1.21.1.

## Comandos

- `/narrator pause` - Pausa temporariamente a coleta de eventos para a narração.
- `/narrator resume` - Retoma a coleta de eventos.
- `/narrator status` - Exibe o status atual do mod, sequência e caminho do bridge.
- `/narrator reload` - Recarrega as configurações de `config/ai-narrator.json`.

## Configuração

O arquivo de configuração é gerado em `.minecraft/config/ai-narrator.json`:
- `bridge_path`: Diretório onde os arquivos IPC (`state.json`, `window.json`, etc.) são trocados (padrão: `~/ai-narrator`).
- `heartbeat_interval_ms`: Frequência do sinal de vida (padrão: 5000ms).
- `trigger_poll_ms`: Intervalo de verificação de `trigger.flag` (padrão: 1000ms).