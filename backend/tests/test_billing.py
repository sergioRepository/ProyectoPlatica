"""Facturación: validación con Google simulada, créditos, cobro del acceso y RTDN.

Ningún test llama a Google: FakePlay responde lo que le digamos.
"""

import base64
import json
from datetime import UTC, datetime, timedelta
from typing import Any
from uuid import uuid4

import pytest

from banano import billing
from banano.billing import PlayNotFoundError, PlayUnavailableError, account_hash
from banano.graphs.judge_graph import JudgeEvaluation
from tests.fakes import (
    TEST_DEVICE_ID,
    FakeLLM,
    auth_headers,
    consented_store,
    evaluation,
    judge_request,
    make_settings,
    seed_entitlement,
)

SUB = "token-suscripcion-0001"
PACK = "token-creditos-0001"
OTHER_DEVICE = "dispositivo-de-prueba-02"


def subscription(
    state: str = "SUBSCRIPTION_STATE_ACTIVE",
    base_plan: str = "monthly",
    days: int = 30,
    acknowledged: bool = False,
    linked: str | None = None,
) -> dict[str, Any]:
    expiry = (datetime.now(UTC) + timedelta(days=days)).isoformat().replace("+00:00", "Z")
    body: dict[str, Any] = {
        "subscriptionState": state,
        "acknowledgementState": "ACKNOWLEDGEMENT_STATE_"
        + ("ACKNOWLEDGED" if acknowledged else "PENDING"),
        "lineItems": [
            {
                "productId": "premium",
                "expiryTime": expiry,
                "offerDetails": {"basePlanId": base_plan},
                "autoRenewingPlan": {"autoRenewEnabled": state == "SUBSCRIPTION_STATE_ACTIVE"},
            }
        ],
        "externalAccountIdentifiers": {"obfuscatedExternalAccountId": "hash"},
    }
    if linked:
        body["linkedPurchaseToken"] = linked
    return body


class FakePlay:
    def __init__(self) -> None:
        self.subscriptions: dict[str, Any] = {}
        self.products: dict[str, Any] = {}
        self.acknowledged: list[str] = []
        self.consumed: list[str] = []
        self.fail_next: BaseException | None = None  # lo lanza el próximo acknowledge o consume

    def _maybe_fail(self) -> None:
        if self.fail_next is not None:
            error, self.fail_next = self.fail_next, None
            raise error

    @staticmethod
    def _answer(value: Any) -> Any:
        if value is None:
            raise PlayNotFoundError("404")
        if isinstance(value, BaseException):
            raise value
        return value

    async def get_subscription(self, token: str) -> dict[str, Any]:
        return self._answer(self.subscriptions.get(token))

    async def acknowledge_subscription(self, product_id: str, token: str) -> None:
        self._maybe_fail()
        self.acknowledged.append(token)

    async def get_product(self, product_id: str, token: str) -> dict[str, Any]:
        return self._answer(self.products.get(token))

    async def consume_product(self, product_id: str, token: str) -> None:
        self._maybe_fail()
        self.consumed.append(token)


def verify(client, product_id: str, token: str, device_id: str = TEST_DEVICE_ID):
    return client.post(
        "/billing/verify",
        json={"product_id": product_id, "purchase_token": token},
        headers=auth_headers(device_id),
    )


# Validación de suscripciones


def test_a_valid_subscription_gives_the_plan_and_is_acknowledged_once(make_client):
    store, play = consented_store(credits=0), FakePlay()
    play.subscriptions[SUB] = subscription(base_plan="semester")
    client = make_client(store=store, play_client=play)

    first = verify(client, "premium", SUB)
    assert first.status_code == 200
    body = first.json()
    assert body["outcome"] == "subscription"
    assert body["device"]["plan"] == "semester"
    assert body["device"]["daily_limit"] == 20
    assert body["device"]["included_remaining"] == 3
    assert body["device"]["agent_enabled"] is True
    assert play.acknowledged == [SUB]

    play.subscriptions[SUB] = subscription(base_plan="semester", acknowledged=True)
    assert verify(client, "premium", SUB).status_code == 200
    assert play.acknowledged == [SUB]


@pytest.mark.parametrize(
    ("state", "days", "plan"),
    [
        ("SUBSCRIPTION_STATE_IN_GRACE_PERIOD", 3, "monthly"),
        ("SUBSCRIPTION_STATE_CANCELED", 5, "monthly"),
        ("SUBSCRIPTION_STATE_CANCELED", -1, "free"),
        ("SUBSCRIPTION_STATE_ON_HOLD", 5, "free"),
        ("SUBSCRIPTION_STATE_PAUSED", 5, "free"),
        ("SUBSCRIPTION_STATE_EXPIRED", -1, "free"),
        ("SUBSCRIPTION_STATE_PENDING", 5, "free"),
    ],
)
def test_each_google_state_maps_to_its_benefits(make_client, state, days, plan):
    store, play = consented_store(), FakePlay()
    play.subscriptions[SUB] = subscription(state=state, days=days)
    response = verify(make_client(store=store, play_client=play), "premium", SUB)
    assert response.status_code == 200
    assert response.json()["device"]["plan"] == plan
    assert response.json()["device"]["subscription_state"] == state
    # Solo se hace el acknowledge de lo que da acceso.
    assert play.acknowledged == ([SUB] if plan != "free" else [])


def test_an_active_subscription_that_expired_without_news_gives_nothing(make_client):
    # Si no llega la notificación de Google, el plan no dura para siempre.
    store = consented_store()
    seed_entitlement(store, TEST_DEVICE_ID, expires_in=timedelta(hours=-2))
    client = make_client(store=store)
    assert client.get("/devices/me", headers=auth_headers()).json()["plan"] == "free"


def test_reinstalling_does_not_renew_the_included_accesses(make_client):
    store, play = consented_store(TEST_DEVICE_ID, OTHER_DEVICE, credits=0), FakePlay()
    play.subscriptions[SUB] = subscription()
    client = make_client(
        store=store, play_client=play, judge_llm=FakeLLM({JudgeEvaluation: evaluation()})
    )
    verify(client, "premium", SUB)
    for _ in range(3):
        assert judge(client).json()["access_source"] == "included"

    verify(client, "premium", SUB, device_id=OTHER_DEVICE)
    status = client.get("/devices/me", headers=auth_headers(OTHER_DEVICE)).json()

    assert status["plan"] == "monthly"
    assert status["included_remaining"] == 0


def test_the_ledger_never_keeps_tokens_or_ids_in_clear(make_client):
    store, play = consented_store(credits=0), FakePlay()
    play.products[PACK] = {"purchaseState": 0, "consumptionState": 0}
    client = make_client(
        store=store, play_client=play, judge_llm=FakeLLM({JudgeEvaluation: evaluation()})
    )
    verify(client, "creditos_10", PACK)
    judge(client, request_id=str(uuid4()))

    refs = " ".join(r["ref"] for r in store.ledger)
    assert PACK not in refs and TEST_DEVICE_ID not in refs


def test_a_pending_subscription_gives_nothing_yet(make_client):
    store, play = consented_store(), FakePlay()
    play.subscriptions[SUB] = subscription(state="SUBSCRIPTION_STATE_PENDING")
    response = verify(make_client(store=store, play_client=play), "premium", SUB)
    assert response.json()["outcome"] == "pending"
    assert response.json()["device"]["plan"] == "free"


def test_invalid_purchases_never_give_benefits(make_client):
    store, play = consented_store(), FakePlay()
    play.subscriptions["otro-plan"] = subscription(base_plan="anual")
    client = make_client(store=store, play_client=play)

    assert verify(client, "premium", "no-existe-0001").status_code == 404
    assert verify(client, "premium", "otro-plan").status_code == 422
    assert verify(client, "otro_producto", PACK).status_code == 422
    assert client.get("/devices/me", headers=auth_headers()).json()["plan"] == "free"


def test_google_down_answers_503_and_changes_nothing(make_client):
    store, play = consented_store(), FakePlay()
    play.subscriptions[SUB] = PlayUnavailableError("503")
    response = verify(make_client(store=store, play_client=play), "premium", SUB)
    assert response.status_code == 503
    assert store.entitlements == {}


def test_without_a_service_account_billing_is_off(make_client):
    response = verify(make_client(store=consented_store()), "premium", SUB)
    assert response.status_code == 503


@pytest.mark.parametrize(
    "token", ["x/../" + PACK, "../tokens/" + PACK, PACK + "?x=1", "corto", PACK + "/", ".." + PACK]
)
def test_a_token_that_could_point_elsewhere_is_rejected(make_client, token):
    # El token va en la URL de Google y es la llave del registro: otro texto no puede validar ni
    # canjear dos veces la misma compra.
    store, play = consented_store(credits=0), FakePlay()
    play.products[PACK] = {"purchaseState": 0, "consumptionState": 0}
    response = verify(make_client(store=store, play_client=play), "creditos_10", token)
    assert response.status_code == 422
    assert play.consumed == []


def test_url_segments_are_encoded():
    assert billing._seg("a/../b?c") == "a%2F..%2Fb%3Fc"


def test_verify_needs_the_device_token(make_client):
    client = make_client(store=consented_store(), play_client=FakePlay())
    response = client.post("/billing/verify", json={"product_id": "premium", "purchase_token": SUB})
    assert response.status_code == 401


def test_reinstalling_moves_the_plan_and_the_credits_once(make_client):
    store, play = consented_store(TEST_DEVICE_ID, OTHER_DEVICE, credits=4), FakePlay()
    play.subscriptions[SUB] = subscription()
    client = make_client(store=store, play_client=play)
    assert verify(client, "premium", SUB).status_code == 200

    moved = verify(client, "premium", SUB, device_id=OTHER_DEVICE)
    again = verify(client, "premium", SUB, device_id=OTHER_DEVICE)

    assert moved.json()["device"]["plan"] == "monthly"
    assert again.json()["device"]["credits"] == 8
    old = client.get("/devices/me", headers=auth_headers()).json()
    assert (old["plan"], old["credits"]) == ("free", 0)


def test_a_plan_change_retires_the_replaced_purchase(make_client):
    store, play = consented_store(), FakePlay()
    play.subscriptions[SUB] = subscription(base_plan="monthly")
    play.subscriptions["token-semestral-0001"] = subscription(base_plan="semester", linked=SUB)
    client = make_client(store=store, play_client=play)
    verify(client, "premium", SUB)

    response = verify(client, "premium", "token-semestral-0001")

    assert response.json()["device"]["plan"] == "semester"
    # El aviso de la pantalla habla de la compra vigente, nunca de la reemplazada.
    assert response.json()["device"]["subscription_state"] == "SUBSCRIPTION_STATE_ACTIVE"
    assert store.entitlements[SUB]["state"] == "REPLACED"


def test_the_plan_endpoint_can_no_longer_raise_the_plan(make_client):
    client = make_client(store=consented_store())
    response = client.put(
        "/devices/me/plan", json={"plan": "semester", "would_pay": "yes"}, headers=auth_headers()
    )
    assert response.status_code == 200
    assert response.json()["plan"] == "free"
    assert response.json()["would_pay"] == "yes"


# Créditos


def test_a_credit_pack_is_added_once_and_consumed(make_client):
    store, play = consented_store(credits=0), FakePlay()
    play.products[PACK] = {"purchaseState": 0, "consumptionState": 0}
    client = make_client(store=store, play_client=play)

    first = verify(client, "creditos_10", PACK)
    play.products[PACK] = {"purchaseState": 0, "consumptionState": 1}
    second = verify(client, "creditos_10", PACK)

    assert first.json()["outcome"] == "granted"
    assert second.json()["outcome"] == "already_granted"
    assert second.json()["device"]["credits"] == 10
    assert play.consumed == [PACK]


def test_a_failed_acknowledge_is_retried_on_the_next_verify(make_client):
    store, play = consented_store(), FakePlay()
    play.subscriptions[SUB] = subscription()
    play.fail_next = PlayUnavailableError("503")
    client = make_client(store=store, play_client=play)

    assert verify(client, "premium", SUB).status_code == 503
    retry = verify(client, "premium", SUB)
    assert retry.status_code == 200
    assert retry.json()["device"]["plan"] == "monthly"
    assert play.acknowledged == [SUB]


def test_an_acknowledge_already_done_elsewhere_keeps_the_plan(make_client):
    store, play = consented_store(), FakePlay()
    play.subscriptions[SUB] = subscription()
    play.fail_next = PlayNotFoundError("400")
    response = verify(make_client(store=store, play_client=play), "premium", SUB)
    assert response.status_code == 200
    assert response.json()["device"]["plan"] == "monthly"


def test_a_failed_consume_is_retried_without_adding_twice(make_client):
    store, play = consented_store(credits=0), FakePlay()
    play.products[PACK] = {"purchaseState": 0, "consumptionState": 0}
    play.fail_next = PlayUnavailableError("503")
    client = make_client(store=store, play_client=play)

    assert verify(client, "creditos_10", PACK).status_code == 503
    retry = verify(client, "creditos_10", PACK)
    assert retry.json()["outcome"] == "already_granted"
    assert retry.json()["device"]["credits"] == 10
    assert play.consumed == [PACK]


def test_several_packs_in_one_purchase_add_all_their_credits(make_client):
    store, play = consented_store(credits=0), FakePlay()
    play.products[PACK] = {"purchaseState": 0, "consumptionState": 0, "quantity": 3}
    response = verify(make_client(store=store, play_client=play), "creditos_10", PACK)
    assert response.json()["device"]["credits"] == 30
    assert play.consumed == [PACK]


def test_the_same_pack_token_cannot_feed_two_devices(make_client):
    store, play = consented_store(TEST_DEVICE_ID, OTHER_DEVICE, credits=0), FakePlay()
    play.products[PACK] = {"purchaseState": 0, "consumptionState": 0}
    client = make_client(store=store, play_client=play)
    verify(client, "creditos_10", PACK)

    stolen = verify(client, "creditos_10", PACK, device_id=OTHER_DEVICE)

    assert stolen.json()["outcome"] == "already_granted"
    assert stolen.json()["device"]["credits"] == 0


@pytest.mark.parametrize(("state", "code", "outcome"), [(2, 200, "pending"), (1, 422, None)])
def test_pending_or_canceled_packs_give_no_credits(make_client, state, code, outcome):
    store, play = consented_store(credits=0), FakePlay()
    play.products[PACK] = {"purchaseState": state, "consumptionState": 0}
    response = verify(make_client(store=store, play_client=play), "creditos_10", PACK)
    assert response.status_code == code
    if outcome:
        assert response.json()["outcome"] == outcome
        assert response.json()["device"]["credits"] == 0
    assert play.consumed == []


# Cobro del acceso de 10 minutos en el juez


def judge(client, **overrides):
    return client.post(
        "/excuses/judge",
        json=judge_request(**overrides).model_dump(mode="json"),
        headers=auth_headers(),
    )


def test_a_paid_plan_uses_its_included_accesses_first(make_client):
    store = consented_store(plan="monthly", credits=5)
    client = make_client(store=store, judge_llm=FakeLLM({JudgeEvaluation: evaluation()}))

    body = judge(client).json()

    assert (body["valid"], body["access_source"]) == (True, "included")
    assert (body["included_remaining"], body["credits"]) == (2, 5)


def test_free_pays_with_one_credit(make_client):
    store = consented_store(credits=2)
    client = make_client(store=store, judge_llm=FakeLLM({JudgeEvaluation: evaluation()}))

    body = judge(client).json()

    assert (body["access_source"], body["credits"]) == ("credit", 1)


def test_a_rejection_costs_only_the_evaluation(make_client):
    store = consented_store(credits=2)
    rejected = evaluation(valid=False, reason_code="boredom", message="No.")
    client = make_client(store=store, judge_llm=FakeLLM({JudgeEvaluation: rejected}))

    body = judge(client).json()
    status = client.get("/devices/me", headers=auth_headers()).json()

    assert body["valid"] is False and body["access_source"] is None
    assert (status["credits"], status["remaining_today"]) == (2, 2)


def test_an_emergency_never_costs_anything(make_client):
    store = consented_store(credits=0)
    client = make_client(store=store, judge_llm=FakeLLM({JudgeEvaluation: evaluation()}))

    body = judge(client, type="emergency").json()
    status = client.get("/devices/me", headers=auth_headers()).json()

    assert body["valid"] is True and body["access_source"] is None
    assert (status["credits"], status["remaining_today"]) == (0, 3)


def test_a_repeated_request_is_not_judged_or_charged_twice(make_client):
    store = consented_store(credits=2)
    llm = FakeLLM({JudgeEvaluation: evaluation()})
    client = make_client(store=store, judge_llm=llm)
    request_id = str(uuid4())

    first = judge(client, request_id=request_id).json()
    second = judge(client, request_id=request_id).json()

    assert first["access_source"] == second["access_source"] == "credit"
    assert second["credits"] == 1
    assert llm.count(JudgeEvaluation) == 1


def test_if_the_last_credit_is_gone_the_approval_is_not_given(make_client, monkeypatch):
    store = consented_store(credits=1)
    client = make_client(store=store, judge_llm=FakeLLM({JudgeEvaluation: evaluation()}))

    async def nothing_left(*_: Any) -> None:
        return None

    monkeypatch.setattr(store, "spend_access", nothing_left)
    body = judge(client).json()

    assert (body["valid"], body["reason_code"], body["granted_minutes"]) == (
        False,
        "no_credits",
        0,
    )


# Notificaciones en tiempo real (RTDN)


def rtdn_settings():
    return make_settings(
        rtdn_audience="https://api.invalid/billing/rtdn",
        rtdn_service_account_email="pubsub@proyecto.iam.gserviceaccount.com",
    )


def push(client, notification: dict[str, Any], authorization: str = "Bearer jwt"):
    data = base64.b64encode(json.dumps(notification).encode()).decode()
    return client.post(
        "/billing/rtdn",
        json={"message": {"data": data, "messageId": "1"}, "subscription": "s"},
        headers={"Authorization": authorization},
    )


def sub_notification(token: str = SUB, kind: int = 2) -> dict[str, Any]:
    return {
        "version": "1.0",
        "packageName": "com.lavidanoesunbanano.app",
        "subscriptionNotification": {"notificationType": kind, "purchaseToken": token},
    }


@pytest.fixture
def trusted_push(monkeypatch):
    """Simula un JWT válido de Pub/Sub; la verificación real se prueba aparte."""
    monkeypatch.setattr(billing, "verify_push", lambda authorization, settings: {})
    import banano.api.routes as routes

    monkeypatch.setattr(routes, "verify_push", lambda authorization, settings: {})


def test_rtdn_rejects_a_push_without_a_valid_jwt(make_client):
    client = make_client(settings=rtdn_settings(), store=consented_store(), play_client=FakePlay())
    assert push(client, sub_notification(), authorization="Bearer falso").status_code == 401
    assert push(client, sub_notification(), authorization="").status_code == 401


def test_rtdn_is_off_until_configured(make_client):
    client = make_client(store=consented_store(), play_client=FakePlay())
    assert push(client, sub_notification()).status_code == 503


@pytest.mark.parametrize(
    ("state", "days", "plan"),
    [
        ("SUBSCRIPTION_STATE_ACTIVE", 30, "monthly"),
        ("SUBSCRIPTION_STATE_IN_GRACE_PERIOD", 3, "monthly"),
        ("SUBSCRIPTION_STATE_ON_HOLD", 3, "free"),
        ("SUBSCRIPTION_STATE_PAUSED", 3, "free"),
        ("SUBSCRIPTION_STATE_CANCELED", 3, "monthly"),
        ("SUBSCRIPTION_STATE_EXPIRED", -1, "free"),
    ],
)
def test_rtdn_rereads_google_and_updates_the_plan(make_client, trusted_push, state, days, plan):
    store, play = consented_store(), FakePlay()
    seed_entitlement(store, TEST_DEVICE_ID, token=SUB)
    play.subscriptions[SUB] = subscription(state=state, days=days)
    client = make_client(settings=rtdn_settings(), store=store, play_client=play)

    assert push(client, sub_notification()).status_code == 204
    assert client.get("/devices/me", headers=auth_headers()).json()["plan"] == plan


def test_rtdn_acknowledges_a_pending_payment_charged_with_the_app_closed(make_client, trusted_push):
    store, play = consented_store(), FakePlay()
    seed_entitlement(store, TEST_DEVICE_ID, token=SUB)
    play.subscriptions[SUB] = subscription()
    client = make_client(settings=rtdn_settings(), store=store, play_client=play)

    assert push(client, sub_notification()).status_code == 204
    assert play.acknowledged == [SUB]
    play.subscriptions[SUB] = subscription(acknowledged=True)
    assert push(client, sub_notification()).status_code == 204
    assert play.acknowledged == [SUB]


def test_rtdn_of_an_unknown_purchase_changes_nothing(make_client, trusted_push):
    store, play = consented_store(), FakePlay()
    play.subscriptions[SUB] = subscription()
    client = make_client(settings=rtdn_settings(), store=store, play_client=play)

    assert push(client, sub_notification()).status_code == 204
    assert store.entitlements == {}


def test_rtdn_refunds_a_voided_credit_pack(make_client, trusted_push):
    store, play = consented_store(credits=0), FakePlay()
    play.products[PACK] = {"purchaseState": 0, "consumptionState": 0}
    client = make_client(settings=rtdn_settings(), store=store, play_client=play)
    verify(client, "creditos_10", PACK)
    voided = {
        "version": "1.0",
        "packageName": "com.lavidanoesunbanano.app",
        "voidedPurchaseNotification": {"purchaseToken": PACK, "productType": 2, "refundType": 1},
    }

    assert push(client, voided).status_code == 204
    assert push(client, voided).status_code == 204
    assert client.get("/devices/me", headers=auth_headers()).json()["credits"] == 0


def test_rtdn_of_a_refunded_subscription_removes_the_plan(make_client, trusted_push):
    store, play = consented_store(), FakePlay()
    seed_entitlement(store, TEST_DEVICE_ID, token=SUB)
    play.subscriptions[SUB] = subscription(state="SUBSCRIPTION_STATE_EXPIRED", days=-1)
    client = make_client(settings=rtdn_settings(), store=store, play_client=play)
    voided = {
        "version": "1.0",
        "packageName": "com.lavidanoesunbanano.app",
        "voidedPurchaseNotification": {"purchaseToken": SUB, "productType": 1, "refundType": 1},
    }

    assert push(client, voided).status_code == 204
    assert client.get("/devices/me", headers=auth_headers()).json()["plan"] == "free"


def test_rtdn_of_another_app_or_malformed_is_acknowledged_and_ignored(make_client, trusted_push):
    store, play = consented_store(), FakePlay()
    seed_entitlement(store, TEST_DEVICE_ID, token=SUB)
    client = make_client(settings=rtdn_settings(), store=store, play_client=play)
    foreign = {**sub_notification(), "packageName": "com.otra.app"}

    assert push(client, foreign).status_code == 204
    malformed = client.post(
        "/billing/rtdn", json={"message": {"data": "%%%"}}, headers={"Authorization": "Bearer x"}
    )
    assert malformed.status_code == 204


def test_rtdn_asks_for_a_retry_when_google_is_down(make_client, trusted_push):
    store, play = consented_store(), FakePlay()
    seed_entitlement(store, TEST_DEVICE_ID, token=SUB)
    play.subscriptions[SUB] = PlayUnavailableError("503")
    client = make_client(settings=rtdn_settings(), store=store, play_client=play)
    assert push(client, sub_notification()).status_code == 503


# Piezas sueltas


def test_verify_push_checks_audience_and_sender(monkeypatch):
    settings = rtdn_settings()
    seen: dict[str, Any] = {}

    def fake_verify(token, request, audience):
        seen["audience"] = audience
        return {"email": settings.rtdn_service_account_email, "email_verified": True}

    monkeypatch.setattr(billing.id_token, "verify_oauth2_token", fake_verify)
    assert billing.verify_push("Bearer abc", settings)["email_verified"] is True
    assert seen["audience"] == settings.rtdn_audience

    monkeypatch.setattr(
        billing.id_token,
        "verify_oauth2_token",
        lambda *a, **k: {"email": "otro@x.com", "email_verified": True},
    )
    with pytest.raises(ValueError):
        billing.verify_push("Bearer abc", settings)
    with pytest.raises(ValueError):
        billing.verify_push(None, settings)


def test_the_account_id_sent_to_google_is_a_hash():
    hashed = account_hash(TEST_DEVICE_ID)
    assert len(hashed) == 64 and TEST_DEVICE_ID not in hashed
