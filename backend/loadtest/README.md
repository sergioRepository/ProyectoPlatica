# Prueba de carga (k6)

`load.js` registra dispositivos de prueba, simula el uso de la app y los borra al final.

- `MODE=smoke`: 5 peticiones/s durante 1 minuto con 20 dispositivos. No usa la IA.
- `MODE=full`: sube de 5 a 160 peticiones/s en escalones de 90 s (unas 28.000 peticiones) con 200 dispositivos. En paralelo, 2 usuarios piden emergencias reales a Peel durante 2 minutos (unas 50 llamadas al modelo). Se corta sola si falla más del 5 % o si p95 pasa de 3 s.

Se ejecuta con Docker, sin instalar k6:

```sh
export ENROLLMENT_CODE=…   # el de producción; nunca en el repo
BASE_URL=https://<backend> MODE=smoke MSYS_NO_PATHCONV=1 \
  docker run --rm -i -e BASE_URL -e ENROLLMENT_CODE -e MODE grafana/k6:1.0.0 run - < backend/loadtest/load.js
```

En local: `BASE_URL=http://host.docker.internal:8000`, con el backend de `docker compose` y los límites desactivados.

## Antes de lanzarla contra Vercel

1. **Límites por IP.** Todo sale de una sola IP, y el registro admite 10 por hora. Pon `RATE_LIMIT_REGISTER_PER_HOUR=0` y `RATE_LIMIT_DEVICE_PER_HOUR=0` en las variables de producción y vuelve a desplegar. Al terminar, restaura `10` y `60`.
2. **Firewall de Vercel.** Crea una regla personalizada que permita (Bypass) tu IP y comprueba que Attack Challenge Mode esté apagado. Sin eso, Vercel puede bloquear la prueba como si fuera un ataque ([guía de Vercel](https://vercel.com/kb/guide/how-to-effectively-load-test-your-vercel-application)).
3. **Plan Hobby.** Incluye 4 horas de Active CPU y 1 millón de invocaciones al mes (100.000 en proyectos sin Fluid compute). Pasarse puede pausar el proyecto. Revisa Usage antes y después.
4. **IA.** El modo `full` llama al proveedor real unas 50 veces.
