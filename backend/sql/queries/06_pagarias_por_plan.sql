-- Respuestas a "¿Pagarías?" por plan
SELECT
    plan,
    coalesce(would_pay, 'sin respuesta') AS pagaria,
    count(*) AS dispositivos
FROM devices
GROUP BY 1, 2
ORDER BY 1, 2;
