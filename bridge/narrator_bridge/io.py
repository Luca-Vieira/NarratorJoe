import os
import json
from pathlib import Path
from typing import Optional, Type, TypeVar, Any
from pydantic import BaseModel, ValidationError
from loguru import logger

T = TypeVar("T", bound=BaseModel)

def read_json_atomic(path: Path, model: Type[T]) -> Optional[T]:
    try:
        if not path.exists():
            return None
        text = path.read_text(encoding="utf-8")
        return model.model_validate_json(text)
    except ValidationError as e:
        logger.warning(f"JSON validation failed for {path}: {e}")
        return None
    except Exception as e:
        logger.error(f"Error reading {path}: {e}")
        return None

def write_json_atomic(path: Path, data: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    if isinstance(data, BaseModel):
        text = data.model_dump_json(indent=2)
    elif isinstance(data, str):
        text = data
    else:
        text = json.dumps(data, indent=2, ensure_ascii=False)

    with open(tmp, "w", encoding="utf-8") as f:
        f.write(text)
        f.flush()
        try:
            os.fsync(f.fileno())
        except OSError:
            pass
    os.replace(tmp, path)

def write_text_atomic(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    with open(tmp, "w", encoding="utf-8") as f:
        f.write(text)
        f.flush()
        try:
            os.fsync(f.fileno())
        except OSError:
            pass
    os.replace(tmp, path)

def append_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "a", encoding="utf-8") as f:
        f.write(text)
        f.flush()
        os.fsync(f.fileno())

def delete_if_exists(path: Path) -> bool:
    try:
        path.unlink()
        return True
    except FileNotFoundError:
        return False
    except Exception as e:
        logger.error(f"Error deleting {path}: {e}")
        return False

def touch(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.touch(exist_ok=False)