"""Tokens de dispositivo firmados con HMAC.

Vercel no tiene disco persistente, así que el backend no guarda tokens: el token lleva el
id del dispositivo y una firma con DEVICE_TOKEN_SECRET. Cambiar el secreto invalida todos
los tokens y obliga a registrar de nuevo el teléfono.
"""

import base64
import hashlib
import hmac
import re

TOKEN_VERSION = "v1"
DEVICE_ID_PATTERN = r"^[A-Za-z0-9_-]{16,64}$"
_DEVICE_ID_RE = re.compile(r"[A-Za-z0-9_-]{16,64}")


def _signature(device_id: str, secret: str) -> str:
    digest = hmac.new(
        secret.encode(), f"{TOKEN_VERSION}:{device_id}".encode(), hashlib.sha256
    ).digest()
    return base64.urlsafe_b64encode(digest).rstrip(b"=").decode()


def sign_device_token(device_id: str, secret: str) -> str:
    return f"{TOKEN_VERSION}.{device_id}.{_signature(device_id, secret)}"


def verify_device_token(token: str, secret: str) -> str | None:
    """Devuelve el id del dispositivo si la firma es válida."""
    parts = token.split(".")
    if len(parts) != 3 or parts[0] != TOKEN_VERSION or not _DEVICE_ID_RE.fullmatch(parts[1]):
        return None
    device_id = parts[1]
    if hmac.compare_digest(parts[2], _signature(device_id, secret)):
        return device_id
    return None
