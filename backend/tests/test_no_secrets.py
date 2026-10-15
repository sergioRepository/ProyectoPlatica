"""Criterio de aceptación: la llave del gateway nunca aparece en el repo.

Revisa los archivos versionados y los que git agregaría (no ignorados). backend/.env está
ignorado y por eso no se revisa: es el único lugar local donde vive la llave.
"""

import re
import shutil
import subprocess
from pathlib import Path

import pytest

REPO_ROOT = Path(__file__).resolve().parents[2]
# Una llave real: pk_ seguido de muchos caracteres. "pk_..." de .env.example no cuenta.
KEY_RE = re.compile(rb"pk_[A-Za-z0-9_-]{16,}")


def candidate_files() -> list[Path]:
    if shutil.which("git") is None or not (REPO_ROOT / ".git").exists():
        pytest.skip("Sin git o fuera del repo (por ejemplo, dentro de Docker)")
    listing = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
        cwd=REPO_ROOT,
        capture_output=True,
        check=True,
    ).stdout
    return [REPO_ROOT / name for name in listing.decode().split("\0") if name]


def test_no_gateway_key_in_the_repo() -> None:
    leaks = [
        path.relative_to(REPO_ROOT).as_posix()
        for path in candidate_files()
        if path.is_file() and KEY_RE.search(path.read_bytes())
    ]
    assert leaks == [], f"Posible llave del gateway en: {leaks}"
