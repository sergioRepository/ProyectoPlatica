-- Costo y tokens por dispositivo, día, plan y grafo
SELECT
    (created_at AT TIME ZONE 'America/Bogota')::date AS dia,
    device_id,
    plan,
    graph,
    count(*) AS llamadas,
    sum(input_tokens) AS tokens_entrada,
    sum(output_tokens) AS tokens_salida,
    sum(cost_usd) AS costo_usd
FROM llm_usage
GROUP BY 1, 2, 3, 4
ORDER BY 1 DESC, costo_usd DESC NULLS LAST;
