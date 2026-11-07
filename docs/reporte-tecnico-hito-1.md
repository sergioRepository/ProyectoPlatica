# Reporte técnico: La vida no es un banano

**LaVidaNoEsUnBanano: agente para reducir la adicción al celular** · Sergio Daniel Flórez López

15 de octubre de 2026 · Hito 1 del [Reto Agente 2026](https://reto.pltk.mx/)

## Resumen

La vida no es un banano es un bloqueador de apps para Android con un agente de IA que conoce la meta del usuario y se la recuerda, con sarcasmo personalizado, cada vez que abre una app bloqueada. En la prueba del 15 de octubre de 2026, el agente hizo 15 llamadas a Grok por el gateway del reto, todas con respuesta 200, por un costo total de 0,0501 USD.

- **Generar la plantilla** de mensajes de un usuario cuesta en promedio 0,0146 USD (4 llamadas) y produjo 52 mensajes.
- **Juzgar una excusa** cuesta 0,0012 USD (1 llamada).
- **Las excusas triviales** (demasiado cortas o repetidas) se rechazan en un prefiltro del backend, sin llamar al modelo: costo cero.
- **Los mensajes de bloqueo** se muestran sin internet, porque la plantilla queda guardada en el teléfono.

## Problema y propuesta

Abrir TikTok o Instagram "solo un ratito" es un gesto automático, y los bloqueadores tradicionales se saltan con un toque porque no tienen nada que decir. Este agente responde en el momento exacto de la recaída con lo único que el usuario no puede ignorar: su propia meta y sus propias excusas.

- **Personal:** cada mensaje se genera a partir del perfil del usuario (meta, metas secundarias, por qué le importa, excusas típicas, jerga y nivel de sarcasmo).
- **Con fricción inteligente:** desbloquear exige escribir una razón, y un juez de IA decide si es válida.
- **Discreto:** la app no compite por la atención; aparece, dice lo que tiene que decir y se quita del camino.

La idea central del producto: la vida es mucho más que estar en el celular.

## El agente

El agente son dos grafos de LangGraph en el backend: uno escribe la plantilla de mensajes una sola vez y otro juzga cada excusa. Ambos usan salidas estructuradas validadas con Pydantic.

### Grafo 1: generador de plantillas (`POST /templates/generate`)

1. `analizar_perfil`: extrae los ángulos de ataque, sobre todo la contradicción entre la meta y los hábitos.
2. `generar_mensajes`: escribe unos 60 mensajes de estructura variada, con placeholders (`{nombre}`, `{meta}`, `{app}`, `{intentos_hoy}`, `{minutos_hoy}`, `{hora}`) que la app rellena localmente.
3. `revisar_calidad`: elimina repetidos, genéricos y cualquier violación de los guardarraíles. Si quedan menos de 40, vuelve al paso 2 (máximo 2 reintentos).

Cada mensaje lleva un contexto (`general`, `reincidente`, `noche`, `mañana`, `tras_rechazo`) y una intensidad. La app elige el mensaje según los intentos del día, la hora y si acaban de rechazarle una excusa, sin repetir los últimos 10.

### Grafo 2: juez de excusas (`POST /excuses/judge`)

1. `prefiltro` (sin LLM): rechaza razones de menos de 15 caracteres, casi idénticas a una excusa reciente o por encima del cupo diario.
2. `detectar_crisis`: si el texto sugiere una emergencia real de salud o seguridad, apaga el sarcasmo y aprueba con tono humano.
3. `evaluar`: Grok decide con criterio y se vuelve más estricto a medida que suben los intentos del día.
4. `redactar_veredicto`: responde en el tono elegido por el usuario y devuelve un código de motivo (`legit_work`, `legit_emergency`, `boredom`, `repeated`, `over_quota`, `crisis`).

### Reglas de negocio

| Tipo de desbloqueo | Duración | Cupo diario | Sin internet o timeout de 8 s |
|---|---|---|---|
| Dame 10 minutos más | 10 min | 3 aprobados | Se rechaza con un mensaje local |
| Esto es una emergencia | 15 min | 2 aprobados | Se aprueba y queda marcada para revisión |

## Guardarraíles, privacidad y seguridad

El sarcasmo ataca la contradicción entre la meta y la conducta, nunca a la persona, y se apaga por completo ante una emergencia real.

- **Temas vetados en el prompt del generador:** apariencia, cuerpo, peso, salud mental, familia, orientación, religión, etnia, dinero, autolesión y los temas que el propio usuario marque como prohibidos.
- **Cero emojis:** se pide en los prompts de sistema, se filtra en el backend y un test lo verifica.
- **Accesibilidad mínima:** el `AccessibilityService` escucha solo `TYPE_WINDOW_STATE_CHANGED` para saber qué paquete pasa a primer plano. No lee la pantalla ni lo que el usuario escribe.
- **Datos locales:** perfil, plantilla, reglas e historial viven en el teléfono (Room y DataStore). Al servidor solo van el perfil, al generar la plantilla, y cada excusa que el usuario pide juzgar.
- **La llave del gateway** (`XAI_API_KEY`) vive solo en variables de entorno del backend: nunca en el APK ni en el repositorio.
- **Acceso al backend:** registro con código, token por dispositivo y límite de peticiones por dispositivo.

## Evidencia de funcionamiento

El registro del gateway del 15 de octubre de 2026 coincide minuto a minuto con lo que muestra la app en el [video de demostración](https://www.youtube.com/watch?v=uU0Go189CdI). Las 15 llamadas fueron a `/v1/chat/completions` con el modelo `grok-4.20-0309-non-reasoning` y todas respondieron 200.

| Hora | Llamadas | Costo (USD) | Qué fue | Evidencia en la app |
|---|---|---|---|---|
| 21:29 | 1 | 0,0012 | Juez: "Videos de gatos" como emergencia | Historial 21:29, `RECHAZADO · aburrimiento` |
| 21:28 | 0 | 0 | Prefiltro: excusa vacía | Historial 21:28, `RECHAZADO · muy corta`, sin llamada en el gateway |
| 21:27 | 4 | 0,0143 | Generación de la plantilla de la demo | Ajustes: 52 mensajes, generada 15 oct 21:27 |
| 21:19 a 21:20 | 4 + 1 | 0,0149 + 0,0012 | Ensayo: generación y un juicio | Prueba previa a la grabación |
| 20:35 a 20:36 | 4 | 0,0145 | Ensayo: generación | Prueba previa a la grabación |
| 20:30 | 1 | 0,0040 | Llamada aislada | Sin identificar en este registro |

Cada generación aparece como una ráfaga de 4 llamadas en uno o dos minutos, y cada juicio como una llamada sola de 0,0012 USD. El rechazo de las 21:28 no dejó rastro en el gateway: el prefiltro funcionó sin gastar tokens.

**Pregunta abierta:** el `.env.example` del SPEC indica `grok-4.6`, pero el gateway registró `grok-4.20-0309-non-reasoning`. Falta confirmar si es un alias que resuelve el gateway o si la variable de entorno de producción tiene otro valor.

## Costos y eficiencia

El diseño concentra el gasto en una sola generación por usuario: después, cada bloqueo cuesta cero y cada excusa juzgada cuesta 0,0012 USD. Con uso normal, un usuario cuesta alrededor de 0,12 USD al mes.

| Operación | Llamadas | Costo medido (USD) | Frecuencia |
|---|---|---|---|
| Generar plantilla | 4 | 0,0143 a 0,0149 (promedio 0,0146) | Una vez, y al pulsar "Regenerar mensajes" |
| Juzgar una excusa | 1 | 0,0012 | Solo si el usuario pide emergencia o 10 minutos |
| Excusa trivial o repetida | 0 | 0 | La rechaza el prefiltro |
| Mostrar un bloqueo | 0 | 0 | Cada intento; todo local |

Proyección con los costos medidos (supuestos propios, no medidos):

| Escenario por usuario y mes | Generaciones | Excusas juzgadas | Costo (USD) |
|---|---|---|---|
| Uso normal | 1 | 3 al día (90) | 0,12 |
| Uso intenso | 4 (una por semana) | 10 al día (300) | 0,42 |

Para 1.000 usuarios en uso normal, el costo sería de unos 123 USD al mes. Tres decisiones lo mantienen bajo: generar los mensajes una sola vez y guardarlos en el teléfono, el prefiltro sin LLM y un modelo sin razonamiento extendido para tareas cortas.

## Pruebas y criterios de aceptación

El video y el registro del gateway demuestran el flujo completo; los criterios que dependen de pruebas automatizadas deben respaldarse con la salida de `pytest` antes de la entrega.

| Criterio | Evidencia | Estado |
|---|---|---|
| La pantalla de bloqueo aparece en menos de 1 s | Video, minuto 1:56 | Demostrado |
| No se entra a la app bloqueada sin pasar por un botón | Video, intentos 1 a 4 | Demostrado |
| Una excusa trivial se rechaza sin llamar al LLM | Rechazo de las 21:28 sin llamada en el gateway | Demostrado |
| El juez rechaza una excusa inválida con su motivo | Llamada de las 21:29 y `RECHAZADO · aburrimiento` | Demostrado |
| Los mensajes funcionan en modo avión | Plantilla guardada en Room | Falta grabarlo |
| Una excusa repetida dos veces se rechaza sin LLM | Nodo `prefiltro` | Falta test o captura |
| Ningún texto contiene emojis | Test del backend | Falta adjuntar la salida |
| La llave nunca está en el APK ni en el repo | Variables de entorno en Vercel | Falta revisar el historial de git |
| Tests del backend en verde | `pytest` con el LLM simulado | Falta adjuntar la salida |

## Limitaciones y trabajo futuro

La versión 0.1.0 funciona de punta a punta en un Samsung con Android 13 o superior; sus límites vienen sobre todo de la distribución por APK y de las restricciones de Android.

- **Instalación:** sin Play Store, el usuario debe permitir ajustes restringidos, y en Samsung apagar el Bloqueador automático mientras instala.
- **Firma de debug:** actualizar desde un APK compilado en otra máquina exige desinstalar y borra el perfil y el historial.
- **Autosabotaje:** quien conoce Android puede desactivar Accesibilidad. El SPEC contempla detectar esa pantalla y avisar si el servicio se apaga.
- **Ahorro de batería:** algunos fabricantes detienen servicios en segundo plano; la guía pide quitar las restricciones de batería.
- **Próximos pasos:** firma de release, medir los minutos recuperados en lugar de estimarlos (hoy: 10 minutos por intento que no terminó en desbloqueo), y regenerar la plantilla automáticamente según las excusas más usadas.
