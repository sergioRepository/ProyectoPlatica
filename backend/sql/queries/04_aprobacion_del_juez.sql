-- Tasa de aprobación del juez por tipo
-- Solo cubre a quien autorizó la analítica.
SELECT
    CASE name WHEN 'emergency_requested' THEN 'emergencia' ELSE '10 minutos' END AS tipo,
    count(*) AS veredictos,
    count(*) FILTER (WHERE props ->> 'verdict' = 'approved') AS aprobados,
    round(
        count(*) FILTER (WHERE props ->> 'verdict' = 'approved')::numeric / count(*),
        3
    ) AS tasa_aprobacion
FROM app_events
WHERE name IN ('emergency_requested', 'extra_time_requested')
GROUP BY 1
ORDER BY 1;
