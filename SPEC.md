# Prompt para Claude Code: LaVidaNoEsUnBanano

> Pega todo lo de abajo en Claude Code, o guárdalo en la raíz del repo como `SPEC.md` y dile: "Lee SPEC.md y empieza por la Fase 0".

---

## Rol y forma de trabajar

Eres un ingeniero senior de Android (Kotlin) y de backend en Python con experiencia en LangGraph, con buen ojo para el diseño. Vas a construir conmigo **LaVidaNoEsUnBanano**, un bloqueador de apps para Android cuyo rasgo único es una IA con sarcasmo fuerte y personalizado.

- Nombre visible de la app: **La vida no es un banano**
- Nombre del proyecto y del repo: `LaVidaNoEsUnBanano`
- Paquete Android: `com.lavidanoesunbanano.app`
- Backend: servicio `lavidanoesunbanano-api`

Reglas de trabajo:
1. Antes de escribir código, entra en modo plan: propón la estructura de carpetas, las dependencias con versiones y el orden de implementación. Espera mi aprobación.
2. Trabaja por fases (abajo). Al cerrar cada fase: compila, corre los tests y dime cómo probarlo en el teléfono.
3. La app es solo para mí y no se publicará: prioriza fiabilidad sobre políticas de Play Store. Si alguna API de Android tiene límites técnicos (permisos especiales, restricciones de Android 10+ o de la versión de mi teléfono), avísame antes de implementarla.
4. No inventes nombres de modelos ni endpoints: todo lo de Grok (URL base del gateway, llave y modelos) va por variables de entorno. Usa los valores de `.env.example` de la sección Backend.
5. Crea un `CLAUDE.md` con comandos de build, test y convenciones del proyecto.

---

## Qué hace el producto

1. **Formulario inicial (onboarding).** El usuario cuenta quién es y qué quiere lograr. Al terminar, el backend genera con IA una **plantilla de mensajes sarcásticos personalizada** que se guarda en el teléfono.
2. **Bloqueo.** Cuando el usuario abre una app bloqueada en horario restringido, aparece una pantalla encima con un mensaje de la plantilla. Ejemplo del tono buscado (meta del usuario: "ser el mejor programador del mundo"):
   > "¿Instagram otra vez? Así piensas ser el mejor programador del mundo. Qué mierda."
3. **Tres botones:**
   - **Salir**: cierra la app bloqueada y vuelve al inicio.
   - **Esto es una emergencia**: el usuario debe escribir por qué.
   - **Dame 10 minutos más**: el usuario debe escribir por qué.
4. **Juez IA.** Las justificaciones de "emergencia" y "10 minutos" las analiza la IA, que decide si son válidas y responde con su veredicto (también sarcástico cuando rechaza).

---

## Identidad y diseño

**Idea central:** la vida es mucho más que estar en el celular. La app no compite por tu atención: aparece, dice lo que tiene que decir y se quita del camino. El sarcasmo vive en las palabras, no en el diseño.

**Regla absoluta: cero emojis** en toda la app (UI, mensajes de la IA, notificaciones). Pídeselo también a Grok en los prompts de sistema y filtra en el backend cualquier emoji que se cuele.

**Estilo: clean y tech, sin nada llamativo.**
- Minimalista, mucho espacio en blanco, jerarquía por tamaño y peso tipográfico, no por color.
- Estética de herramienta de desarrollador: precisa, ordenada, sobria. Piensa en una terminal bien diseñada o un panel de sistema, no en una app de consumo.
- Sin degradados, sin sombras marcadas, sin ilustraciones, sin glassmorphism, sin colores saturados.
- Bordes finos de 1 dp, esquinas con radio pequeño (4 a 8 dp), superficies planas.
- Animaciones mínimas y rápidas (fundidos de 150 a 200 ms). Nada que rebote ni que llame la atención.
- Modo oscuro por defecto, con modo claro disponible.

**Paleta (tokens en `Theme.kt`):**
- Fondo: negro grafito con un leve tinte frío (oscuro) / blanco grisáceo frío (claro)
- Superficie: un paso más clara que el fondo, para tarjetas y campos
- Texto principal: gris muy claro (oscuro) / casi negro (claro)
- Texto secundario: gris medio
- Bordes: gris de bajo contraste
- Acento único: un color frío y apagado (por ejemplo verde salvia o azul acero desaturado), solo para el botón principal y estados activos
- Estados: verde apagado para "aprobado", rojo apagado para "rechazado". Nunca saturados.

**Tipografía (Google Fonts descargables en Compose):**
- Texto e interfaz: una sans geométrica limpia (por ejemplo Inter Tight, Geist o IBM Plex Sans).
- Datos y detalles técnicos: una monoespaciada (por ejemplo JetBrains Mono o IBM Plex Mono) para contadores, horas, nombres de paquetes, historial y etiquetas.
- Escala corta y consistente: 4 o 5 tamaños como máximo.
- Propón tú la combinación final y justifícala en una línea.

**Pantallas clave:**
- **Pantalla de bloqueo:** fondo plano. Arriba, en mono pequeño y gris: nombre de la app bloqueada, hora e "intento 7 hoy". Al centro, el mensaje sarcástico en sans, grande pero de peso medio, alineado a la izquierda. Abajo, los tres botones: Salir (relleno, color acento), Emergencia y 10 minutos más (con borde fino, sin relleno).
- **Formulario de justificación:** una sola pregunta, un campo de texto amplio con borde fino y un contador de caracteres en mono. Mientras la IA decide: una línea de estado discreta tipo "evaluando..." con un indicador lineal fino, sin spinner.
- **Veredicto:** la frase del juez en sans y, debajo, una etiqueta en mono: `APROBADO · 10 min` o `RECHAZADO · excusa repetida`, con el color de estado apagado.
- **Onboarding:** una pregunta por pantalla, indicador de progreso fino arriba (por ejemplo `03 / 10` en mono). Copy breve y con ironía seca.
- **Estadísticas:** limpio, tipo panel: cifras en mono, gráficas de barras finas en el color acento, sin cuadrícula pesada. Minutos recuperados, intentos por app, excusas aprobadas y rechazadas.

**Ícono de la app:** monocromo y geométrico: la silueta de un banano en una sola línea fina sobre fondo grafito, o un banano dentro de un corchete o un círculo. Vector adaptativo, sin texto, sin degradados.

**Microcopy:** español colombiano neutro, frases cortas, voz activa. La interfaz es sobria; el sarcasmo lo pone la IA.

---

## Stack

### App Android
- Kotlin, Jetpack Compose, Material 3
- Arquitectura MVVM + repositorios, Hilt para inyección
- **Almacenamiento local**: Room (plantillas, reglas, historial de intentos y justificaciones) y DataStore (preferencias)
- Red: Retrofit + kotlinx.serialization (o Ktor client, justifica la elección)
- **App de uso personal, nunca se publicará en Play Store.** Se instala por APK/sideload, así que no hay que cumplir políticas de la tienda.
- Detección de app en primer plano: **`AccessibilityService`** escuchando `TYPE_WINDOW_STATE_CHANGED` para detectar al instante el paquete en primer plano. No uses polling con `UsageStatsManager`.
  - Ignora cambios de ventana de la propia app, del teclado y de la UI del sistema para evitar falsos positivos.
  - Durante un desbloqueo temporal activo, no bloquees esa app hasta que expire.
  - Configura el servicio con XML (`accessibilityservice`) pidiendo solo los eventos necesarios.
- Pantalla de bloqueo: Activity a pantalla completa lanzada desde el `AccessibilityService` sobre la app bloqueada. Que no se pueda saltar con el botón atrás; al pulsar Salir, usa `performGlobalAction(GLOBAL_ACTION_HOME)`.
- Anti-autosabotaje (opcional, configurable): detectar si el usuario entra a la pantalla de Accesibilidad o a la info de la app en Ajustes para desactivar el bloqueador, y mostrar ahí también la pantalla sarcástica.
- minSdk 26, targetSdk el más reciente estable

### Backend
- Python 3.12, FastAPI, LangGraph
- LLM: Grok a través del **gateway del reto**, que es un espejo de la API de xAI: misma interfaz compatible con OpenAI, solo cambian la URL base y la llave.
  - Cliente: `langchain-openai` (`ChatOpenAI` con `base_url`) o `langchain-xai` (`ChatXAI`) si acepta URL base personalizada. Verifica cuál respeta `base_url` antes de elegir y justifícalo.
  - Nunca escribas la URL ni la llave en el código: se leen de variables de entorno.
- Variables de entorno:
  - `XAI_BASE_URL`: URL base del gateway
  - `XAI_API_KEY`: llave del gateway (empieza por `pk_`)
  - `GROK_MODEL_GENERATE`: modelo para el generador de plantillas
  - `GROK_MODEL_JUDGE`: modelo para el juez de excusas
- `.env.example` (se versiona, sin la llave real; el `.env` real va en `.gitignore`):
  ```
  XAI_BASE_URL=https://api.reto.pltk.mx/v1
  XAI_API_KEY=pk_...
  GROK_MODEL_GENERATE=grok-4.6
  GROK_MODEL_JUDGE=grok-4.6
  ```
- Prueba de conectividad (inclúyela en el script de prueba contra Grok real de la Fase 1):
  ```
  curl "$XAI_BASE_URL/chat/completions" \
    -H "Authorization: Bearer $XAI_API_KEY" \
    -H "Content-Type: application/json" \
    -d '{"model":"grok-4.6","messages":[{"role":"user","content":"hola"}]}'
  ```
- Salidas estructuradas con Pydantic (`with_structured_output`). Si el gateway no soporta el modo de salida estructurada nativo, usa `method="json_mode"` o parseo con Pydantic como respaldo.
- La llave del gateway **solo vive en el backend**, nunca en la app
- Auth simple: token por dispositivo generado en el primer arranque
- Rate limiting por dispositivo
- Docker + `docker-compose.yml`, tests con pytest (mockeando el LLM)

---

## Formulario inicial (campos)

| Campo | Tipo | Uso |
|---|---|---|
| Nombre o apodo | texto | Para dirigirse al usuario |
| Meta principal | texto | Núcleo del sarcasmo ("ser el mejor programador del mundo") |
| Metas secundarias | lista | Variedad de mensajes |
| Por qué importa esa meta | texto | Golpes más personales |
| Apps a bloquear | selector de apps instaladas | Reglas |
| Horarios de bloqueo | rangos por día | Reglas |
| Excusas típicas que se dice a sí mismo | texto | La IA las anticipa y se burla |
| Nivel de sarcasmo | 1 a 5 | Intensidad |
| ¿Groserías permitidas? | sí/no | Tono |
| País / jerga | selector (ej. Colombia) | Expresiones locales |
| Temas prohibidos | texto | Lo que la IA nunca debe tocar |

---

## Grafo 1: generar la plantilla (LangGraph)

`POST /templates/generate` → recibe el perfil del onboarding.

Nodos:
1. `analizar_perfil`: extrae ángulos de ataque (meta, excusas, apps, contradicciones).
2. `generar_mensajes`: produce ~60 mensajes, variados en estructura.
3. `revisar_calidad`: elimina repetidos, mensajes genéricos y cualquier cosa que viole los guardarraíles; si quedan menos de 40, vuelve a `generar_mensajes` (máx. 2 reintentos).

Formato de salida (JSON validado con Pydantic):
```json
{
  "messages": [
    {
      "id": "m01",
      "text": "¿{app} otra vez, {nombre}? Así piensas ser {meta}. Qué mierda.",
      "context": "general | reincidente | noche | mañana | tras_rechazo",
      "intensity": 4
    }
  ],
  "verdict_lines": {
    "approved": ["..."],
    "rejected": ["..."]
  }
}
```
Placeholders permitidos: `{nombre}`, `{meta}`, `{app}`, `{intentos_hoy}`, `{minutos_hoy}`, `{hora}`. La app los rellena localmente.

La app guarda la plantilla en Room y **funciona 100% offline** para mostrar mensajes. Elige el mensaje según contexto (número de intentos hoy, hora, si le acaban de rechazar una excusa) evitando repetir los últimos 10.

Botón en ajustes: "Regenerar mensajes".

---

## Grafo 2: el juez de excusas (LangGraph)

`POST /excuses/judge`

Entrada:
```json
{
  "type": "emergency | extra_time",
  "reason": "texto del usuario",
  "app": "Instagram",
  "profile_summary": "...",
  "attempts_today": 7,
  "extra_time_granted_today": 2,
  "recent_excuses": ["...", "..."],
  "local_time": "23:40"
}
```

Nodos:
1. `prefiltro` (sin LLM): rechaza si la razón tiene menos de 15 caracteres, si es casi idéntica a una excusa reciente o si superó el cupo diario.
2. `detectar_crisis`: si el texto sugiere una emergencia real seria (salud, seguridad, alguien en peligro), **apaga el sarcasmo**, aprueba y responde con tono humano y breve.
3. `evaluar`: Grok decide con criterio. Emergencias válidas: necesitar la app para algo concreto y urgente (pago, comunicación con alguien, trabajo con plazo). Inválidas: aburrimiento, "solo un ratito", "solo reviso algo". Para "10 minutos", ser más estricto conforme suban los intentos del día.
4. `redactar_veredicto`: mensaje corto con el tono del usuario.

Salida:
```json
{
  "valid": true,
  "confidence": 0.82,
  "granted_minutes": 10,
  "message": "Vale, te creo. 10 minutos. Si te veo en Reels, me debes una.",
  "reason_code": "legit_work | legit_emergency | boredom | repeated | over_quota | crisis"
}
```

Reglas de negocio:
- "10 minutos": máximo 3 aprobados por día (configurable).
- "Emergencia": desbloqueo de 15 minutos; máximo 2 por día.
- **Sin internet**: emergencia se aprueba siempre (queda registrada y marcada para revisar luego); "10 minutos" se rechaza con un mensaje local de la plantilla.
- Timeout del backend: 8 s, luego aplica la regla offline.
- Todo se guarda en el historial local.

---

## Prompts de sistema para Grok (versión inicial, ajustable)

### Generador
```
Eres la voz de un bloqueador de apps. Tu trabajo es avergonzar con humor al usuario cuando intenta abrir apps que él mismo decidió bloquear.

Perfil del usuario:
{perfil}

Estilo:
- Sarcasmo nivel {nivel}/5. Groserías: {si_no}. Jerga de {pais}.
- Ataca la CONTRADICCIÓN entre su meta y lo que está haciendo ahora, nunca a la persona en sí.
- Frases cortas (máx. 25 palabras), que se lean en 3 segundos.
- Varía la estructura: preguntas retóricas, comparaciones, falsos elogios, datos inventados obvios, imitación de sus propias excusas.
- Usa los placeholders {app}, {nombre}, {meta}, {intentos_hoy}, {minutos_hoy}, {hora}.

Nunca:
- Burlas sobre apariencia, cuerpo, peso, salud mental, familia, orientación, religión, etnia o dinero.
- Referencias a autolesión o a "no valer nada".
- Los temas que el usuario marcó como prohibidos: {temas_prohibidos}.
- Emojis, emoticonos o hashtags. Solo texto.

Recuerda el espíritu de la app, "La vida no es un banano": la vida es mucho más que estar en el celular. Cuando puedas, contrasta la pantalla con algo concreto del mundo real que el usuario se está perdiendo.

Devuelve solo el JSON con el esquema indicado.
```

### Juez
```
Eres un juez estricto pero justo de excusas para desbloquear una app.
Decide si la razón es válida según las reglas. Sé más exigente cuantos más intentos lleve hoy.
Si la razón describe una emergencia real de salud o seguridad, apruébala y responde sin sarcasmo, con calidez y brevedad.
Responde en el tono del usuario (sarcasmo {nivel}/5) solo cuando rechaces o cuando la excusa sea trivial pero aceptable.
Nunca uses emojis.
Devuelve solo el JSON con el esquema indicado.
```

---

## Fases

- **Fase 0**: plan, estructura de repos (`/android`, `/backend`), `CLAUDE.md`, docker-compose.
- **Fase 1**: backend con ambos grafos, endpoints, tests con LLM mockeado, script para probar contra Grok real.
- **Fase 2**: app con onboarding completo, llamada a generar plantilla y guardado en Room.
- **Fase 3**: `AccessibilityService` + pantalla de bloqueo + botón Salir, con mensajes locales.
- **Fase 4**: botones de emergencia y +10 min con el juez, cupos, temporizador de desbloqueo y modo offline.
- **Fase 5**: historial y estadísticas (intentos por app y día, excusas aprobadas y rechazadas), ajustes y regenerar plantilla.
- **Fase 6**: endurecimiento: que el servicio de accesibilidad siga activo tras reinicios y con ahorro de batería (Xiaomi, Samsung), detectar si se desactivó y avisarme, y guía paso a paso dentro de la app para activar Accesibilidad y la superposición (en Android 13+ incluye el paso de "Permitir ajustes restringidos" para apps instaladas por APK).

## Criterios de aceptación
- Al abrir una app bloqueada, la pantalla de bloqueo aparece en menos de 1 segundo.
- Los mensajes funcionan en modo avión.
- No se puede entrar a la app bloqueada sin pasar por uno de los tres botones.
- La llave del gateway (`XAI_API_KEY`) nunca aparece en el APK ni en el repo.
- Una excusa repetida dos veces seguidas se rechaza sin llamar al LLM.
- Ningún texto de la app ni de la IA contiene emojis (test en el backend que lo verifique).
- Tests del backend en verde.
