"""Crea las tablas del agente en la base de DATABASE_URL (backend/.env o entorno).

Uso, desde la raíz del repo:
    uv --directory backend run python -m scripts.init_db

Es idempotente: correrlo otra vez no borra nada. Nunca imprime la URL (lleva la clave).
"""

import sys
from pathlib import Path

import psycopg

from banano.config import Settings

SQL_DIR = Path(__file__).resolve().parent.parent / "sql"


def main() -> int:
    settings = Settings()
    if settings.database_url is None:
        print("Falta DATABASE_URL.")
        return 2
    with psycopg.connect(settings.database_url.get_secret_value(), connect_timeout=10) as conn:
        for path in sorted(SQL_DIR.glob("*.sql")):
            conn.execute(path.read_text(encoding="utf-8"))
            print(f"  aplicado {path.name}")
    print("Listo.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
