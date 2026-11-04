"""Corre las consultas de backend/sql/queries y escribe un resumen en Markdown.

Uso, desde la raíz del repo:
    uv --directory backend run python -m scripts.usage_report --salida reporte-de-uso.md

Solo lee: la conexión es de solo lectura. Nunca imprime la URL (lleva la clave).
"""

import argparse
import sys
from collections.abc import Callable
from datetime import date
from pathlib import Path
from typing import Any

import psycopg
from psycopg.rows import dict_row

from banano.config import Settings

QUERIES_DIR = Path(__file__).resolve().parent.parent / "sql" / "queries"


def title_of(sql: str) -> str:
    return sql.splitlines()[0].removeprefix("--").strip()


def cell(value: Any) -> str:
    if value is None:
        return "-"
    return str(value).replace("|", "\\|")


def table(rows: list[dict[str, Any]]) -> str:
    if not rows:
        return "Sin datos."
    columns = list(rows[0])
    lines = [
        "| " + " | ".join(columns) + " |",
        "|" + " --- |" * len(columns),
        *("| " + " | ".join(cell(row[column]) for column in columns) + " |" for row in rows),
    ]
    return "\n".join(lines)


def render(run: Callable[[str], list[dict[str, Any]]], today: date) -> str:
    """`run` ejecuta una consulta y devuelve sus filas."""
    sections = [f"# Reporte de uso\n\nGenerado el {today.isoformat()}."]
    for path in sorted(QUERIES_DIR.glob("*.sql")):
        sql = path.read_text(encoding="utf-8")
        sections.append(f"## {title_of(sql)}\n\n{table(run(sql))}")
    return "\n\n".join(sections) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--salida", type=Path, default=Path("reporte-de-uso.md"))
    args = parser.parse_args()
    settings = Settings()
    if settings.database_url is None:
        print("Falta DATABASE_URL.")
        return 2
    url = settings.database_url.get_secret_value()
    with psycopg.connect(url, connect_timeout=10, row_factory=dict_row) as conn:
        conn.read_only = True
        report = render(lambda sql: conn.execute(sql).fetchall(), date.today())
    args.salida.write_text(report, encoding="utf-8")
    print(f"Listo: {args.salida}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
