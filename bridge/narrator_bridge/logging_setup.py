import sys
from pathlib import Path
from loguru import logger
from .config import LoggingConfig

def setup_logging(config: LoggingConfig, bridge_path: Path):
    logger.remove()

    logger.add(
        sys.stderr,
        level=config.level,
        format="<green>{time:YYYY-MM-DD HH:mm:ss.SSS}</green> | <level>{level: <8}</level> | <cyan>{name}</cyan> - <level>{message}</level>",
        colorize=True,
    )

    log_file = bridge_path / "bridge.log"
    bridge_path.mkdir(parents=True, exist_ok=True)

    logger.add(
        str(log_file),
        level=config.level,
        rotation=config.rotation,
        retention=config.retention,
        format="{time:YYYY-MM-DDTHH:mm:ss.SSSZ} | {level: <8} | {name} | {message}",
        encoding="utf-8",
        enqueue=True,
    )