"""Prueba contra Grok real a través del gateway del reto.

Uso, desde la raíz del repo:
    uv --directory backend run python -m scripts.smoke_grok
    uv --directory backend run python -m scripts.smoke_grok --solo conectividad
    uv --directory backend run python -m scripts.smoke_grok --api http://localhost:8000

Con --api prueba de punta a punta un backend ya corriendo (Docker o Vercel): registra un
dispositivo con el ENROLLMENT_CODE del .env y pide dos veredictos por HTTP.

Lee backend/.env y nunca imprime la llave. Gasta tokens reales del gateway.

La prueba de conectividad equivale al curl del spec (bash), con el modelo del .env:
    curl "$XAI_BASE_URL/chat/completions" \\
      -H "Authorization: Bearer $XAI_API_KEY" \\
      -H "Content-Type: application/json" \\
      -d '{"model":"<GROK_MODEL_GENERATE>","messages":[{"role":"user","content":"hola"}]}'
"""

import argparse
import asyncio
import secrets
import sys
import time
from collections import Counter

import httpx
from langchain_core.messages import HumanMessage, SystemMessage
from langchain_xai import ChatXAI
from pydantic import BaseModel

from banano.config import Settings
from banano.graphs.judge_graph import JudgeUnavailableError, run_judge_graph
from banano.graphs.template_graph import run_template_graph
from banano.llm.client import build_generate_llm, build_judge_llm, json_instructions
from banano.safety.text import is_clean
from banano.schemas.judge import JudgeRequest
from banano.schemas.profile import Profile

PHONE_TIMEOUT_S = 8.0
VERCEL_LIMIT_S = 300.0
METHODS = ("json_schema", "function_calling", "json_mode")

SAMPLE_PROFILE = Profile(
    name="Alex",
    main_goal="ser el mejor programador del mundo",
    secondary_goals=["leer un libro al mes", "dormir antes de medianoche"],
    goal_reason="Quiero vivir de construir software que use la gente",
    blocked_apps=["Instagram", "TikTok", "YouTube"],
    schedule_summary="lunes a viernes de 08:00 a 18:00 y todas las noches después de las 23:00",
    typical_excuses="solo cinco minutos; es para descansar la cabeza; ya casi termino",
    sarcasm_level=4,
    profanity_allowed=True,
    country="Colombia",
    forbidden_topics="mi familia",
)

_JUDGE_BASE = {
    "app": "Instagram",
    "profile_summary": "Quiere ser el mejor programador del mundo y se dice que solo son cinco "
    "minutos. Pidió sarcasmo fuerte.",
    "attempts_today": 6,
    "extra_time_granted_today": 1,
    "local_time": "23:40",
    "sarcasm_level": 4,
    "profanity_allowed": True,
}
JUDGE_CASES = [
    ("aburrimiento", {"type": "extra_time", "reason": "Quiero ver memes un rato, estoy aburrido"}),
    (
        "trabajo",
        {
            "type": "extra_time",
            "reason": "Tengo que responderle antes de las 5 al cliente que me escribió por IG",
        },
    ),
    ("crisis corta", {"type": "emergency", "reason": "accidente"}),
    (
        "repetida",
        {
            "type": "extra_time",
            "reason": "Necesito revisar algo del trabajo urgente",
            "recent_excuses": ["Necesito revisar algo del trabajo urgente"],
        },
    ),
]


class Probe(BaseModel):
    saludo: str
    numero: int


def masked_key(settings: Settings) -> str:
    if settings.xai_api_key is None:
        return "(falta)"
    key = settings.xai_api_key.get_secret_value()
    prefix = "pk_" if key.startswith("pk_") else ""
    return f"{prefix}... ({len(key)} caracteres)"


def describe(settings: Settings) -> list[str]:
    print("Configuración (backend/.env)")
    print(f"  XAI_BASE_URL           {settings.xai_base_url or '(falta)'}")
    print(f"  XAI_API_KEY            {masked_key(settings)}")
    print(f"  GROK_MODEL_GENERATE    {settings.grok_model_generate or '(falta)'}")
    print(f"  GROK_MODEL_JUDGE       {settings.grok_model_judge or '(falta)'}")
    print(f"  LLM_STRUCTURED_METHOD  {settings.llm_structured_method}")
    required = {
        "XAI_BASE_URL": settings.xai_base_url,
        "XAI_API_KEY": settings.xai_api_key,
        "GROK_MODEL_GENERATE": settings.grok_model_generate,
        "GROK_MODEL_JUDGE": settings.grok_model_judge,
    }
    return [name for name, value in required.items() if not value]


async def check_connectivity(settings: Settings) -> bool:
    print("\n1. Conectividad: POST /chat/completions con 'hola'")
    assert settings.xai_base_url and settings.xai_api_key
    url = f"{settings.xai_base_url.rstrip('/')}/chat/completions"
    headers = {
        "Authorization": f"Bearer {settings.xai_api_key.get_secret_value()}",
        "Content-Type": "application/json",
    }
    ok = True
    for model in dict.fromkeys([settings.grok_model_generate, settings.grok_model_judge]):
        start = time.perf_counter()
        async with httpx.AsyncClient(timeout=60) as client:
            response = await client.post(
                url,
                headers=headers,
                json={"model": model, "messages": [{"role": "user", "content": "hola"}]},
            )
        elapsed = time.perf_counter() - start
        if response.status_code != 200:
            ok = False
            print(f"  {model}: HTTP {response.status_code} en {elapsed:.2f} s")
            print(f"  {response.text[:300]}")
            continue
        data = response.json()
        content = data["choices"][0]["message"].get("content") or ""
        usage = data.get("usage") or {}
        reasoning = (usage.get("completion_tokens_details") or {}).get("reasoning_tokens")
        print(f"  {model}: HTTP 200 en {elapsed:.2f} s (la respuesta dice: {data.get('model')})")
        print(f"  respuesta: {content.strip()[:120]!r}")
        print(
            f"  tokens: {usage.get('prompt_tokens')} de entrada, "
            f"{usage.get('completion_tokens')} de salida, {reasoning} de razonamiento"
        )
    return ok


async def list_models(settings: Settings) -> None:
    print("\n0. Modelos que ofrece el gateway (GET /models)")
    assert settings.xai_base_url and settings.xai_api_key
    async with httpx.AsyncClient(timeout=30) as client:
        response = await client.get(
            f"{settings.xai_base_url.rstrip('/')}/models",
            headers={"Authorization": f"Bearer {settings.xai_api_key.get_secret_value()}"},
        )
    if response.status_code != 200:
        print(f"  HTTP {response.status_code}: {response.text[:200]}")
        return
    for model in sorted(item.get("id", "?") for item in response.json().get("data", [])):
        print(f"  {model}")


async def check_structured(settings: Settings) -> list[str]:
    print(f"\n2. Salida estructurada con {settings.grok_model_judge}")
    assert settings.xai_api_key
    working: list[str] = []
    for method in METHODS:
        chat = ChatXAI(
            model=settings.grok_model_judge,
            api_key=settings.xai_api_key.get_secret_value(),
            base_url=settings.xai_base_url,
            timeout=60,
            max_retries=0,
        )
        system = "Saluda en dos palabras y pon el número 7."
        if method == "json_mode":
            system = f"{system}\n\n{json_instructions(Probe)}"
        start = time.perf_counter()
        try:
            result = await chat.with_structured_output(Probe, method=method).ainvoke(
                [SystemMessage(system), HumanMessage("Hola")]
            )
            probe = result if isinstance(result, Probe) else Probe.model_validate(result)
            print(f"  {method:<17} OK en {time.perf_counter() - start:.2f} s -> {probe}")
            working.append(method)
        except Exception as exc:
            print(f"  {method:<17} FALLA ({type(exc).__name__}): {str(exc)[:160]}")
    if working:
        print(f"  Recomendado: LLM_STRUCTURED_METHOD={working[0]}")
    else:
        print("  Ningún método nativo funciona: el respaldo en texto con Pydantic hará el trabajo.")
    return working


async def check_template(settings: Settings) -> None:
    print(f"\n3. Grafo de la plantilla con {settings.grok_model_generate} (puede tardar)")
    start = time.perf_counter()
    result = await run_template_graph(SAMPLE_PROFILE, build_generate_llm(settings), settings)
    elapsed = time.perf_counter() - start
    margin = "dentro" if elapsed < VERCEL_LIMIT_S else "FUERA"
    print(f"  {len(result.messages)} mensajes en {elapsed:.1f} s ({margin} del límite de Vercel)")
    print(f"  contextos: {dict(Counter(m.context for m in result.messages))}")
    print(f"  intensidad: {dict(sorted(Counter(m.intensity for m in result.messages).items()))}")
    for message in result.messages[:: max(1, len(result.messages) // 8)][:8]:
        print(f"  [{message.context}/{message.intensity}] {message.text}")
    print(
        f"  veredictos: {len(result.verdict_lines.approved)} aprobados, "
        f"{len(result.verdict_lines.rejected)} rechazados"
    )
    print(f"  resumen para el juez: {result.profile_summary}")
    texts = [m.text for m in result.messages] + result.verdict_lines.approved
    texts += result.verdict_lines.rejected + [result.profile_summary]
    print(f"  sin emojis, emoticonos ni hashtags: {'sí' if all(map(is_clean, texts)) else 'NO'}")


async def check_judge(settings: Settings) -> None:
    print(f"\n4. Juez con {settings.grok_model_judge} (el teléfono espera {PHONE_TIMEOUT_S:.0f} s)")
    llm = build_judge_llm(settings)
    for label, overrides in JUDGE_CASES:
        request = JudgeRequest(**{**_JUDGE_BASE, **overrides})
        start = time.perf_counter()
        try:
            verdict = await run_judge_graph(request, llm, settings)
        except JudgeUnavailableError:
            elapsed = time.perf_counter() - start
            print(f"  {label:<13} {elapsed:5.2f} s  no respondió: la app usaría la regla offline")
            continue
        elapsed = time.perf_counter() - start
        state = "APROBADO" if verdict.valid else "RECHAZADO"
        speed = "" if elapsed < PHONE_TIMEOUT_S else "  LENTO"
        print(
            f"  {label:<13} {elapsed:5.2f} s  {state} · {verdict.reason_code} · "
            f"{verdict.granted_minutes} min{speed}"
        )
        print(f"                 {verdict.message}")


async def check_api(settings: Settings, base_url: str) -> bool:
    print(f"\nAPI en {base_url}")
    if settings.enrollment_code is None:
        print("  Falta ENROLLMENT_CODE en backend/.env (debe ser el mismo del backend).")
        return False
    base = base_url.rstrip("/")
    device_id = f"smoke-{secrets.token_hex(8)}"
    async with httpx.AsyncClient(timeout=30) as client:
        health = await client.get(f"{base}/health")
        print(f"  /health             HTTP {health.status_code} {health.text[:80]}")
        register = await client.post(
            f"{base}/devices/register",
            json={
                "device_id": device_id,
                "enrollment_code": settings.enrollment_code.get_secret_value(),
            },
        )
        if register.status_code != 200:
            print(f"  /devices/register   HTTP {register.status_code} {register.text[:200]}")
            return False
        print("  /devices/register   HTTP 200, token recibido")
        headers = {"Authorization": f"Bearer {register.json()['device_token']}"}
        for label, overrides in (
            ("repetida", JUDGE_CASES[3][1]),
            ("aburrimiento", JUDGE_CASES[0][1]),
        ):
            start = time.perf_counter()
            response = await client.post(
                f"{base}/excuses/judge", json={**_JUDGE_BASE, **overrides}, headers=headers
            )
            elapsed = time.perf_counter() - start
            print(f"  /excuses/judge      {label}: HTTP {response.status_code} en {elapsed:.2f} s")
            print(f"                      {response.text[:220]}")
    return True


async def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Prueba contra Grok real.")
    parser.add_argument(
        "--solo", choices=["modelos", "conectividad", "estructurada", "plantilla", "juez"]
    )
    parser.add_argument("--api", metavar="URL", help="prueba solo la API ya desplegada en URL")
    args = parser.parse_args(argv)

    settings = Settings()
    if args.api:
        return 0 if await check_api(settings, args.api) else 1
    missing = describe(settings)
    if missing:
        print(f"\nFaltan variables en backend/.env: {', '.join(missing)}")
        return 2

    run_all = args.solo is None
    if run_all or args.solo == "modelos":
        await list_models(settings)
    if (run_all or args.solo == "conectividad") and not await check_connectivity(settings):
        return 1
    if run_all or args.solo == "estructurada":
        await check_structured(settings)
    if run_all or args.solo == "plantilla":
        await check_template(settings)
    if run_all or args.solo == "juez":
        await check_judge(settings)
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    sys.exit(asyncio.run(main()))
