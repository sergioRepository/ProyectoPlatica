"""Compras con Google Play: validación en el servidor y notificaciones en tiempo real (RTDN).

Ningún beneficio se da por lo que diga el teléfono: todo sale de la Google Play Developer API.
Referencias (consultadas el 2026-11-11):
- https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptionsv2
- https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.products
- https://developer.android.com/google/play/billing/rtdn-reference
- https://cloud.google.com/pubsub/docs/authenticate-push-subscriptions
"""

import asyncio
import base64
import binascii
import hashlib
import json
import logging
from datetime import UTC, date, datetime
from typing import Any, Literal, Protocol
from urllib.parse import quote

import httpx
from google.auth.transport.requests import Request as GoogleRequest
from google.oauth2 import id_token, service_account

from banano.agent.store import Store
from banano.config import Settings

logger = logging.getLogger(__name__)

API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"
ACKNOWLEDGE_PENDING = "ACKNOWLEDGEMENT_STATE_PENDING"
# Cancelada (sin renovación) sigue pagada hasta vencer: también hay que confirmarla.
ACKNOWLEDGEABLE = (
    "SUBSCRIPTION_STATE_ACTIVE",
    "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
    "SUBSCRIPTION_STATE_CANCELED",
)
# purchases.products: purchaseState 0 comprado, 1 cancelado, 2 pendiente; consumptionState 0 sin
# consumir.
PRODUCT_PURCHASED, PRODUCT_PENDING = 0, 2
NOT_CONSUMED = 0
# voidedPurchaseNotification.productType: 1 suscripción, 2 producto único.
VOIDED_SUBSCRIPTION, VOIDED_ONE_TIME = 1, 2

CreditsOutcome = Literal["granted", "already_granted", "pending"]


class BillingError(Exception):
    """La compra no es válida para esta app (producto o plan desconocido, cancelada)."""


class PlayNotFoundError(Exception):
    """Google no conoce ese token: inventado, de otra app o vencido hace mucho."""


class PlayUnavailableError(Exception):
    """Google no respondió o rechazó la llamada: hay que reintentar después."""


class PlayClient(Protocol):
    async def get_subscription(self, token: str) -> dict[str, Any]: ...

    async def acknowledge_subscription(self, product_id: str, token: str) -> None: ...

    async def get_product(self, product_id: str, token: str) -> dict[str, Any]: ...

    async def consume_product(self, product_id: str, token: str) -> None: ...


class GooglePlayClient:
    """Llama a la Developer API con la cuenta de servicio. La llave no sale de este objeto."""

    def __init__(self, settings: Settings) -> None:
        if settings.google_play_service_account_json is None:
            raise PlayUnavailableError("not_configured")
        info = json.loads(settings.google_play_service_account_json.get_secret_value())
        self._credentials = service_account.Credentials.from_service_account_info(
            info, scopes=[SCOPE]
        )
        self._base = f"{API}/{settings.google_play_package_name}/purchases"
        self._timeout = settings.play_api_timeout_s
        self._lock = asyncio.Lock()

    async def _token(self) -> str:
        async with self._lock:
            if not self._credentials.valid:
                # google-auth refresca de forma síncrona: se saca del bucle de eventos.
                await asyncio.to_thread(self._credentials.refresh, GoogleRequest())
        return self._credentials.token

    async def _call(self, method: str, path: str) -> dict[str, Any]:
        try:
            headers = {"Authorization": f"Bearer {await self._token()}"}
            async with httpx.AsyncClient(timeout=self._timeout) as client:
                response = await client.request(method, f"{self._base}/{path}", headers=headers)
        except Exception as exc:  # red, llave inválida o Google caído
            # Solo el tipo: el mensaje podría traer datos de la llave.
            raise PlayUnavailableError(type(exc).__name__) from exc
        if response.status_code in (400, 404, 410):
            raise PlayNotFoundError(str(response.status_code))
        if response.status_code >= 300:
            raise PlayUnavailableError(str(response.status_code))
        return response.json() if response.content else {}

    async def get_subscription(self, token: str) -> dict[str, Any]:
        return await self._call("GET", f"subscriptionsv2/tokens/{_seg(token)}")

    async def acknowledge_subscription(self, product_id: str, token: str) -> None:
        path = f"subscriptions/{_seg(product_id)}/tokens/{_seg(token)}:acknowledge"
        await self._call("POST", path)

    async def get_product(self, product_id: str, token: str) -> dict[str, Any]:
        return await self._call("GET", f"products/{_seg(product_id)}/tokens/{_seg(token)}")

    async def consume_product(self, product_id: str, token: str) -> None:
        await self._call("POST", f"products/{_seg(product_id)}/tokens/{_seg(token)}:consume")


def _seg(value: str) -> str:
    """Un segmento de la URL, codificado: "/", "?" o ".." no cambian a qué recurso se llama."""
    return quote(value, safe="")


def account_hash(device_id: str) -> str:
    """El `obfuscatedAccountId` que la app manda a Google: nunca el id en claro."""
    return hashlib.sha256(device_id.encode()).hexdigest()


def _parse_time(value: str | None) -> datetime | None:
    if not value:
        return None
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def entitlement_from(
    subscription: dict[str, Any], token: str, device_id: str, settings: Settings, day: date
) -> dict[str, Any]:
    """Convierte la respuesta de subscriptionsv2.get en la fila de `entitlements`."""
    items = subscription.get("lineItems") or []
    ours = [i for i in items if i.get("productId") == settings.play_subscription_id]
    if not ours:
        raise BillingError("producto desconocido")
    plans = {
        settings.play_base_plan_monthly: "monthly",
        settings.play_base_plan_semester: "semester",
    }
    item = max(ours, key=lambda i: i.get("expiryTime") or "")
    plan = plans.get((item.get("offerDetails") or {}).get("basePlanId", ""))
    if plan is None:
        raise BillingError("plan desconocido")
    ids = subscription.get("externalAccountIdentifiers") or {}
    return {
        "purchase_token": token,
        "device_id": device_id,
        "product_id": settings.play_subscription_id,
        "plan": plan,
        "state": subscription.get("subscriptionState", "SUBSCRIPTION_STATE_UNSPECIFIED"),
        "expiry_time": _parse_time(item.get("expiryTime")),
        "auto_renewing": bool((item.get("autoRenewingPlan") or {}).get("autoRenewEnabled")),
        "linked_purchase_token": subscription.get("linkedPurchaseToken"),
        "account_hash": ids.get("obfuscatedExternalAccountId"),
        "day": day,
    }


async def verify_subscription(
    store: Store, client: PlayClient, settings: Settings, device_id: str, token: str, day: date
) -> dict[str, Any]:
    """Valida la suscripción con Google, la liga a esta instalación y hace el acknowledge.

    1 suscripción = 1 instalación activa: si estaba ligada a otra (reinstalación), se re-liga y
    el saldo de créditos se traslada. No se exige que el `obfuscatedAccountId` coincida, porque
    una reinstalación cambia el id; el token solo lo conoce la cuenta de Google que compró.
    """
    subscription = await client.get_subscription(token)
    entitlement = entitlement_from(subscription, token, device_id, settings, day)
    await store.save_entitlement(entitlement)
    # Sin acknowledge en 3 días Google reembolsa y revoca: se hace aquí, después de guardar.
    await _acknowledge(client, settings, subscription, entitlement, token)
    return entitlement


async def _acknowledge(
    client: PlayClient,
    settings: Settings,
    subscription: dict[str, Any],
    entitlement: dict[str, Any],
    token: str,
) -> None:
    """Confirma una compra pagada y sin vencer. El derecho ya está guardado: un 4xx (confirmada
    por otra validación simultánea) no lo deshace; un 5xx sube para que se reintente."""
    expiry = entitlement["expiry_time"]
    if (
        subscription.get("acknowledgementState") != ACKNOWLEDGE_PENDING
        or entitlement["state"] not in ACKNOWLEDGEABLE
        or (expiry is not None and expiry <= datetime.now(UTC))
    ):
        return
    try:
        await client.acknowledge_subscription(settings.play_subscription_id, token)
    except PlayNotFoundError:
        logger.warning("acknowledge rechazado por Google con el derecho ya guardado")


async def verify_credits(
    store: Store,
    client: PlayClient,
    settings: Settings,
    device_id: str,
    product_id: str,
    token: str,
    day: date,
) -> CreditsOutcome:
    """Valida un paquete de créditos, lo suma una sola vez por token y lo consume en Google."""
    if product_id != settings.play_credits_product_id:
        raise BillingError("producto desconocido")
    purchase = await client.get_product(product_id, token)
    state = purchase.get("purchaseState")
    if state == PRODUCT_PENDING:
        return "pending"
    if state != PRODUCT_PURCHASED:
        raise BillingError("compra cancelada")
    quantity = max(int(purchase.get("quantity") or 1), 1)
    granted = await store.grant_credits(
        device_id, token, settings.credits_per_pack * quantity, day
    )
    # Se consume después de anotar el saldo: si el consumo falla, la próxima restauración lo
    # reintenta y el registro no vuelve a sumar.
    if purchase.get("consumptionState") == NOT_CONSUMED:
        try:
            await client.consume_product(product_id, token)
        except PlayNotFoundError:
            # Ya consumida por otra validación simultánea (al volver del flujo y al restaurar).
            logger.warning("consumo rechazado por Google con los créditos ya anotados")
    return "granted" if granted else "already_granted"


def verify_push(authorization: str | None, settings: Settings) -> dict[str, Any]:
    """Comprueba el JWT que firma Pub/Sub: firma de Google, audiencia, emisor y cuenta de
    servicio. Lanza ValueError si algo no cuadra. Es síncrona: se llama con to_thread."""
    if not settings.rtdn_audience or not settings.rtdn_service_account_email:
        raise ValueError("not_configured")
    scheme, _, token = (authorization or "").partition(" ")
    if scheme.lower() != "bearer" or not token:
        raise ValueError("missing_token")
    # verify_oauth2_token comprueba la firma, la caducidad, la audiencia y que el emisor sea
    # accounts.google.com.
    claims = id_token.verify_oauth2_token(
        token.strip(), GoogleRequest(), audience=settings.rtdn_audience
    )
    if claims.get("email") != settings.rtdn_service_account_email or not claims.get(
        "email_verified"
    ):
        raise ValueError("wrong_sender")
    return claims


def decode_notification(envelope: dict[str, Any]) -> dict[str, Any]:
    """Saca el DeveloperNotification del sobre de Pub/Sub (`message.data` en base64)."""
    try:
        data = envelope["message"]["data"]
        return json.loads(base64.b64decode(data))
    except (KeyError, TypeError, ValueError, binascii.Error) as exc:
        raise ValueError("malformed") from exc


async def handle_notification(
    notification: dict[str, Any], store: Store, client: PlayClient, settings: Settings, day: date
) -> str:
    """Aplica una notificación. Nunca confía en su contenido: vuelve a leer la API.

    Solo se actualizan compras que ya validó alguna instalación: una notificación no sabe a qué
    instalación pertenece la compra (sin cuentas), y la app la restaura al abrirse.
    """
    if notification.get("packageName") != settings.google_play_package_name:
        return "ignored_package"
    if sub := notification.get("subscriptionNotification"):
        return await _refresh_subscription(sub.get("purchaseToken"), store, client, settings, day)
    if voided := notification.get("voidedPurchaseNotification"):
        token = voided.get("purchaseToken")
        if voided.get("productType") == VOIDED_ONE_TIME and token:
            refunded = await store.refund_credits(token, day)
            return "refunded" if refunded else "unknown_purchase"
        if voided.get("productType") == VOIDED_SUBSCRIPTION:
            return await _refresh_subscription(token, store, client, settings, day)
    # Productos únicos, pruebas y lo que no se reconozca: la app lo valida al abrirse.
    return "ignored"


async def _refresh_subscription(
    token: str | None, store: Store, client: PlayClient, settings: Settings, day: date
) -> str:
    if not token:
        return "ignored"
    known = await store.find_entitlement(token)
    if known is None:
        return "unknown_purchase"
    try:
        subscription = await client.get_subscription(token)
    except PlayNotFoundError:
        logger.warning("RTDN de un token que Google ya no reconoce")
        return "unknown_purchase"
    entitlement = entitlement_from(subscription, token, known["device_id"], settings, day)
    await store.save_entitlement(entitlement)
    # Un pago pendiente que se cobra con la app cerrada solo llega por aquí.
    await _acknowledge(client, settings, subscription, entitlement, token)
    return "updated"
