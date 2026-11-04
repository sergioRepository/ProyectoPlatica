-- Proporción de bloqueos que terminan en block_exit
-- Solo cubre a quien autorizó la analítica.
SELECT
    app,
    count(*) FILTER (WHERE name = 'block_shown') AS bloqueos,
    count(*) FILTER (WHERE name = 'block_exit') AS salidas,
    round(
        count(*) FILTER (WHERE name = 'block_exit')::numeric
        / nullif(count(*) FILTER (WHERE name = 'block_shown'), 0),
        3
    ) AS proporcion_salidas
FROM app_events
WHERE name IN ('block_shown', 'block_exit')
GROUP BY 1
ORDER BY bloqueos DESC;
