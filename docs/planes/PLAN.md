# Plan aprobado: planes de pago con Google Play Billing

Aprobado por Sergio el 2026-11-11. Rama de trabajo: `feat/planes-play-billing`.

Este archivo es la fuente de los subagentes, que no ven la conversación. Tiene tres partes:

- **A. Cómo se ejecuta**: orden, reparto, commits y paradas.
- **B. Decisiones de producto**: lo que la app debe hacer.
- **C. Plan técnico**: diagnóstico y cambios por archivo, tal como se aprobó en la Fase 1.

Si dos partes chocan, manda A, después B y después C.

---

# A. Cómo se ejecuta

## Orden de los pasos

Paso 0 → Paso 1 → **Paso 9** → Pasos 2, 3, 4, 5, 6, 7 y 8. Los números son los de la parte C.

El Paso 9 (bloqueantes de Google Play) va antes porque la cuenta de desarrollador es personal y nueva: Google exige una prueba cerrada con 12 testers durante 14 días seguidos antes de publicar, y ese plazo debe empezar cuanto antes.

Al terminar el Paso 9:

- `versionCode = 5` y `versionName = "0.5.0"` (autorizado por Sergio);
- se genera el AAB de release con `./gradlew bundleRelease`; si la firma no está configurada, se explica qué falta y no se toca el keystore;
- se crea `docs/planes/PLAY_CONSOLE.md` con los pasos de la prueba cerrada y los datos que Sergio debe preparar;
- se para y se avisa a Sergio.

## Quién hace qué

| Quién | Modelo | Qué hace |
|---|---|---|
| Sesión principal | Fable | Coordina, decide y revisa. Escribe lo delicado: `banano/billing.py`, `/billing/verify`, `/billing/rtdn` y la verificación del JWT; `spend_access`, `save_entitlement` y el traslado de créditos; la máquina de estados de `PlayBillingRepository` y los modos de reemplazo; y toda duda que devuelva un subagente. |
| `implementer` | sonnet | Pasos 2, 5, 6, 7 y 9, y lo mecánico de los pasos 3 y 4: migración SQL, DTOs, `MemoryStore`, inyección de dependencias, Gradle y tests de apoyo. Recibe un encargo concreto con archivos y criterio de terminado. |
| `strings-editor` | haiku | Pasos 1 y 8 y las actualizaciones de `ESTADO.md`, siempre a partir de una tabla exacta. Los textos nuevos los redacta la sesión principal. |
| `test-runner` | haiku | Todos los tests, lint y builds. La sesión principal no ejecuta gradle, pytest ni ruff. |
| `play-compliance-reviewer` | sonnet | Revisa al terminar el Paso 9 (solo sus cambios), al terminar el Paso 7 y al final sobre todos los cambios. |

- Backend y Android pueden ir en paralelo solo si no tocan los mismos archivos. Dentro de una misma capa, de uno en uno.
- Tras cada encargo se revisa `git diff --stat` y se leen solo los archivos con lógica de negocio o de seguridad.
- Si un subagente falla dos veces en lo mismo, lo hace la sesión principal.

## Commits y control de avance

- Un commit por paso, solo con los tests de ese paso en verde:
  - backend: `uv run pytest` y `uv run ruff check`, desde `backend/`;
  - Android: `./gradlew testDebugUnitTest lint`, desde `android/`.
- `docs/planes/ESTADO.md` se actualiza en cada commit con IMPLEMENTADO, PROBADO o PENDIENTE.
- Si un paso falla dos veces seguidas, se para y se reporta lo intentado. No se buscan rodeos.
- No se hace push ni se fusiona a `production` ni a `desarrollo`.

## Paradas obligatorias

1. Tras crear los subagentes, si hace falta reiniciar la sesión para verlos.
2. Antes del Paso 3, si no se puede confirmar que la base de datos de producción está a salvo.
3. Tras el Paso 9, para que Sergio suba el AAB a la prueba cerrada.
4. Tras el Paso 3, para que Sergio ejecute `/security-review`.
5. Al final, para la revisión de pr-review-toolkit sobre la rama completa.

## Reglas fijas

- **Base de datos de producción.** `schema_sql()` aplica todos los `sql/*.sql` en la primera conexión de cada instancia. La migración 007 no debe llegar a la base real de Neon sin la aprobación de Sergio. Antes del Paso 3 se comprueba si los despliegues de prueba de Vercel comparten `DATABASE_URL` con producción.
- **Seguridad.** Los avisos del plugin security-guidance son bloqueantes: se corrigen en el mismo paso o se explica por qué son un falso positivo. La revisión tras el Paso 3 mira sobre todo: la verificación del JWT de Pub/Sub (audiencia y emisor); la idempotencia por `purchaseToken` y `request_id`; que ningún endpoint eleve el plan ni sume créditos sin una compra validada con Google; y que las credenciales de la cuenta de servicio no lleguen a los logs ni a las respuestas.
- **Intocables.** `applicationId`, firma, keystore y secretos. El `versionCode` solo cambia como se indica arriba.
- **Estilo.** Comentarios en español y breves; patrón Repository + ViewModel + Hilt; nada de refactorizar fuera del objetivo.
- **Fuentes de Google Play.** Solo documentación oficial, con la URL de cada requisito. Lo que no se pueda verificar se marca «NO VERIFICADO».
- **Etiquetas.** Todo se reporta como IMPLEMENTADO, PROBADO o PENDIENTE. Nunca se dice que la app está lista para Google Play mientras quede algo pendiente.

## Cierre de la Fase 2

1. Parada para que Sergio ejecute la revisión de pr-review-toolkit.
2. Se corrige lo importante; el resto queda listado en `docs/planes/INFORME.md`.
3. Informe final con lo IMPLEMENTADO, lo PROBADO y lo PENDIENTE por separado.

---

# B. Decisiones de producto

## Identidad

- La app se llama **La vida no es un banano**, con «un». Nunca «La Vida» ni «La vida» sola como nombre en textos visibles.
- La mascota y juez de IA es **Peel**. Sustituye a «el banano» y a «el juez» cuando el texto visible se refiere a él.
- No se cambian: las frases donde «la vida» es lenguaje común («quitarme la vida», «la vida es mucho más que estar en el celular»); los identificadores técnicos (package `com.lavidanoesunbanano`, módulo `banano`, `LaVidaDatabase`, clases `LaVida*`, módulo `android/shared` con `com.lavidanoesbanano`); ni las claves de recursos, como `history_judge_banana`.

## Planes

| | Gratis | Mensual | Semestral |
|---|---|---|---|
| ID en código y backend | `free` | `monthly` | `semester` |
| Producto en Google Play | ninguno | suscripción `premium`, base plan `monthly` (1 mes, renovación automática) | suscripción `premium`, base plan `semester` (6 meses, renovación automática) |
| Precio base | USD 0 | USD 1.99 al mes | USD 8.39 cada 6 meses |
| Peel evalúa razones al día | 3 | 20 | 20 |
| Revisión automática del uso | No | Sí | Sí |
| Accesos de 10 min incluidos al día | 0 | 3 | 3 |
| Accesos adicionales | con créditos | con créditos | con créditos |

- Los precios de producción vienen siempre de `ProductDetails` (`formattedPrice`, moneda y período). Los valores en USD solo sirven de respaldo en builds de debug.
- El equivalente mensual y el porcentaje de ahorro se calculan con `priceAmountMicros`. Si no se pueden calcular, se ocultan.
- Comunes a todos los planes y siempre gratis: el bloqueo de apps, los mensajes personalizados y las emergencias.

## Qué es «evaluar una razón»

- Es una llamada del juez (`POST /excuses/judge`) sobre una petición de **10 minutos** a la que Peel llega a responder.
- No consumen evaluación: las emergencias, los veredictos sin conexión o por error o timeout, ni los análisis del agente.
- En el backend, `llm_calls` cuenta solo los juicios de 10 min. La revisión automática usa `agent_calls`, con su propio tope (96 al día).

## Créditos

- 1 crédito = 1 acceso de 10 minutos.
- Se compran como producto consumible `creditos_10`: 10 créditos, precio base USD 0.99.
- No caducan. Viven en el servidor, en un registro por dispositivo, con idempotencia por `purchaseToken`.
- Flujo de un acceso de 10 min:
  1. Peel evalúa la razón: consume 1 evaluación.
  2. Si aprueba, se usa primero un acceso incluido del día (solo planes de pago). Si no queda ninguno, el servidor descuenta 1 crédito.
  3. Si rechaza, no se descuenta nada más que la evaluación.
- Antes de pedir la razón, la interfaz comprueba que habrá con qué pagar el acceso. Si no, ofrece comprar créditos o mejorar el plan, en lugar de gastar una evaluación.
- El tope personal de Ajustes (`UnlockLimits.extraTimeDaily`) se aplica a todos los accesos de 10 min, también a los pagados con créditos. Comprar nunca lo supera.
- **Los créditos siguen al plan.** Cuando el servidor liga una suscripción a una instalación nueva, traslada el saldo de la anterior. Quien compra créditos sin plan los pierde al desinstalar o al borrar sus datos, y la app lo avisa antes de comprar.

## Reinicio diario

- Los contadores diarios se reinician a medianoche, hora de Colombia (UTC−5, sin horario de verano). El único punto de configuración es `quota_utc_offset_hours`.
- La interfaz lo dice: «Se reinician a medianoche (hora de Colombia)».
- Las evaluaciones y los accesos incluidos no usados no se acumulan. Los créditos sí se conservan.

## Al agotar algo

- **Sin evaluaciones:** hoy no se pueden pedir 10 minutos. Texto: «Hoy no quedan evaluaciones. Mañana vuelven.», más una opción de mejorar el plan. Las emergencias siguen disponibles.
- **Sin accesos incluidos:** se usan créditos.
- **Sin créditos:** se ofrece comprarlos.
- No se inventan otros comportamientos.

## Emergencias

- Peel juzga la razón cuando puede. Una crisis detectada se aprueba siempre y muestra las líneas de ayuda.
- Sin red o sin juez, se aprueba y queda «por revisar» en el historial.
- Tope: `emergencyDaily` (2 por defecto, configurable) y 15 minutos.
- Nunca consumen evaluaciones ni créditos, en ningún plan.

## Revisión automática

- Hace lo que ya hace `AgentCheckWorker`: revisa el uso de las últimas 24 horas con reglas locales y, si lo justifican, pide un análisis al agente.
- Solo se programa si el plan validado es de pago. Se cancela al volver a Gratis o al vencer la suscripción.
- En Gratis tampoco está disponible «Revisar ahora».
- Texto: «Peel revisa tu uso aproximadamente cada 15 minutos. Android puede retrasar la revisión para ahorrar batería.» Nunca se promete una frecuencia exacta.

## Facturación y derechos

- Play Billing Library directa (`billing-ktx:9.1.0`), sin RevenueCat.
- Cada compra lleva `obfuscatedAccountId` = SHA-256 del `device_id`.
- La validación en el servidor es obligatoria. `POST /billing/verify` recibe `purchaseToken` y `productId`, consulta la Google Play Developer API, guarda el derecho y hace el acknowledge o el consumo desde el servidor.
- `POST /billing/rtdn` recibe el push de Pub/Sub, verifica su JWT y vuelve a leer el estado en la API.
- El plan de un dispositivo lo determina solo el servidor, a partir del derecho vigente. `PUT /devices/me/plan` solo guarda `would_pay`.
- Con acceso: `ACTIVE`, `IN_GRACE_PERIOD`, y `CANCELED` mientras no venza. Sin acceso: `ON_HOLD`, `PAUSED`, `EXPIRED`, `PENDING` y las compras revocadas.
- Restauración: al iniciar la app y al volver a primer plano, `queryPurchasesAsync` y después `/billing/verify`. Una suscripción ligada a otra instalación se re-liga a la actual: 1 suscripción = 1 dispositivo activo.
- Estados que la app distingue: cargando productos, compra en curso, pendiente (sin beneficios hasta que el servidor la valide), cancelada por el usuario, error con reintento, activa, en gracia (beneficios y aviso de pago), en suspensión (sin beneficios y enlace a gestionar), cancelada pero vigente hasta una fecha, y vencida.
- Sin compras duplicadas: botón desactivado durante un flujo; nunca se ofrece el plan activo; el cambio de plan usa un modo de reemplazo explícito: Mensual → Semestral con `CHARGE_FULL_PRICE` y Semestral → Mensual con `WITHOUT_PRORATION`.
- Las credenciales de Google se leen de variables de entorno, van sin valor en `.env.example` y nunca entran al repo.

## Pantalla de planes

- Sigue la jerarquía de la imagen de referencia (descrita al final de la parte C) con el design system existente.
- Distingue «Tu plan» (validado por el servidor) de la tarjeta seleccionada.
- Botón principal según la selección: «Suscribirme · {precio}/{período}», «Cambiar a Semestral», «Es tu plan actual» (deshabilitado) o «Gestionar suscripción».
- Bajo el botón, sin toques extra: importe y moneda de la tienda, período, «Se renueva automáticamente hasta que la canceles», cómo cancelar, enlace a la gestión de suscripciones de Google Play, y enlaces a la Política de privacidad y a los Términos.
- El importe cobrado es la cifra más destacada. El equivalente mensual va en texto secundario.
- Sección «¿Qué son los créditos?» con las reglas de arriba.
- Seleccionar una tarjeta nunca activa beneficios. Solo los activa el estado validado.
- En Ajustes, un resumen (plan, evaluaciones restantes, accesos incluidos, créditos) y el botón «Ver planes».

## Cumplimiento de Google Play

- **Anti-autosabotaje solo dentro de la app.** El servicio de accesibilidad no tapa ninguna pantalla del sistema. La protección se apaga dentro de la app escribiendo la frase «Soy consciente de que estoy apagando la protección» (sin distinguir mayúsculas, tildes ni espacios de más), o programándola para 24 horas después.
- `android:allowBackup="false"`.
- Sin el permiso `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: el paso de batería abre la lista de ajustes del sistema.
- Divulgación de Accesibilidad en pantalla propia, con «Acepto» y «No acepto», antes de abrir los ajustes del sistema.

---

# C. Plan técnico aprobado en la Fase 1

# Planes de pago con Google Play Billing — La vida no es un banano

> **Fase 1 (diagnóstico, solo lectura): TERMINADA.** Lo único hecho en el repo es crear la rama. Aprobar este plan da paso a la Fase 2.
> Nada de esto está listo para Google Play: ver «Pendiente fuera del código» al final.

## Contexto

Hoy la app tiene los planes `free`, `monthly` y `semester` sin cobro: el usuario los elige en Ajustes y el backend los acepta sin validar nada. Se va a cobrar con Google Play Billing (suscripción `premium` con base plans `monthly` y `semester`, consumible `creditos_10`), validar cada compra en el servidor, separar contadores, añadir créditos y unificar la marca: la app es **La vida no es un banano** y la mascota y juez es **Peel**. Las decisiones de producto del encargo se implementan tal cual.

## Fase 0 — estado

- Rama `feat/planes-play-billing` creada desde `production` (`aebb214`), árbol limpio. IMPLEMENTADO.
- `production` (0.4.0) y `desarrollo` (0.3.0) no comparten historia: un PR hacia `desarrollo` no sería un merge normal.
- No existe TodoWrite en esta sesión: el avance se lleva en `docs/planes/ESTADO.md`.
- Subagentes: A (nombres), B (planes y cupos) y D (Google Play) como Explore de solo lectura; C (segundo plano y permisos) lo leí yo, porque el modo plan admite tres.

## 1. Diagnóstico

1. **El plan lo elige el cliente y nadie lo valida.** `SettingsViewModel.selectPlan` → `PUT /devices/me/plan` (`routes.py:141`) → `store.set_plan` (`store.py:448`).
2. **Un solo contador para todo.** Juicios de 10 min, emergencias y análisis del agente gastan `llm_calls` (`quota.py:51`). Un fallo del LLM también gasta (`test_quota.py:161`).
3. **La pantalla de bloqueo no consulta al servidor** antes de pedir la razón (`BlockViewModel.kt:185`): el usuario escribe y después recibe `plan_quota`.
4. **El plan no se guarda en el teléfono** y `AgentCheckWorker` se programa para todos en `LaVidaApp.kt:14`.
5. **No hay Play Billing, créditos ni tablas de compras.** El backend ya tiene `httpx`; falta `google-auth` para la cuenta de servicio y el JWT de Pub/Sub.
6. **«Peel» no aparece en ningún archivo.** Ningún test compara los textos que cambian.
7. **El design system ya coincide con la imagen**: mismos colores, Plus Jakarta Sans y Geist Mono, `Modifier.toon` y `Modifier.dashed` (`core/designsystem/Components.kt`).
8. **El esquema se aplica solo**: `schema_sql()` (`store.py:34`) ejecuta `sql/*.sql` en la primera conexión de cada instancia. La migración nueva entra al desplegar.

## 2. Tabla de nombres

`strings.xml` = `android/app/src/main/res/values/strings.xml`. Las claves de recursos no cambian.

| archivo:línea | texto actual | tipo | propuesta |
|---|---|---|---|
| `strings.xml:22` `consent_not_medical` | «La Vida te ayuda a usar menos el teléfono…» | nombre de la app | «La vida no es un banano te ayuda a usar menos el teléfono. No es atención médica ni psicológica.» |
| `strings.xml:173` `home_brand` | «la vida · %1$s» | nombre de la app | «la vida no es un banano · %1$s» (va en mayúsculas mono; se prueba a 360 dp) |
| `strings.xml:385` `setup_accessibility_title` | «Deja que La Vida vea qué app abres» | nombre de la app | «Deja que veamos qué app abres» (el nombre completo ocuparía tres líneas; el paso 2 ya lo nombra) |
| `strings.xml:424` `sabotage_brand` | «La Vida · protección» | nombre de la app | se elimina junto con la pantalla que tapaba Ajustes (paso 9) |
| `strings.xml:253` `history_judge_banana` | «El banano:» | mascota | «Peel:» |
| `strings.xml:341-343` `settings_plan_limit` | «El banano evalúa hasta %1$d razones al día…» | mascota | «Peel evalúa hasta %1$d razones al día. Hoy te quedan %2$d.» |
| `strings.xml:477` `excuse_judging` | «El banano está leyendo tu razón…» | mascota | «Peel está leyendo tu razón…» |
| `strings.xml:108` `onboarding_sarcasm_insults_warning` | «…el juez puede insultarte a ti…» | juez | «…Peel puede insultarte a ti…» |
| `strings.xml:187` `home_disconnected_title` | «Sin conexión con el juez» | juez | «Sin conexión con Peel» |
| `strings.xml:508` `verdict_offline_rejected` | «Sin juez no hay minutos extra…» | juez | «Sin Peel no hay minutos extra. Vuelve cuando haya conexión.» |
| `strings.xml:346` `settings_plan_hint` | «Por ahora ningún plan tiene costo… sin juez…» | juez | se reescribe entero (paso 7) |
| `backend/banano/llm/prompts.py:43` | «Eres un juez estricto pero justo de excusas…» | juez | «Eres Peel, la mascota de "La vida no es un banano" y juez estricto pero justo de excusas para desbloquear una app. No firmes ni digas tu nombre.» |
| `backend/banano/graphs/judge_graph.py:57-60` | «Por hoy no quedan llamadas al juez. Sin juez…», «Por hoy no hay juez, así que te creo…» | juez; el usuario los ve como mensaje del veredicto | «Por hoy no quedan evaluaciones. Sin Peel no hay minutos extra.», «Ya fueron muchos rechazos por hoy. Sin Peel no hay minutos extra.», «Por hoy Peel no está, así que te creo. Queda anotado para revisarlo después.» |
| `README.md` y `docs/politica-de-datos.md:38` | «el juez» | documentación | «el juez (Peel)» en la primera mención; el resto igual |
| `prompts.py:29`, `safety/crisis.py:17`, `safety/guardrails.py:15`, `CrisisDetector.kt:11`, `README.md:29` | «la vida es mucho más…», «quitarme la vida» | frase común | no se cambia |
| `strings.xml:2, 19, 389, 418, 419` | «La vida no es un banano» | nombre correcto | no se cambia |
| package `com.lavidanoesunbanano` (113 archivos), módulo `banano`, `LaVida*`, `BananaMood`, `banana_*.xml`, `lavida.db`, `android/shared` | — | identificador | no se cambia |

No se tocan los prompts del generador ni del análisis (`prompts.py:11, 33, 38, 67, 141`): hablan de «el juez» en instrucciones internas y cambiarlos haría que «Peel» apareciera dentro de los mensajes de bloqueo.

## 3. Inconsistencias

### Código frente a la imagen

| En la imagen | En el código o en las decisiones |
|---|---|
| «el banano» (4 veces) | «Peel» |
| Semestral «$6.99», «cada 5 meses» | USD 8.39 cada 6 meses; Google Play no admite 5 meses |
| «AHORRAS 30%» y «≈ $1.40 al mes» fijos | se calculan con `priceAmountMicros`; si no se puede, se ocultan |
| «Revisión automática de tu uso cada 15 min» | «aproximadamente cada 15 minutos; Android puede retrasarla» |
| «Continuar con Mensual · $1.99 al mes» | «Suscribirme · {precio}/{período}», «Cambiar a Semestral», «Es tu plan actual», «Gestionar suscripción» |
| «Tu plan actual» solo como subtítulo | insignia «TU PLAN» (validado) distinta de la tarjeta seleccionada |
| «Precios de referencia en USD» | importe y moneda de la tienda, período, renovación, cómo cancelar, enlaces |
| Gratis: «10 minutos: solo con créditos» | hoy Gratis recibe 3 accesos de 10 min sin créditos (`UnlockLimits`, `Excuse.kt:45`) |

### Código frente a las decisiones

1. **Las emergencias gastan evaluaciones** cuando hay cupo (`take_llm_call` no mira el tipo). Decisión: nunca.
2. **Un fallo o timeout del LLM gasta la evaluación** (se reserva antes de llamar, `judge_graph.py:206`). Decisión: solo cuenta si Peel llega a responder.
3. **Juez y agente comparten contador**, con la reserva `agent_reserved_for_judge`. Decisión: contadores separados.
4. **`PUT /devices/me/plan` eleva el plan.**
5. **«Revisar ahora» (el análisis manual de Inicio) no está en las decisiones.** Lo trato como parte de la revisión automática: en Gratis no está disponible. Si no, Gratis tendría hasta 96 análisis diarios con el contador nuevo.
6. **El «día» difiere**: servidor UTC−5 fijo; el tope personal y los conteos locales usan la zona del teléfono. Coinciden en Colombia. Se deja así y la interfaz lo dice.
7. **Textos que quedan falsos**: `agent_never` («Reviso tu uso cada 15 minutos»), `settings_plan_hint`, `README.md:128` («Planes sin cobro»), `README.md:176`.
8. **Al desplegar el backend, los APK 0.4.0 ya instalados cambian de comportamiento**: sus planes elegidos a mano vuelven a Gratis y Gratis deja de recibir 10 minutos. No hay que fusionar a `production` hasta tener la versión de Play en pruebas.
9. **No existe documento de Términos** y `policy_url` apunta a una página de GitHub.
10. **`android:allowBackup="true"`** (`AndroidManifest.xml:32`): la identidad del teléfono puede restaurarse en otra instalación. Contradice «los créditos están ligados a la instalación» y, en la política, «se borra al desinstalar». DECIDIDO: pasa a `false`.

### Código frente a Google Play

Ver la tabla de riesgos.

## 4. Riesgos de política, por gravedad

| # | Riesgo | Gravedad | Fuente | Alternativa propuesta |
|---|---|---|---|---|
| 1 | El anti-autosabotaje usa el servicio de accesibilidad para tapar la página de Accesibilidad y el diálogo de desinstalar (`BlockerService.kt:175`, `SabotageGuard.kt:33`). | ALTA | https://support.google.com/googleplay/android-developer/answer/9888170 — la API no puede usarse para «prevent the ability for users to disable or uninstall any app or service», salvo control parental o empresa (cita obtenida por el subagente D) | DECIDIDO: la fricción queda solo dentro de la app y no se tapa ninguna ventana del sistema |
| 2 | Falta la divulgación destacada con consentimiento propio de Accesibilidad. La tarjeta de `SetupScreen` explica el uso, pero no pide aceptar ni permite rechazar. | ALTA (bloquea la revisión) | https://support.google.com/googleplay/android-developer/answer/10964491 — «Must require affirmative user action for consent»; no puede ir junto a otras divulgaciones | Pantalla propia con «Acepto» y «No acepto» antes de abrir Ajustes; declaración y vídeo en Play Console; párrafo en la ficha |
| 3 | `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` con petición directa (`SetupIntents.kt:30`). Un bloqueador no figura entre los casos aceptables. | ALTA | https://developer.android.com/training/monitoring-device-state/doze-standby — «Google Play policies prohibit apps from requesting direct exemption… unless the core function of the app is adversely affected» | Quitar el permiso y abrir `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` |
| 4 | El agente decide con IA iniciar una sesión de enfoque que el servicio de accesibilidad hace cumplir. | MEDIA | https://support.google.com/googleplay/android-developer/answer/10964491 — prohibido usar la API para «autonomously initiate, plan, and execute actions or decisions»; se permite «deterministic, rule-based automation» | Declarar que el servicio aplica una regla fija y que la IA no lo maneja. Si lo rechazan: el agente propone y el usuario confirma con un toque |
| 5 | Paywall: el equivalente mensual no puede destacar más que el importe cobrado; hay que mostrar renovación y cómo cancelar sin toques extra. | MEDIA | https://support.google.com/googleplay/android-developer/answer/9900533 — infracción: «Annual subscriptions that most prominently display their pricing in terms of monthly cost»; «clearly disclose how a user can manage or cancel» | Cifra grande = importe de la tienda; equivalente en texto secundario; enlace al centro de suscripciones |
| 6 | Cuenta personal nueva: prueba cerrada con 12 testers durante 14 días seguidos antes de pedir producción. | MEDIA (plazo) | https://support.google.com/googleplay/android-developer/answer/14151465 | Subir cuanto antes un build con Billing a la pista interna, que no tiene requisito |
| 7 | Política de privacidad: sin contacto, con TODO legal, alojada en GitHub. Términos inexistentes. | MEDIA | https://support.google.com/googleplay/android-developer/answer/10144311 — URL «publicly accessible and non-geofenced», «no PDFs» | Completar contacto, publicar en una URL web, redactar Términos |
| 8 | El uso de apps es dato sensible y hay que declararlo en Seguridad de los datos junto con id de instalación, texto de las razones e historial de compras. | MEDIA | https://support.google.com/googleplay/android-developer/answer/10787469 | La pantalla de autorizaciones ya lo pide por separado; falta el formulario |
| 9 | Prometer «cada 15 min». | BAJA | https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work — «The minimum repeat interval that can be defined is 15 minutes»; en Doze «WorkManager tasks don't run» | Texto honesto de las decisiones |
| 10 | Contenido de IA: se exige reporte dentro de la app. | BAJA | https://support.google.com/googleplay/android-developer/answer/13985936 | Ya existe (`report_action`); los reportes deben revisarse |
| 11 | targetSdk: se exige 36 o superior desde el 31-08-2026. El 37 cumple «or higher», pero no está confirmado que Play Console lo acepte. | BAJA | https://support.google.com/googleplay/android-developer/answer/11926878 | Mantener 37; bajar a 36 solo si la subida falla |
| 12 | Cobrar por desbloquear apps que el usuario bloqueó. | NO VERIFICADO | Ninguna cláusula de Pagos, Suscripciones ni Abuso de dispositivos lo prohíbe en lo leído; Comportamiento engañoso no se abrió | Explicar el crédito con exactitud antes de pagar |

Otros datos verificados que condicionan el código:

- **Play Billing Library**: la v7 venció el 31-08-2026 (prórroga hasta el 01-11-2026). Mínimo hoy: v8. Se integra `billing-ktx:9.1.0` (https://developer.android.com/google/play/billing/deprecation-faq).
- **Cambio de plan**: entre base plans de la misma suscripción solo valen `CHARGE_FULL_PRICE` y `WITHOUT_PRORATION`; con otro modo la compra falla (https://developer.android.com/google/play/billing/subscriptions).
- **Acknowledge** en 3 días o Google reembolsa; un cambio de plan se bloquea si la compra anterior sigue sin acknowledge.
- **Derecho por estado**: con acceso en `ACTIVE`, `IN_GRACE_PERIOD` y `CANCELED` con vencimiento futuro; sin acceso en `ON_HOLD`, `PAUSED`, `EXPIRED`, `PENDING` (https://developer.android.com/google/play/billing/lifecycle/subscriptions).
- **RTDN**: la notificación no trae el estado; hay que consultar siempre la API. Pub/Sub acusa con 102, 200, 201, 202 o 204.
- **Sin verificar** y se comprueba al implementar: nombres exactos de `SubscriptionProductReplacementParams`, declaraciones de apps de salud y de ID de publicidad, el cambio de la política de Suscripciones anunciado para el 27-01-2027.

## 5. Lo que necesita tu confirmación

Ya confirmado por ti:

- **Anti-autosabotaje: solo dentro de la app.** El servicio deja de tapar la página de Accesibilidad y el diálogo de desinstalar. La protección se apaga dentro de la app escribiendo una frase fija de confirmación (tu idea), en lugar de la razón de 15 letras; se conserva la opción de programarla para 24 horas después.
- **Los créditos siguen al plan.** Cuando el servidor liga una suscripción a una instalación nueva, traslada también el saldo de créditos de la anterior. Quien compra créditos sin plan los pierde al desinstalar, y se le avisa antes de comprar. `allowBackup` pasa a `false`.
- **El plan nunca se pierde**: vive en la cuenta de Google y se recupera al reinstalar.

Interpretaciones que aplico salvo que digas otra cosa:

- **Gratis no tiene «Revisar ahora»**, además de no tener revisión automática.
- **`PUT /devices/me/plan` se conserva en el servidor** guardando solo `would_pay`, porque los APK 0.4.0 instalados lo siguen llamando. La app nueva deja de usarlo y se quita la encuesta «¿Pagarías?»: con precios reales, las compras responden esa pregunta.
- **Términos**: creo `docs/terminos.md` como borrador marcado con revisión legal pendiente y lo enlazo igual que la política.
- **`CLAUDE.md`**: no hay uno en la raíz; el del proyecto está en `.claude/CLAUDE.md`. Añado ahí las secciones sin borrar nada.
- **Costo de las emergencias**: ya no gastan evaluaciones. Su gasto de IA queda acotado por el tope personal (máximo 5 al día), el tope de 5 rechazos y el límite de 30 por hora; no invento un contador nuevo.
- **Insignia de ahorro**: redondeo normal (29,7 % se muestra como 30 %).
- **Cumplimiento de Play**: los riesgos 2 y 3 se corrigen en la Fase 2 (paso 9), porque el revisor de cumplimiento los marcaría como bloqueantes.

## 6. Plan de la Fase 2

### Paso 0 — Configuración reutilizable

- `.claude/CLAUDE.md`: secciones Identidad, Planes y Comandos del encargo.
- `.claude/agents/play-compliance-reviewer.md`: tal cual el encargo.
- `docs/planes/ESTADO.md`: avance con IMPLEMENTADO, PROBADO o PENDIENTE.

### Paso 1 — Nombres

Los cambios de la tabla del apartado 2: `strings.xml`, `prompts.py:43`, `judge_graph.py:57-60`, primera mención en README y política.

### Paso 2 — Configuración central de planes

- **Android**: `domain/billing/PlanCatalog.kt` con los tres planes (evaluaciones, accesos incluidos, revisión automática), `SUBSCRIPTION_ID = "premium"`, base plans `monthly` y `semester`, `CREDITS_PRODUCT_ID = "creditos_10"`, `CREDITS_PER_PACK = 10` y precios de respaldo solo para `BuildConfig.DEBUG`.
- **Backend**: en `banano/config.py`, `plan_included_accesses_paid = 3`, `agent_daily_cap = 96`, los ids de Play y `Settings.benefits(plan)`. `plan_limit` de `quota.py` pasa a leer de ahí. Se elimina `agent_reserved_for_judge`.

### Paso 3 — Backend

**`backend/sql/007_billing.sql`** (idempotente, estilo de los anteriores):

- `ALTER TABLE device_daily_usage ADD COLUMN IF NOT EXISTS agent_calls`.
- `entitlements`: `purchase_token` (clave primaria), `device_id` (el dispositivo ligado), `product_id`, `plan`, `state`, `expiry_time`, `auto_renewing`, `linked_purchase_token`, `updated_at`.
- `credit_ledger`: `device_id`, `delta`, `kind` (`purchase`, `access`, `refund`), `ref` (token de compra o `request_id`), `created_at`, con `UNIQUE (kind, ref)`. El saldo es la suma; un acceso incluido se anota con `delta = 0`, así los incluidos de hoy se cuentan desde el mismo registro.

**`banano/agent/store.py`** (protocolo, `UnconfiguredStore`, `MemoryStore` y `PostgresStore`):

- `DAILY_COUNTERS` suma `agent_calls`; `DEVICE_TABLES` suma `credit_ledger` y `entitlements`.
- `get_device` devuelve además créditos, incluidos usados hoy, estado y vencimiento del derecho, y el plan efectivo: el del derecho si está vigente, si no `free`.
- Métodos nuevos: `save_entitlement` (alta o actualización por token; re-liga al dispositivo actual, traslada a él el saldo de créditos del dispositivo anterior e invalida el `linked_purchase_token`), `find_entitlement`, `device_by_account_hash`, `grant_credits` (idempotente por token), `spend_access` (una sola sentencia: incluido si queda, si no crédito, idempotente por `request_id`), `refund_credits`, `set_would_pay` (sustituye a `set_plan`).

**`banano/quota.py`**:

- El juez comprueba sin reservar; `llm_calls` sube solo cuando Peel responde a una petición de 10 min (mismo patrón que `templates`, `routes.py:248`).
- Las emergencias no miran el cupo del plan.
- El agente usa `agent_calls` con el tope del plan (0 en Gratis).
- Motivo nuevo `no_credits`: no hay acceso incluido ni crédito, y se responde sin llamar al LLM.

**`banano/billing.py`** (nuevo):

- `PlayClient` (protocolo) y `GooglePlayClient` con `httpx` y `google-auth`: `subscriptionsv2.get`, `subscriptions.acknowledge`, `products.get`, `products.consume`.
- Función pura que convierte la respuesta de Google en derecho (plan por `basePlanId`, estado, vencimiento).
- `verify_subscription`, `verify_credits` y `handle_notification`. Los créditos solo se conceden con `purchaseState = 0` y `consumptionState = 0`, y después se consume.

**`banano/api/routes.py`**:

- `POST /billing/verify` (`ManagedDevice`): valida, guarda, hace acknowledge o consume, devuelve `DeviceStatus`.
- `POST /billing/rtdn`: verifica el JWT (`id_token.verify_oauth2_token`, audiencia y correo configurados), decodifica `message.data`, vuelve a leer la API y aplica. Responde 204 al guardar o al recibir algo irrelevante; 503 si falla la base o Google, para que Pub/Sub reintente.
- `PUT /devices/me/plan`: ignora `plan`, guarda `would_pay`.
- `POST /excuses/judge`: `JudgeRequest.request_id` opcional; tras aprobar 10 min llama a `spend_access`; la respuesta añade `access_source`, `credits` e `included_remaining`. Un `request_id` repetido devuelve la aprobación ya pagada sin evaluar ni cobrar otra vez.
- `DeviceStatus` añade `subscription_state`, `expiry_time`, `auto_renewing`, `credits`, `included_daily`, `included_remaining` y `agent_enabled`.

**Resto**: `main.py` y `api/deps.py` inyectan el cliente de Play como hoy los LLM; `pyproject.toml` suma `google-auth` y `requests`; `.env.example` suma `GOOGLE_PLAY_PACKAGE_NAME`, `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`, `RTDN_AUDIENCE`, `RTDN_SERVICE_ACCOUNT_EMAIL`, `AGENT_DAILY_CAP` y `PLAN_INCLUDED_ACCESSES_PAID`, sin valores, y quita `AGENT_RESERVED_FOR_JUDGE`.

### Paso 4 — Android: facturación

- `gradle/libs.versions.toml` y `app/build.gradle.kts`: `com.android.billingclient:billing-ktx:9.1.0`.
- `data/billing/BillingRepository.kt`: interfaz y modelos de estado (cargando productos, compra en curso, pendiente, cancelada por el usuario, error con reintento).
- `data/billing/PlayBillingRepository.kt`: `BillingClient` con reconexión automática y compras pendientes activadas; `queryProductDetailsAsync`; `launchBillingFlow` con `obfuscatedAccountId` = SHA-256 del `device_id`; cada compra `PURCHASED` va a `/billing/verify`; `PENDING` no da nada.
- **Modo de reemplazo**: Mensual → Semestral con `CHARGE_FULL_PRICE`; Semestral → Mensual con `WITHOUT_PRORATION`. Son los dos únicos modos válidos entre base plans de la misma suscripción y ninguno cobra dos veces.
- `di/BillingModule.kt`: enlaza la interfaz. `FakeBillingRepository` en los tests.
- `LaVidaApi` y `dto/`: `POST billing/verify`; `DeviceStatusDto` con los campos nuevos; se borran `updatePlan`, `PlanRequestDto` y `DeviceRepository.setPlan`.
- **Restauración**: `MainActivity.onResume` → `MainViewModel` → `queryPurchasesAsync` → `/billing/verify`.
- `DeviceRepository.status()` guarda el último estado validado en `DeviceStore`.

### Paso 5 — Accesos y créditos en el bloqueo

- `BlockViewModel.open` pide el estado sin bloquear la pantalla. Al tocar «Pedir 10 minutos»:
  - sin evaluaciones: «Hoy no quedan evaluaciones. Mañana vuelven.» y «Ver planes»; las emergencias siguen;
  - sin acceso incluido ni créditos: «Comprar créditos» y «Ver planes»;
  - sin estado (sin red): sigue como hoy.
- El tope personal `extraTimeDaily` no cambia: se aplica antes, en el teléfono (`ExcuseRepository.quotaToday`) y en el prefiltro del servidor (`over_quota`), y ningún crédito lo supera.
- `ExcuseRepository.judge` envía `request_id` y lo reutiliza si el intento anterior quedó sin respuesta, para no pagar dos veces.
- `VerdictReason.NO_CREDITS`, `VerdictText.kt` y la pantalla de veredicto muestran con qué se pagó y cuánto queda.
- «Comprar créditos» y «Ver planes» abren `MainActivity` en la pantalla de planes, con el mismo mecanismo que `EXTRA_OPEN_SETUP`.

### Paso 6 — Revisión automática condicionada al plan

- Interfaz `AgentSchedule` con implementación de WorkManager (`enqueueUniquePeriodicWork` y `cancelUniqueWork`). `DeviceRepository.status()` la llama con `agent_enabled`.
- Se quita la programación incondicional de `LaVidaApp.kt:14`. `AgentCheckWorker.doWork` se cancela a sí mismo si el plan guardado no es de pago.
- Inicio: texto «Peel revisa tu uso aproximadamente cada 15 minutos. Android puede retrasar la revisión para ahorrar batería.»; en Gratis, la tarjeta remite a «Ver planes».

### Paso 7 — Pantalla de planes y Ajustes

- `feature/plans/PlansScreen.kt` y `PlansViewModel.kt`; ruta `PlansRoute` en `navigation/LaVidaNavHost.kt`.
- Piezas reutilizadas: `ScreenColumn`, `toon`, `dashed`, `MonoLabel`, `RowDivider`, `PrimaryButton`, `SecondaryButton`, `TextLink`, `PolicyLink`. Las tarjetas son un composable privado de la pantalla, con radio a la izquierda y precio a la derecha como en la imagen.
- Insignia «TU PLAN» en el plan validado; relleno amarillo y radio marcado en el seleccionado.
- Botón principal según selección y estado, desactivado durante un flujo en curso; nunca ofrece el plan activo.
- Bajo el botón: importe y moneda de `formattedPrice`, período, «Se renueva automáticamente hasta que la canceles», cómo cancelar, enlace `https://play.google.com/store/account/subscriptions?sku=premium&package=com.lavidanoesunbanano.app`, Privacidad y Términos.
- Avisos por estado: en gracia (beneficios y aviso de pago), en suspensión (sin beneficios y enlace), cancelada vigente hasta la fecha, vencida.
- «¿Qué son los créditos?»: se despliega con las reglas exactas, el saldo, «Se reinician a medianoche (hora de Colombia)», el aviso «Con un plan activo, tus créditos te siguen si reinstalas. Sin plan, se pierden al desinstalar o borrar tus datos» y «Comprar 10 créditos · {precio}».
- `SettingsScreen.kt`: `PlanSection`, `PLANS` y `PaySurvey` se sustituyen por un resumen (plan y estado, evaluaciones restantes, accesos incluidos, créditos) y el botón «Ver planes». Se borran `selectPlan` y `answerWouldPay`.
- `settings_erase_warning` avisa de que se pierden los créditos y de que la suscripción se cancela en Google Play.

### Paso 8 — Documentación

- `docs/politica-de-datos.md`: qué guarda el servidor de los pagos (token de compra, producto, estado, vencimiento, registro de créditos), que el cobro lo procesa Google Play y qué pasa al borrar los datos.
- `docs/terminos.md` (borrador), `README.md` (filas de planes, endpoints y variables), `docs/planes/ESTADO.md` e `INFORME.md`.

### Paso 9 — Bloqueantes de Google Play

- Quitar `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` del manifest y abrir `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` en `SetupIntents.kt`.
- Divulgación de Accesibilidad con «Acepto» y «No acepto» antes de abrir Ajustes, en `feature/setup`.
- **Anti-autosabotaje solo dentro de la app.** Se borra lo que tapa ventanas del sistema: `BlockerService.guardedWindow` y la lectura de `event.text`, `SabotageGuard.isSabotage` y `SYSTEM_SETTINGS`, `BlockRequest.sabotage`, `BlockStep.Sabotage` y su pantalla, y los strings `sabotage_label`, `sabotage_brand` y `setup_protection_note`. Con eso el servicio ya solo mira qué app pasa al frente. Se conserva `SabotageGuard.rulesLocked` (reglas bajo llave en horario de bloqueo) y la salida de Ajustes, donde `canDisableNow` pasa a exigir la frase fija. Se actualizan `settings_anti_sabotage_desc` y los textos de la salida.
- `android:allowBackup="false"` en `AndroidManifest.xml`.
- Ejecutar el subagente `play-compliance-reviewer` sobre todos los cambios, corregir lo bloqueante e incluir su informe.

### Tests

**Backend** (`cd backend && uv run pytest`), en `tests/test_billing.py` nuevo y `tests/test_quota.py`, `tests/test_store.py` actualizados:

- cupos por plan y contadores separados; medianoche de Colombia;
- un fallo del LLM no gasta evaluación; una emergencia no gasta evaluación;
- verify con la API de Play simulada; idempotencia por `purchaseToken`; créditos no concedidos si la compra está pendiente o ya consumida;
- RTDN para cada estado, JWT inválido rechazado;
- créditos: no se descuenta si Peel rechaza ni cuando quedan accesos incluidos; `request_id` repetido no cobra dos veces;
- re-ligado al reinstalar, con el saldo de créditos trasladado; `linked_purchase_token` invalidado;
- `PUT /devices/me/plan` no eleva el plan.

Los tests actuales que juzgan 10 min con un dispositivo Gratis necesitan saldo: `consented_store` de `tests/fakes.py` lo siembra por defecto y los tests nuevos lo ponen en cero. Cambian de sentido `test_a_failed_llm_still_used_the_call`, `test_without_quota_an_emergency_is_approved_without_llm`, `test_a_periodic_analysis_leaves_the_last_call_for_the_judge` y `test_plan_can_be_changed_and_keeps_the_answer`.

**Android** (`cd android && ./gradlew testDebugUnitTest lint assembleRelease`):

- mapeo de estado del servidor y de compra a derecho;
- `PlansViewModelTest` con `FakeBillingRepository`: sin compras duplicadas, selección frente a plan actual, botón por estado;
- `ExcuseRepositoryTest`: tope personal frente a créditos, `request_id` reutilizado;
- `DeviceRepositoryTest`: worker programado o cancelado según el plan (con `AgentSchedule` falso);
- se adaptan `DeviceRepositoryTest.kt:119` y `SabotageGuardTest` (fuera los casos de `isSabotage`, dentro la frase fija); el resto, en verde.

No hay tests de ViewModel hoy: se añade una regla mínima de `Dispatchers.setMain` en los tests.

## 7. Verificación

1. `cd backend && uv run pytest` y `uv run ruff check`.
2. `cd android && ./gradlew testDebugUnitTest lint assembleRelease`.
3. En un teléfono con build de depuración: pantalla de planes con precios de respaldo, los cuatro textos del botón, Ajustes con el resumen, bloqueo sin créditos y sin evaluaciones, nombres a 360 dp.
4. Migración contra Postgres local: `docker compose up -d db` con `TEST_DATABASE_URL`.
5. **No se puede probar aquí**: compras reales, renovaciones, RTDN y cambio de plan. Requieren la app en Play Console, productos creados y testers de licencia.

## Pendiente fuera del código

- **Play Console**: app creada; suscripción `premium` con `monthly` y `semester`; consumible `creditos_10`; precios por país; período de gracia y suspensión; pausa; tema de Pub/Sub; cuenta de servicio con permisos de finanzas y de pedidos; testers de licencia; declaración y vídeo de Accesibilidad; Seguridad de los datos; URL pública de privacidad; clasificación y público objetivo (18+); prueba cerrada de 12 testers durante 14 días.
- **Infraestructura**: variables en Vercel; suscripción push de Pub/Sub hacia `/billing/rtdn` con cuenta de servicio y audiencia; despliegue de prueba separado de `production`.
- **Legal**: revisión de la política de datos y de los Términos; contacto del responsable.

## Referencia visual (SVG recibido, 390 × 1180)

1. Miga «Ajustes › Planes» (mono, 13).
2. Título «Elige tu plan» (32, peso 800) y bajada de una frase (15).
3. Caja de borde discontinuo (radio 14): rótulo mono «TODOS LOS PLANES INCLUYEN»; ✓ «Bloqueo de apps y tus mensajes personalizados»; ✓ «Emergencias reales, siempre gratis».
4. Tres tarjetas en grupo de radio (radio 18, borde de 2 px, sombra dura): título, subtítulo, precio grande en mono a la derecha, línea divisoria y tres ventajas con ✓ verde o ✕ rojo. La seleccionada va en amarillo, con el radio marcado y sombra de 4 px. Semestral lleva una insignia en píldora de tinta.
5. Enlace subrayado «¿Qué son los créditos?».
6. Botón principal en píldora amarilla (58 de alto).
7. Letra pequeña centrada y un enlace secundario.

Colores: fondo `#FFFDF7`, tinta `#1F1A14`, amarillo `#FACC15`, verde `#1F7A3A`, rojo `#BA1A1A`, texto secundario `#3D352B` y `#4A4136`. Son los de `LaVidaPalette` (`core/designsystem/Theme.kt:48`).
