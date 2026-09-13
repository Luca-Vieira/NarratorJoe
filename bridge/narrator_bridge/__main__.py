import sys
from loguru import logger
from .config import Config
from .logging_setup import setup_logging
from .loop import MainLoop

def main():
    try:
        config = Config.load()
    except Exception as e:
        print(f"Erro carregando configuração: {e}", file=sys.stderr)
        sys.exit(1)

    setup_logging(config.logging, config.bridge.path)
    logger.info("AI Narrator Bridge inicializado")
    logger.info(f"Bridge path: {config.bridge.path}")
    logger.info(f"LLM Provider: {config.llm.provider} (Modelo: {config.llm.model})")
    logger.info(f"Intervalo de ciclo: {config.bridge.cycle_interval_s}s | Poll: {config.bridge.poll_interval_s}s")

    loop = MainLoop(config)
    loop.run()

if __name__ == "__main__":
    main()