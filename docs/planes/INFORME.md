# Informe final: planes de pago con Google Play Billing

Para: Sergio. Rama `feat/planes-play-billing`, subida como `play-store`. `production` (`aebb214`) no cambió.

**La app NO está lista para Google Play.** El código está hecho y probado con tests, pero las compras reales, la configuración de Play Console, la infraestructura y la revisión legal siguen pendientes (apartados 3 a 6).

Etiquetas: **IMPLEMENTADO** (código escrito), **PROBADO** (sus tests corrieron en verde), **PENDIENTE**.

## 1. Cambios por paso

| Paso | Commit | Qué cambió | Estado |
|---|---|---|---|
| 0 | `41f5ddb` | Plan aprobado (`PLAN.md`), avance (`ESTADO.md`) y subagentes en `.claude/agents/` | IMPLEMENTADO |
| — | `f138f5c`, `3559aa7` | Dos fallos que ya estaban en `production`: el test de emojis rechazaba ✓ y ✕; `LocalTime.ofInstant` hacía caer las notificaciones en Android 8 a 11 | PROBADO |
| 1 | `45b9e84` | Nombre completo «La vida no es un banano» y «Peel» en textos, prompt del juez y mensajes del servidor | PROBADO |
| 9 | `58b77fc`, `fef164c` | Sin permiso de batería, `allowBackup="false"`, divulgación de Accesibilidad con «Acepto/No acepto», anti-autosabotaje solo dentro de la app con frase fija, sin `SYSTEM_ALERT_WINDOW`. Versión 0.5.1 (versionCode 6) | PROBADO, y en el teléfono de Sergio |
| 2 | `8ed521d` | `PlanCatalog` (Android) y `Settings.benefits` (backend) como única fuente de los planes | PROBADO |
| — | `e0424de` | `backend/vercel.json` desactiva los despliegues de `play-store` y `feat/planes-play-billing`: la migración 007 no llega a la base de producción | PROBADO (sin despliegue nuevo en Vercel) |
| 3 | `ce7e60e` … `251c99e` | Migración 007, contadores separados, plan efectivo desde el derecho validado, `/billing/verify`, `/billing/rtdn`, cobro del acceso, créditos, traslado al reinstalar, reembolsos | PROBADO contra Postgres 17 de Docker |
| 4 | `7e1ed12` | Play Billing 9.1.0, compra con `obfuscatedAccountId`, cambio de plan con modo de reemplazo, restauración al abrir y volver | PROBADO |
| 5 | `702c3a5` | La pantalla de bloqueo comprueba con qué pagar antes de pedir la razón; `request_id` contra cobros dobles | PROBADO |
| 6 | `b484f6e` | Revisión automática solo con plan de pago validado | PROBADO |
| 7 | `ef06065` | Pantalla de planes, «¿Qué son los créditos?», resumen en Ajustes | PROBADO |
| 8 | (este commit) | Política de datos con pagos, borrador de Términos, README, este informe | IMPLEMENTADO |

En total: 101 archivos, unas 5.200 líneas añadidas y 600 borradas.

### Decisiones de producto aplicadas tal cual

Planes Gratis, Mensual (USD 1.99) y Semestral (USD 8.39 cada 6 meses); 3, 20 y 20 evaluaciones; 0, 3 y 3 accesos incluidos; créditos `creditos_10`; emergencias nunca cobran; reinicio a medianoche de Colombia; revisión automática solo en planes de pago. Precios siempre de Google Play.

### Decisiones tomadas con Sergio durante el trabajo

- Anti-autosabotaje solo dentro de la app, con la frase «Soy consciente de que estoy apagando la protección».
- Los créditos siguen al plan al reinstalar; sin plan se pierden, y se avisa antes de comprar.
- Sin `SYSTEM_ALERT_WINDOW`, tras probar en el teléfono que el bloqueo funciona igual.
- Gratis no tiene «Revisar ahora».

## 2. Pruebas

| Qué | Comando | Resultado |
|---|---|---|
| Backend | `cd backend && TEST_DATABASE_URL=postgresql://banano:banano@localhost:5432/banano uv run pytest` | 300 pasados, 0 saltados (con Postgres 17 de Docker) |
| Backend, estilo | `cd backend && uv run ruff check` | Sin errores |
| Android | `cd android && JAVA_HOME="…/Android Studio/jbr" ./gradlew testDebugUnitTest lint` | 195 pasados; lint sin errores |
| Teléfono (0.5.1) | Lista de 12 pruebas en `ESTADO.md` | Hechas por Sergio: el bloqueo funciona sin `SYSTEM_ALERT_WINDOW` |

**No se pudo probar desde aquí:**

- compras reales, renovaciones, cambios de plan, compras pendientes y reembolsos: requieren la app en Play Console, los productos dados de alta y testers de licencia;
- RTDN de verdad: requiere el tema de Pub/Sub y la suscripción push;
- la verificación real del JWT de Pub/Sub y las llamadas reales a la Developer API: en los tests están simuladas, con los formatos de la documentación oficial;
- las pantallas nuevas en un teléfono (planes, bloqueo sin créditos o sin evaluaciones, resumen de Ajustes): solo hay tests de lógica.

## 3. Pendiente en Play Console

Guía paso a paso en [`PLAY_CONSOLE.md`](PLAY_CONSOLE.md).

- [ ] App creada.
- [ ] Prueba cerrada con al menos 12 testers durante 14 días seguidos (cuenta personal nueva).
- [ ] Suscripción `premium` con base plans `monthly` (1 mes) y `semester` (6 meses), renovación automática.
- [ ] Producto único `creditos_10` (consumible).
- [ ] Precios por país.
- [ ] Período de gracia y suspensión de cuenta (account hold); decidir si se permite la pausa.
- [ ] Tema de Pub/Sub para RTDN, con permiso de publicación para `google-play-developer-notifications@system.gserviceaccount.com`.
- [ ] Cuenta de servicio con acceso a la API y permisos «Ver datos financieros» y «Gestionar pedidos y suscripciones».
- [ ] Testers de licencia.
- [ ] Declaración de Accesibilidad y vídeo de la divulgación.
- [ ] Seguridad de los datos (añadir «Historial de compras»).
- [ ] URL pública de la política de privacidad.
- [ ] Clasificación de contenido y público objetivo (18+).

## 4. Pendiente en el backend y la infraestructura

- [ ] Variables en Vercel: `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`, `RTDN_AUDIENCE`, `RTDN_SERVICE_ACCOUNT_EMAIL` (y las demás de `.env.example`, que tienen valores por defecto).
- [ ] Migración 007 en Neon de producción: se aplica sola en la primera conexión al desplegar, **solo con tu aprobación** y al fusionar a `production`. Debe desplegarse la versión final: el formato de las referencias del registro cambió antes de su primer despliegue.
- [ ] Suscripción push de Pub/Sub hacia `https://<backend>/billing/rtdn`, con autenticación por cuenta de servicio y la misma audiencia que `RTDN_AUDIENCE`.
- [ ] Desplegar el backend **antes** o a la vez que la app con facturación: los APK 0.4.0 instalados pasan a Gratis (sus planes elegidos a mano dejan de valer) y Gratis ya no recibe 10 minutos sin créditos.
- [ ] Las consultas de análisis `sql/queries/05` y `06` agrupan por `devices.plan`, que ya no refleja el plan real.

## 5. Riesgos y limitaciones

| Riesgo | Fuente | Estado |
|---|---|---|
| Accesibilidad: la revisión puede pedir justificar el uso. La IA no maneja el servicio (aplica una regla fija), pero el agente puede iniciar sesiones de enfoque | https://support.google.com/googleplay/android-developer/answer/10964491 | Mitigado; si lo rechazan, el agente propondría y el usuario confirmaría |
| Batería: sin exención directa, algunos fabricantes pueden detener el servicio | https://developer.android.com/training/monitoring-device-state/doze-standby | Aceptado; el asistente guía a los ajustes del sistema |
| Frecuencia de la revisión: no es exacta y Android la retrasa | https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work | Los textos dicen «aproximadamente» y avisan del retraso |
| Créditos ligados a la instalación: sin plan se pierden al desinstalar o borrar datos | Decisión de producto (sin cuentas) | Avisado antes de comprar y en los Términos |
| Período de gracia: se asume un máximo de 30 días | NO VERIFICADO | Margen en `GRACE_LEEWAY` |
| Política de datos y Términos sin revisión legal ni contacto | `docs/politica-de-datos.md`, `docs/terminos.md` | PENDIENTE |
| ¿Hay que volver a pedir las autorizaciones al añadir los pagos? La política dice que se piden de nuevo si cambia «de forma importante» | `docs/politica-de-datos.md` | PENDIENTE: decisión legal; hoy no se piden de nuevo |
| Cobrar por desbloquear apps que el usuario bloqueó | NO VERIFICADO (política de Comportamiento engañoso no revisada) | Explicado con exactitud antes de pagar |

## 6. Revisiones

- **Seguridad (backend):** revisión con un subagente sobre la base `aebb214` (el comando `/security-review` falla porque `origin/HEAD` no comparte historia con `production`), más los avisos automáticos del plugin. Diez fallos reales corregidos; ninguno permitía conseguir plan o créditos sin pagar. Detalle en los commits `f10c48b`, `6bde84b`, `2103fdd` y `251c99e`.
- **Seguridad (toda la rama, foco Android):** segunda revisión con subagente sobre `aebb214`. Sin fallos altos ni medios. Uno bajo corregido: `allowBackup="false"` no frena la transferencia entre dispositivos en Android 12+, así que se añadieron `dataExtractionRules` y `fullBackupContent` que excluyen todo (la identidad, el plan y el historial no pasan a otro teléfono). Corregido también un doble toque que podía abrir dos compras a la vez. 195 tests y lint sin errores.
- **Cumplimiento del Paso 9:** sin bloqueantes; corregida la descripción de qué datos salen del teléfono.
- **Cumplimiento del Paso 7:** sin bloqueantes; corregidos cinco importantes (compra pendiente mostrada como activa, cambio de plan sin precio ni momento del cobro, frecuencia de la revisión, aviso de pérdida de créditos, condiciones ocultas con Gratis).
- **Cumplimiento final sobre todos los cambios:** sin bloqueantes de código. Corregidos: condiciones del traslado de créditos (con la misma cuenta de Google; borrar los datos siempre los elimina), «hasta» en las evaluaciones, versión de la autorización en la política (`2026-11-v2`, la del código), campos de compra y seudonimización en la política, y la tabla de Seguridad de los datos de `PLAY_CONSOLE.md`. Quedan abiertos los puntos marcados en el checklist.
- **pr-review-toolkit:** PENDIENTE, lo ejecuta Sergio.

## 7. Checklist antes de publicar

| Punto | Estado |
|---|---|
| Código de planes, créditos y facturación | IMPLEMENTADO y PROBADO con tests |
| Bloqueantes de política (batería, Accesibilidad, anti-autosabotaje, backup, superposición) | IMPLEMENTADO y PROBADO en teléfono |
| Prueba cerrada y producción en Play Console | PENDIENTE |
| Productos, precios, Pub/Sub y cuenta de servicio | PENDIENTE |
| Variables en Vercel y migración en Neon | PENDIENTE |
| Compras reales con testers de licencia | PENDIENTE |
| Pantallas nuevas probadas en un teléfono | PENDIENTE |
| Revisión legal de la política y los Términos | PENDIENTE |
| Revisión pr-review-toolkit | PENDIENTE |
| Contacto del responsable en la política y los Términos | IMPLEMENTADO: sergioflorez422@gmail.com |
| `docs/terminos.md` y la política nueva publicados: `policy_url` y `terms_url` apuntan a `production` en GitHub, que todavía no los tiene | PENDIENTE: fusionar antes de subir la versión con facturación |
| versionCode 7 (0.6.0) para el próximo AAB con facturación | PENDIENTE: necesita tu autorización |
| Manifest fusionado con Play Billing (permiso `com.android.vending.BILLING`) revisado | PENDIENTE: se ve al generar el AAB nuevo |
| ¿Volver a pedir las autorizaciones por los pagos? | PENDIENTE: decisión legal |
| Texto de la autorización de analítica («Solo registramos qué botones tocas») frente a lo que se registra (bloqueos, salidas y veredictos) | PENDIENTE: viene de antes de esta rama; cambiarlo exige subir la versión de la autorización |
| Purga de suscripciones vencidas en `entitlements` (hoy se guardan hasta que el usuario borra sus datos) | PENDIENTE |
