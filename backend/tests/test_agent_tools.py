from banano.agent.tools import consultar_evidencia, consultar_evidencia_uso
from banano.schemas.agent import UsageSession, UsageSnapshot

NOW = 10_000_000_000
MIN = 60_000


def snapshot(*sessions: UsageSession, status: str = "ok") -> UsageSnapshot:
    return UsageSnapshot(status=status, collected_at_ms=NOW, sessions=list(sessions))


def session(package: str, minutes_ago: float, minutes: float, label: str = "") -> UsageSession:
    return UsageSession(
        package=package,
        label=label or package,
        start_ms=int(NOW - minutes_ago * MIN),
        minutes=minutes,
    )


def test_suma_minutos_y_aperturas_en_la_ventana():
    usage = snapshot(
        session("tiktok", 50, 10, "TikTok"),
        session("tiktok", 20, 15, "TikTok"),
        session("chrome", 40, 5),
    )
    result = consultar_evidencia(usage, consultar_evidencia_uso(ventana_minutos=60))
    assert result["status"] == "ok"
    assert result["apps"][0] == {
        "package": "tiktok",
        "label": "TikTok",
        "category": "",
        "minutos": 25.0,
        "aperturas": 2,
    }
    assert [a["package"] for a in result["apps"]] == ["tiktok", "chrome"]


def test_recorta_la_sesion_que_empezo_antes_de_la_ventana():
    usage = snapshot(session("tiktok", 70, 30))
    result = consultar_evidencia(usage, consultar_evidencia_uso(ventana_minutos=60))
    assert result["apps"][0]["minutos"] == 20.0


def test_filtra_por_paquete_o_nombre():
    usage = snapshot(session("com.zhiliaoapp.musically", 10, 5, "TikTok"), session("chrome", 10, 5))
    result = consultar_evidencia(
        usage, consultar_evidencia_uso(ventana_minutos=60, apps=["tiktok"])
    )
    assert [a["label"] for a in result["apps"]] == ["TikTok"]


def test_cero_uso_no_es_falta_de_permiso():
    empty = consultar_evidencia(snapshot(), consultar_evidencia_uso(ventana_minutos=60))
    missing = consultar_evidencia(
        snapshot(status="permission_missing"), consultar_evidencia_uso(ventana_minutos=60)
    )
    assert empty["status"] == "no_data"
    assert missing == {"status": "permission_missing"}
