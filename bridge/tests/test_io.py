import pytest
from pathlib import Path
from pydantic import BaseModel
from narrator_bridge import io

class SampleModel(BaseModel):
    name: str
    count: int

def test_atomic_write_and_read(tmp_path: Path):
    target = tmp_path / "sample.json"
    data = SampleModel(name="test", count=10)
    io.write_json_atomic(target, data)

    assert target.exists()
    loaded = io.read_json_atomic(target, SampleModel)
    assert loaded is not None
    assert loaded.name == "test"
    assert loaded.count == 10

def test_text_atomic_and_append(tmp_path: Path):
    target = tmp_path / "log.txt"
    io.write_text_atomic(target, "line 1\n")
    io.append_text(target, "line 2\n")

    content = target.read_text(encoding="utf-8")
    assert content == "line 1\nline 2\n"

def test_delete_and_touch(tmp_path: Path):
    flag = tmp_path / "trigger.flag"
    assert not io.delete_if_exists(flag)

    io.touch(flag)
    assert flag.exists()

    with pytest.raises(FileExistsError):
        io.touch(flag)

    assert io.delete_if_exists(flag)
    assert not flag.exists()