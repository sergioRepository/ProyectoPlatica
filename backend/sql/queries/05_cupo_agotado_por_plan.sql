-- Dispositivos que llegaron a quota_reached, por plan
-- Solo cubre a quien autorizó la analítica. El plan es el actual del dispositivo.
SELECT
    d.plan,
    count(DISTINCT d.device_id) AS dispositivos,
    count(DISTINCT e.device_id) AS llegaron_al_tope
FROM devices d
LEFT JOIN app_events e ON e.device_id = d.device_id AND e.name = 'quota_reached'
GROUP BY 1
ORDER BY 1;
