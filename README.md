<p align="center">
  <img src="docs/assets/banner.svg" alt="La vida no es un banano: agente de IA que te ayuda a reducir el uso del celular" width="100%">
</p>

<p align="center">
  <a href="https://reto.pltk.mx/"><img alt="Reto Agente 2026 · PLATICA MX" src="https://img.shields.io/badge/Reto_Agente_2026-PLATICA_MX-FACC15?style=flat-square&labelColor=1E1E24"></a>
  <img alt="Android 8.0+ · Kotlin" src="https://img.shields.io/badge/Android_8.0%2B-Kotlin_·_Compose-1E1E24?style=flat-square&logo=android&logoColor=FACC15">
  <img alt="FastAPI + LangGraph" src="https://img.shields.io/badge/Backend-FastAPI_·_LangGraph-1E1E24?style=flat-square&logo=fastapi&logoColor=FACC15">
  <img alt="Grok vía gateway del reto" src="https://img.shields.io/badge/LLM-Grok_vía_gateway-1E1E24?style=flat-square">
  <img alt="PostgreSQL en Neon" src="https://img.shields.io/badge/Datos-PostgreSQL_·_Neon-1E1E24?style=flat-square&logo=postgresql&logoColor=FACC15">
  <a href="https://la-vida-no-es-un-banano.vercel.app"><img alt="Descargar APK 0.4.0" src="https://img.shields.io/badge/APK-0.4.0-FACC15?style=flat-square&labelColor=1E1E24"></a>
</p>

<p align="center">
  <b>LaVidaNoEsUnBanano</b>: un agente que te ayuda a reducir el uso del celular.<br>
  Proyecto de Sergio Daniel Flórez López para el <a href="https://reto.pltk.mx/">Reto Agente 2026 de PLATICA MX</a>.
</p>

<p align="center">
  <a href="https://youtu.be/lcLKj5QDe-Y"><b>Video del agente</b></a> ·
  <a href="https://la-vida-no-es-un-banano.vercel.app"><b>Descargar la app</b></a> ·
  <a href="#reto-agente-2026"><b>Hitos del reto (jueces)</b></a>
</p>

---

Bloqueador de apps para Android con un agente de IA de sarcasmo personalizado. Cuando abres una app bloqueada en horario restringido, aparece una pantalla con un mensaje hecho a tu medida y tres salidas: salir, emergencia o 10 minutos más. Las dos últimas las decide un juez de IA, Peel. Desde la versión 0.3.0, un agente de intervención revisa además tu uso real del teléfono y decide si hace falta actuar.

La idea central: la vida es mucho más que estar en el celular. La app no compite por tu atención; aparece, dice lo que tiene que decir y se quita del camino.

## Reto Agente 2026

<p align="center">
  <img src="docs/assets/hitos.svg" alt="Progreso de hitos: S1 y S2 implementados con aprobación por confirmar; S3 y S4 próximos" width="100%">
</p>

Este proyecto participa en el **[Reto Agente 2026 de PLATICA MX](https://reto.pltk.mx/)**, Track 1: cuatro hitos semanales a partir del 5 de octubre. Cada hito se documenta con la misma estructura. Lo que no tiene evidencia en el repositorio aparece como **Pendiente de completar**.

**Para jueces:** cada hito tiene una tabla **Requerimientos** que enlaza la evidencia de cada requerimiento del reto: [S1](#hito-1-agente-corriendo-end-to-end) · [S2](#hito-2-3-herramientas-y-video-de-2-min) · [siguientes](#hitos-siguientes).

| Hito | Nombre | Estado | Commit | Video | Reporte | Nota de entrega |
|---|---|---|---|---|---|---|
| [S1](#hito-1-agente-corriendo-end-to-end) | Agente corriendo end-to-end | Implementado · aprobación por confirmar | [`0af6038`](https://github.com/sergioRepository/ProyectoPlatica/commit/0af6038a61448ea9b6545cf723548ca44a0f569c) | [YouTube](https://www.youtube.com/watch?v=uU0Go189CdI) | [Reporte](docs/reporte-tecnico-hito-1.md) | Pendiente |
| [S2](#hito-2-3-herramientas-y-video-de-2-min) | 3 herramientas y video de 2 min | Implementado · aprobación por confirmar | [`f9760fe`](https://github.com/sergioRepository/ProyectoPlatica/commit/f9760fe81f73925e900a7bc02388cf25979eef2d) | [YouTube](https://youtu.be/lcLKj5QDe-Y) | [Reporte](docs/reporte-tecnico-hito-2.md) | Pendiente |
| S3 | Datos reales | Próximo | | | | |
| S4 | Semana de carga | Próximo | | | | |

### Hito 1: Agente corriendo end-to-end

| | |
|---|---|
| **Descripción general** | Primera versión completa del producto: backend en FastAPI con dos grafos de LangGraph (generador de mensajes y juez de excusas) que llaman a Grok a través del gateway, y app Android que los consume desde el onboarding hasta la pantalla de bloqueo. El backend se desplegó en Vercel y el APK de release apunta a producción. |
| **Objetivos** | Demostrar un agente funcionando de punta a punta contra Grok, con el repositorio público y un README que explique el problema. |
| **Implementación y resultados** | Se construyó por fases, cada una en su commit: monorepo y esqueleto (`af7c9d8`), backend con grafos, API y pruebas (`a926fd7`), onboarding y generación de la plantilla (`6d49bf9`), servicio de accesibilidad y pantalla de bloqueo (`f29e893`), juez de excusas con cupos y modo sin conexión (`2d99c35`), estadísticas, historial y ajustes (`893f751`), endurecimiento, guía de permisos y anti-autosabotaje (`b5ab6a0`), y APK de release contra Vercel (`0af6038`). |
| **Commit asociado** | [`0af6038`: APK de release contra Vercel, enlazado en el README](https://github.com/sergioRepository/ProyectoPlatica/commit/0af6038a61448ea9b6545cf723548ca44a0f569c) |
| **Video de demostración** | [youtube.com/watch?v=uU0Go189CdI](https://www.youtube.com/watch?v=uU0Go189CdI) |
| **Reporte técnico** | [docs/reporte-tecnico-hito-1.md](docs/reporte-tecnico-hito-1.md) |
| **Nota de entrega** | Pendiente de completar |

**Requerimientos**

| Requerimiento del reto | Cómo se abordó |
|---|---|
| Agente funcionando de punta a punta contra Grok | Grafos de plantilla y juez llamando a Grok por el gateway, consumidos por la app con el backend en Vercel ([El agente](#el-agente)). |
| Repositorio público | [github.com/sergioRepository/ProyectoPlatica](https://github.com/sergioRepository/ProyectoPlatica) |
| README que describa el problema | Sección [El problema](#el-problema). |

### Hito 2: 3 herramientas y video de 2 min

| | |
|---|---|
| **Descripción general** | Agente de intervención: un tercer grafo de LangGraph que decide con llamadas nativas a herramientas si consulta el uso real del teléfono, el historial de intervenciones o si ejecuta una sesión de enfoque. Se integra con el bloqueador existente: la orden viaja en la respuesta, Android la aplica con las reglas de bloqueo que ya tenía y confirma el resultado. |
| **Objetivos** | Pasar de decisiones predefinidas a un agente que observa evidencia real, elige qué herramientas necesita, actúa dentro de límites explícitos y registra lo que ocurrió, incluida la decisión de no actuar. |
| **Implementación y resultados** | Evidencia de uso con `UsageStatsManager` y el permiso `PACKAGE_USAGE_STATS` como paso de la guía; historial en PostgreSQL (Neon en producción) con creación automática de tablas; selección de herramientas con `bind_tools`; política determinista e idempotencia por `request_id`; confirmación desde Android y APK 0.3.0. Las pruebas usan dobles del LLM y de la base (`test_agent_graph.py`, `test_agent_tools.py`, `test_agent_api.py` y `test_store.py`, que también corre contra Postgres real). Queda sin hacer: el resultado observado (`observed_outcome`) existe en la tabla pero queda vacío. |
| **Commit asociado** | [`f9760fe`: La vida no es un banano, versión de producción](https://github.com/sergioRepository/ProyectoPlatica/commit/f9760fe81f73925e900a7bc02388cf25979eef2d) |
| **Video de demostración** | [youtu.be/lcLKj5QDe-Y](https://youtu.be/lcLKj5QDe-Y) |
| **Reporte técnico** | [docs/reporte-tecnico-hito-2.md](docs/reporte-tecnico-hito-2.md) |
| **Evidencia de ejecuciones** | [docs/s2-ejecuciones.md](docs/s2-ejecuciones.md) |
| **Nota de entrega** | Pendiente de completar |

**Requerimientos**

| Requerimiento del reto | Cómo se abordó |
|---|---|
| 3 herramientas | `consultar_evidencia_uso`, `consultar_historial_intervenciones` y `ejecutar_intervencion`, conectadas a datos y acciones reales ([Agente de intervención](#3-agente-de-intervención-post-agentanalyze)). |
| Manejo de errores | `permission_missing`, `no_data` frente a `unavailable`, `agent_unavailable` ante fallo o timeout del LLM, códigos de política y `unknown` cuando falta la confirmación. |
| 10 ejecuciones | [docs/s2-ejecuciones.md](docs/s2-ejecuciones.md): 10 ejecuciones reales exportadas de `agent_events` en producción (38 en total al 2026-11-10, 6 teléfonos), sin `device_id`. |
| Video de 2 minutos | [youtu.be/lcLKj5QDe-Y](https://youtu.be/lcLKj5QDe-Y) (1:58): grabado en vivo contra producción; muestra las 3 herramientas, el error `permission_missing` y la recuperación en la siguiente ejecución, el modo sin red y las 38 ejecuciones registradas. |

### Hitos siguientes

| Hito | Requerimientos del reto | Entregable |
|---|---|---|
| S3 · Datos reales | Set de evaluación propio de más de 30 casos | Reporte de costo por tarea |
| S4 · Semana de carga | 1,000 unidades de trabajo y 12 horas de ejecución desatendida | Bitácora completa |

> Para documentar un hito nuevo, copia la tabla de un hito anterior, agrega su fila al resumen y cambia su estado en `docs/assets/hitos.svg`.

## Contenido

- [Reto Agente 2026: hitos y requerimientos](#reto-agente-2026)
- [El problema](#el-problema)
- [Funcionalidades](#funcionalidades)
- [Arquitectura](#arquitectura)
- [El agente](#el-agente)
- [Tecnologías](#tecnologías)
- [Estructura del repositorio](#estructura-del-repositorio)
- [Conexión con el gateway del reto](#conexión-con-el-gateway-del-reto)
- [Instalar en el teléfono](#instalar-en-el-teléfono)
- [Cómo probar](#cómo-probar)
- [Ética y privacidad](#ética-y-privacidad)
- [Créditos](#créditos)

## El problema

Abrir TikTok o Instagram "solo un ratito" es un gesto automático. Los bloqueadores tradicionales se saltan con un toque porque no tienen nada que decir. Este agente conoce tu meta y tus excusas, y te las devuelve justo en el momento en que estás por romper tu propio compromiso.

## Funcionalidades

| | Qué hace | Dónde vive |
|---|---|---|
| **Bloqueo por horario** | Detecta la app en primer plano y muestra la pantalla de bloqueo encima, con salir, emergencia o 10 minutos más. | `BlockerService` (`AccessibilityService`) y `BlockActivity` |
| **Mensajes a tu medida** | Una plantilla de unos 60 mensajes generada a partir del onboarding y guardada en el teléfono: funciona sin internet. | `POST /templates/generate` y Room |
| **Juez de excusas** | Decide si una emergencia o un "10 minutos más" se justifica, con cupos diarios y una regla sin conexión. | `POST /excuses/judge` y `OfflineVerdict` |
| **Agente de intervención** | Revisa el uso real de las últimas 24 h y el historial, y puede iniciar una sesión de enfoque dentro de límites fijos, o decidir no actuar. | `POST /agent/analyze` y `AgentCheckWorker` |
| **Estadísticas e historial** | Intentos por app, excusas aprobadas y rechazadas, e historial de bloqueos. | `feature/stats` y `feature/history` |
| **Anti-autosabotaje** | En tu horario de bloqueo deja bajo llave las apps, el horario y los cupos dentro de la app. Siempre puedes quitar la llave: ya mismo escribiendo una frase de confirmación, o programándolo para dentro de 24 horas. No tapa ninguna pantalla del sistema. | `SabotageGuard` y `ProtectionRepository` |
| **Planes y créditos** | Gratis, Mensual y Semestral, cobrados por Google Play y validados en el servidor. Los planes de pago suben las evaluaciones de Peel, incluyen 3 accesos de 10 minutos al día y la revisión automática. Los créditos pagan accesos extra. | `/billing/verify`, `/billing/rtdn`, `banano/billing.py`, `banano/quota.py` y `feature/plans` |
| **Medición** | Tokens, costo y latencia de cada llamada a Grok, y eventos de uso de la app si los autorizas. | `llm_usage`, `app_events` y `backend/sql/queries/` |
| **Privacidad** | Autorizaciones por separado, borrado total de tus datos y reporte de mensajes de la IA. | `feature/consent`, `DELETE /devices/me` y `POST /reports` |

## Arquitectura

```mermaid
flowchart LR
  subgraph Android["App Android (Kotlin · Compose)"]
    A11y["AccessibilityService<br/>app en primer plano"]
    Block["BlockActivity<br/>pantalla de bloqueo"]
    Room[("Room<br/>perfil · plantilla · historial")]
    Usage["UsageStatsManager<br/>uso de 24 h"]
    Worker["AgentRepository<br/>worker cada 15 min o Analizar ahora"]
  end

  subgraph API["lavidanoesunbanano-api (FastAPI en Vercel)"]
    TG["Grafo de plantilla"]
    JG["Grafo del juez"]
    AG["Grafo del agente<br/>3 herramientas + política"]
  end

  Grok["Grok<br/>gateway del reto"]
  PG[("PostgreSQL<br/>Neon")]

  A11y --> Block
  Block <--> Room
  Room -->|"POST /templates/generate"| TG
  Block -->|"POST /excuses/judge"| JG
  Usage --> Worker
  Worker -->|"POST /agent/analyze"| AG
  Worker -->|"POST /agent/interventions/{id}/result"| AG
  TG --> Grok
  JG --> Grok
  AG --> Grok
  AG <--> PG
  JG <-->|"cupos"| PG
```

El bloqueo y los mensajes no dependen del backend: la plantilla vive en Room y la regla sin conexión del juez corre en el teléfono. El backend solo entra para generar la plantilla, juzgar excusas, analizar el uso y guardar lo que el usuario autorizó. La llave del gateway nunca sale de él.

### Endpoints

| Método | Ruta | Uso |
|---|---|---|
| `GET` | `/health` | Estado del servicio |
| `POST` | `/devices/register` | Registro automático con las autorizaciones de datos; devuelve el id generado por el servidor y el token. Solo pide `enrollment_code` si el servidor define `ENROLLMENT_CODE`. |
| `GET` | `/devices/me` | Plan, llamadas de IA de hoy y autorizaciones |
| `PUT` | `/devices/me/plan` | Solo guarda la respuesta a "¿Pagarías?" de versiones anteriores; ya no cambia el plan |
| `POST` | `/billing/verify` | Valida una compra de Google Play y aplica el plan o los créditos |
| `POST` | `/billing/rtdn` | Notificaciones de Google Play por Pub/Sub (push con JWT verificado) |
| `PUT` | `/devices/me/consents` | Actualiza las autorizaciones; cada cambio queda como prueba |
| `DELETE` | `/devices/me` | Borra todos los datos del dispositivo en el servidor |
| `POST` | `/events` | Lote de hasta 100 eventos de comportamiento. Sin autorización de analítica no guarda nada. |
| `POST` | `/reports` | Reporte de un mensaje de la IA |
| `GET` | `/internal/retention` | Aplica la retención; lo llama el cron de Vercel con `CRON_SECRET` |
| `POST` | `/templates/generate` | Generador de mensajes |
| `POST` | `/excuses/judge` | Juez de excusas |
| `POST` | `/agent/analyze` | Análisis del agente de intervención |
| `POST` | `/agent/interventions/{event_id}/result` | Confirmación del teléfono: `executed` o `failed` |

## El agente

El backend (`lavidanoesunbanano-api`) tiene tres grafos de LangGraph que usan Grok a través del gateway del reto. Los modelos se configuran por variables de entorno.

### 1. Generador de mensajes (`POST /templates/generate`)

Recibe el perfil del onboarding (meta, apps, nivel de sarcasmo, jerga y temas prohibidos; las metas secundarias, el porqué y las excusas típicas son opcionales y se llenan en Ajustes) y lo procesa en tres nodos:

1. `analizar_perfil`: encuentra los ángulos de ataque, sobre todo las contradicciones entre tu meta y tus hábitos.
2. `generar_mensajes`: escribe unos 60 mensajes variados, con placeholders como `{app}`, `{meta}` o `{hora}`.
3. `revisar_calidad`: elimina repetidos, genéricos y cualquier cosa que viole los guardarraíles. Si quedan menos de 40, vuelve a generar (máximo 2 reintentos).

La plantilla se guarda en el teléfono, así que los bloqueos funcionan sin internet.

### 2. Juez de excusas (`POST /excuses/judge`)

Cuando pides "emergencia" o "10 minutos más", tienes que escribir por qué. El juez tiene cinco nodos, y solo uno llama a Grok:

1. `detectar_crisis`: si el texto describe una emergencia real de salud o seguridad, apaga el sarcasmo, aprueba con tono humano y la app muestra líneas de ayuda.
2. `prefiltro`: rechaza excusas de menos de 15 caracteres, repetidas o por encima del cupo diario.
3. `cuota`: revisa el tope de llamadas del plan y el de rechazos del día. Sin cupo responde como la regla sin conexión, con `plan_quota` o `rejection_cap`: la emergencia se aprueba y los 10 minutos se rechazan.
4. `evaluar`: Grok decide con criterio y se vuelve más estricto con cada intento del día.
5. `redactar_veredicto`: responde en el tono que elegiste, por ejemplo `RECHAZADO · aburrimiento`.

Reglas: máximo 3 aprobaciones de "10 minutos" y 2 emergencias (15 minutos) por día. Sin internet o con timeout de 8 segundos, la emergencia se aprueba y queda marcada para revisión, y el "10 minutos" se rechaza.

### 3. Agente de intervención (`POST /agent/analyze`)

Es el hito S2. Con un plan de pago, el teléfono pide el análisis aproximadamente cada 15 minutos (`AgentCheckWorker`, cuando las reglas locales lo justifican; Android puede retrasarlo) o con "Analizar ahora". Grok elige con llamadas nativas a herramientas (`bind_tools`) qué necesita consultar: no hay una secuencia fija, y puede terminar sin usar todas las herramientas.

```text
Teléfono -> POST /agent/analyze  {request_id, apps de la lista, enfoque activo, uso de 24 h}
            planificar (Grok) <-> herramientas    hasta 3 turnos, 6 s cada uno, 20 s en total
            cerrar: política -> guardar en agent_events -> respuesta
         <- {decision, reason, steps, intervention: {id, status, policy_code}}
Teléfono:   valida, inserta la sesión de enfoque, notifica
         -> POST /agent/interventions/{id}/result  {executed | failed}
```

| Herramienta | Qué hace | Fuente |
|---|---|---|
| `consultar_evidencia_uso` | Filtra las sesiones de uso por ventana y apps. Distingue `ok`, `permission_missing` y `error`. | `UsageStatsManager` en el teléfono: 24 h, las apps de la lista más las 5 más usadas, hasta 300 sesiones |
| `consultar_historial_intervenciones` | Hasta 10 intervenciones previas del mismo dispositivo. Distingue vacío (`no_data`) de caída (`unavailable`). | PostgreSQL (`agent_events`) |
| `ejecutar_intervencion` | Propone iniciar una sesión de enfoque (5, 10, 15, 20 o 30 min). Pasa por la política antes de enviarse. | Política determinista y confirmación de Android |

- **Decisiones posibles:** `intervene`, `no_action`, `insufficient_evidence`, `agent_unavailable` (fallo, timeout o turnos agotados del LLM: sin motivo ni acción) y `plan_quota` (sin llamadas de IA hoy, se responde sin llamar a Grok).
- **Política determinista** (`backend/banano/agent/policy.py`): el modelo nunca tiene la última palabra. Códigos: `no_evidence`, `duration_not_allowed`, `target_not_allowed`, `focus_already_active`, `daily_cap` (4 en 24 h) y `store_unavailable`. Android vuelve a validar antes de aplicar.
- **Estados de la intervención:** `sent`, `rejected`, `executed`, `failed` y `unknown`. Una intervención `sent` que no se confirma en 2 minutos se lee como `unknown`: nunca se da por ejecutada sin la confirmación del teléfono.
- **Idempotencia:** un `request_id` repetido devuelve la respuesta guardada sin volver a llamar al LLM.

### Guardarraíles

- Ataca la contradicción, nunca a la persona: nada sobre apariencia, salud mental, familia, religión, dinero ni los temas que el usuario prohíba.
- Cero emojis en toda la app y en las respuestas de la IA, con un filtro en el backend y un test que lo verifica.
- Salidas estructuradas validadas con Pydantic.

## Tecnologías

| Capa | Tecnologías |
|---|---|
| **Android** | Kotlin, Jetpack Compose con Material 3, Hilt, Room, DataStore, WorkManager, Retrofit con kotlinx.serialization y `AccessibilityService`. minSdk 26 (Android 8.0). |
| **Backend** | Python 3.12, FastAPI, LangGraph, `langchain-xai`, Pydantic, psycopg y uvicorn. Pruebas con pytest y estilo con ruff. |
| **IA** | Grok a través del gateway del reto (espejo de la API de xAI), con salidas estructuradas y llamadas nativas a herramientas. |
| **Datos** | Room en el teléfono y PostgreSQL para el historial del agente: Neon en producción y el servicio `db` de `docker-compose.yml` en local. |
| **Infraestructura** | Vercel para el backend en producción, Docker y `docker-compose` en local, y LangGraph Studio (`langgraph dev`) para inspeccionar los grafos. |

## Estructura del repositorio

```text
android/             App en Kotlin con Jetpack Compose, Room, Hilt y AccessibilityService
backend/             FastAPI y LangGraph, desplegado en Vercel
  banano/graphs/     Grafos de plantilla, juez y agente
  banano/agent/      Herramientas, política y store de Postgres del agente
  banano/quota.py    Topes diarios por plan
  sql/               Migraciones numeradas; se aplican solas al conectar
  sql/queries/       Consultas de análisis para Neon
  scripts/           smoke_grok, init_db y usage_report
  tests/             Pruebas con pytest
apk/                 APK de release
docs/                Política de datos y recursos gráficos del README
docker-compose.yml   Backend y Postgres en local
```

## Conexión con el gateway del reto

La llave vive solo en el backend: nunca está en el APK ni en el repositorio.

El gateway es un espejo de la API de xAI: solo cambian la URL base y la llave. El backend la lee de variables de entorno (`backend/.env`, que no se versiona; la plantilla es [`backend/.env.example`](backend/.env.example)):

| Variable | Uso |
|---|---|
| `XAI_BASE_URL` | URL base del gateway |
| `XAI_API_KEY` | Llave del gateway |
| `GROK_MODEL_GENERATE` | Modelo del generador de mensajes |
| `GROK_MODEL_JUDGE` | Modelo del juez |
| `DEVICE_TOKEN_SECRET` | Secreto para firmar los tokens de dispositivo |
| `DATABASE_URL` | PostgreSQL. Sin ella no se pueden registrar teléfonos ni guardar autorizaciones. |
| `ENROLLMENT_CODE` | Déjala vacía: el registro es abierto. Definirla cierra el registro si hay abuso. |
| `PLAN_LIMIT_FREE`, `PLAN_LIMIT_MONTHLY`, `PLAN_LIMIT_SEMESTER` | Evaluaciones de Peel al día de cada plan: 3, 20 y 20 |
| `DAILY_REJECTION_CAP` | Rechazos del día antes de que el juez responda sin Grok: 5 |
| `DAILY_TEMPLATE_CAP` | Plantillas generadas por día y dispositivo: 2 |
| `PLAN_INCLUDED_ACCESSES_PAID`, `AGENT_DAILY_CAP` | Accesos de 10 minutos incluidos al día y análisis automáticos al día en los planes de pago: 3 y 96 |
| `GOOGLE_PLAY_PACKAGE_NAME`, `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` | Paquete de la app y llave de la cuenta de servicio con acceso a la Play Developer API. Sin la llave, la facturación responde 503 |
| `RTDN_AUDIENCE`, `RTDN_SERVICE_ACCOUNT_EMAIL` | Audiencia y cuenta de servicio del push de Pub/Sub. Sin ellas, `/billing/rtdn` responde 503 |
| `PLAY_SUBSCRIPTION_ID`, `PLAY_BASE_PLAN_MONTHLY`, `PLAY_BASE_PLAN_SEMESTER`, `PLAY_CREDITS_PRODUCT_ID`, `CREDITS_PER_PACK` | Productos de Google Play: `premium`, `monthly`, `semester`, `creditos_10` y 10 |
| `LLM_PRICES` | Precios por modelo en JSON para calcular `cost_usd`. Sin precio, el costo queda en NULL. |
| `CRON_SECRET` | Secreto del cron diario de retención |
| `RETENTION_DAYS` | Días que se guardan `llm_usage`, `app_events` y `agent_events`: 90 |

En Vercel, las mismas variables van en Project Settings > Environment Variables.

## Instalar en el teléfono

Hoy se instala por APK. La versión para Google Play (0.5.1) está en prueba cerrada; ver `docs/planes/PLAY_CONSOLE.md`.

**Requisitos:** Android 8.0 o superior. Internet solo para el onboarding, el juez y el agente; los mensajes de bloqueo funcionan sin conexión.

**[Descargar desde la página oficial](https://la-vida-no-es-un-banano.vercel.app)** (versión 0.4.0, con el agente de intervención). La página trae los pasos de instalación. Usa el backend de producción en Vercel. La versión 0.2.0, sin el agente, sigue en [MediaFire](https://www.mediafire.com/file/t1x19xrl4jngmfi/la-vida-no-es-un-banano.apk/file).

> La versión 0.4.0, con autorizaciones de datos y registro automático, se compila desde este repositorio y aún no está publicada aquí. Cuando el backend se actualice, la 0.3.0 dejará de usar el juez y el agente: seguirá bloqueando, pero con la regla sin conexión.

1. Ábrelo en el teléfono. Android pide permitir instalar apps desde el navegador o el gestor de archivos.
2. En Samsung, si el Bloqueador automático (Auto Blocker) está activo, apágalo para instalar y vuelve a encenderlo después.
3. Abre la app, elige qué datos autorizas y responde el onboarding. El teléfono se registra solo: no hay código.
4. En Inicio, toca **Configurar** y sigue la guía. Como se instaló desde un archivo, en Android 13 o más reciente el primer paso es Permitir ajustes restringidos. Para el agente, la guía también pide el "Acceso a datos de uso".

Por USB: `adb install -r apk/la-vida-no-es-un-banano.apk`.

Las versiones hasta la 0.3.0 están firmadas con la llave de debug de la máquina que las compiló. Desde la 0.4.0, los APK para distribuir usan la llave de release del proyecto, que se configura en `android/keystore.properties` (no versionado). Al pasar de una firma a otra hay que desinstalar la versión anterior, y eso borra el perfil y el historial.

Android o Play Protect pueden bloquear la instalación desde internet porque la app usa el servicio de accesibilidad para bloquear otras apps. Por USB no aplica ese bloqueo.

## Cómo probar

```powershell
# Backend
uv --directory backend run pytest
docker compose up --build            # backend y Postgres en local

# Android (desde la raíz, con el JDK de Android Studio)
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
android\gradlew.bat -p android :app:testDebugUnitTest   # tests JVM
android\gradlew.bat -p android :app:assembleDebug       # APK debug (backend en localhost)
android\gradlew.bat -p android :app:assembleRelease     # APK contra el backend de producción
```

Para correr el contrato del store y las consultas de análisis contra Postgres real: `docker compose up -d db` y `$env:TEST_DATABASE_URL = "postgresql://banano:banano@localhost:5432/banano"`.

Para el resumen de consumo y comportamiento: `uv --directory backend run python -m scripts.usage_report --salida reporte-de-uso.md`. Solo lee.

## Ética y privacidad

La app te ayuda a reducir el uso del celular. No es un servicio médico ni de salud mental, y no diagnostica ni reemplaza a un profesional. La versión completa está en la [política de tratamiento de datos](docs/politica-de-datos.md).

- **Autorizaciones por separado (Ley 1581 de 2012 y Decreto 1377 de 2013).** Antes de registrar el teléfono se piden cuatro, sin marcar. Uso del teléfono y procesamiento fuera de Colombia son obligatorias. El texto de las excusas y la analítica son opcionales: las excusas pueden traer datos de salud, y ninguna función básica se condiciona a entregarlas. Sin esa autorización, el texto no sale del teléfono. El servidor guarda qué se autorizó, la versión del texto y la fecha.
- **Qué se guarda.** Perfil, plantilla e historial viven en el teléfono. El servidor procesa el perfil, las excusas autorizadas y el uso de 24 h, pero no guarda el texto de las excusas. Sí guarda decisiones del agente, consumo de tokens, plan, autorizaciones, eventos autorizados y reportes. Quién más procesa esos datos, con Vercel, Neon, el gateway de PLATICA MX y xAI, está en la política.
- **Retención y borrado.** `llm_usage`, `app_events` y `agent_events` se borran a los 90 días, y los reportes a los 180, con un cron diario. "Borrar mis datos" en Ajustes elimina todo en el servidor y en el teléfono.
- **Reportar contenido de la IA.** Se puede reportar un mensaje desde la pantalla de bloqueo y desde el veredicto, sin salir de la app.
- **Siempre se puede salir.** El anti-autosabotaje da fricción, no encierra: se desactiva ya escribiendo una frase de confirmación, sin aprobación de nadie, o se programa para dentro de 24 horas. La app nunca impide desactivar el servicio ni desinstalarla.
- **Crisis.** Si una excusa describe una emergencia o autolesión, se apaga el sarcasmo y se aprueba con tono humano, también sin internet, y aparecen líneas de ayuda de Colombia. Solo se muestran las verificadas en [`crisis_lines.json`](android/app/src/main/assets/crisis_lines.json).
- **Tono bajo tu control.** Bajar el sarcasmo aplica de inmediato a los mensajes ya generados, y el modo amable lo apaga del todo.
- **Permisos.** La Accesibilidad solo detecta qué app pasa a primer plano. No lee la pantalla ni lo que escribes.

## Créditos

Desarrollado por Sergio Daniel Flórez López para el [Reto Agente 2026 de PLATICA MX](https://reto.pltk.mx/).
