# La vida no es un banano

**LaVidaNoEsUnBanano: agente para reducir la adicción al celular**
Proyecto de Sergio Daniel Flórez López para el Reto de AI.

Bloqueador de apps para Android con un agente de IA de sarcasmo personalizado. Cuando abres una app bloqueada en horario restringido, aparece una pantalla con un mensaje hecho a tu medida y tres salidas: salir, emergencia o 10 minutos más. Las dos últimas las decide un juez de IA.

La idea central: la vida es mucho más que estar en el celular. La app no compite por tu atención; aparece, dice lo que tiene que decir y se quita del camino.

**[Ver el video de demostración](https://www.youtube.com/watch?v=uU0Go189CdI)**

## El problema

Abrir TikTok o Instagram "solo un ratito" es un gesto automático. Los bloqueadores tradicionales se saltan con un toque porque no tienen nada que decir. Este agente conoce tu meta y tus excusas, y te las devuelve justo en el momento en que estás por romper tu propio compromiso.

## El agente

El backend (`lavidanoesunbanano-api`) tiene dos grafos de LangGraph que usan Grok (`grok-4.6`) a través del gateway del reto.

### 1. Generador de mensajes (`POST /templates/generate`)

Recibe el perfil del onboarding (meta, metas secundarias, excusas típicas, apps, nivel de sarcasmo, jerga y temas prohibidos) y lo procesa en tres nodos:

1. `analizar_perfil`: encuentra los ángulos de ataque, sobre todo las contradicciones entre tu meta y tus hábitos.
2. `generar_mensajes`: escribe unos 60 mensajes variados, con placeholders como `{app}`, `{meta}` o `{hora}`.
3. `revisar_calidad`: elimina repetidos, genéricos y cualquier cosa que viole los guardarraíles. Si quedan menos de 40, vuelve a generar (máximo 2 reintentos).

La plantilla se guarda en el teléfono, así que los bloqueos funcionan sin internet.

### 2. Juez de excusas (`POST /excuses/judge`)

Cuando pides "emergencia" o "10 minutos más", tienes que escribir por qué. El juez tiene cuatro nodos:

1. `prefiltro` (sin LLM): rechaza excusas de menos de 15 caracteres, repetidas o por encima del cupo diario.
2. `detectar_crisis`: si el texto describe una emergencia real de salud o seguridad, apaga el sarcasmo y aprueba con tono humano.
3. `evaluar`: Grok decide con criterio y se vuelve más estricto con cada intento del día.
4. `redactar_veredicto`: responde en el tono que elegiste, por ejemplo `RECHAZADO · aburrimiento`.

Reglas: máximo 3 aprobaciones de "10 minutos" y 2 emergencias (15 minutos) por día. Sin internet o con timeout de 8 segundos, la emergencia se aprueba y queda marcada para revisión, y el "10 minutos" se rechaza.

### Guardarraíles

- Ataca la contradicción, nunca a la persona: nada sobre apariencia, salud mental, familia, religión, dinero ni los temas que el usuario prohíba.
- Cero emojis en toda la app y en las respuestas de la IA, con un filtro en el backend y un test que lo verifica.
- Salidas estructuradas validadas con Pydantic.

## Estructura

- `android/`: app en Kotlin con Jetpack Compose, Room, Hilt y `AccessibilityService`.
- `backend/`: FastAPI y LangGraph, desplegado en Vercel.
- [SPEC.md](SPEC.md): producto, diseño y fases.
- [CLAUDE.md](CLAUDE.md): comandos de build y test, y convenciones.

## Conexión con el gateway del reto
La llave vive solo en el backend: nunca está en el APK ni en el repositorio.

## Instalar en el teléfono

No está en Play Store; se instala por APK.

**Requisitos:** Android 8.0 o superior. Internet solo para el onboarding y el juez; los mensajes de bloqueo funcionan sin conexión.

**[Descargar el APK](apk/la-vida-no-es-un-banano.apk)** (versión 0.2.0, estilo Warm Toon, 11 MB). Usa el backend de producción en Vercel. La [versión 0.1.0](https://www.mediafire.com/file/dyz4x8pszfndc5m/la-vida-no-es-un-banano.apk/file), con el estilo anterior, sigue en MediaFire.

1. Ábrelo en el teléfono. Android pide permitir instalar apps desde el navegador o el gestor de archivos.
2. En Samsung, si el Bloqueador automático (Auto Blocker) está activo, apágalo para instalar y vuelve a encenderlo después.
3. Abre la app y responde el onboarding. Al final pide el código de registro del servidor: CÓMO_CONSEGUIRLO.
4. En Inicio, toca **Configurar** y sigue la guía. Como se instaló desde un archivo, en Android 13 o más reciente el primer paso es Permitir ajustes restringidos.

Por USB: `adb install -r apk/la-vida-no-es-un-banano.apk`.

El APK está firmado con la llave de debug de la máquina que lo compiló. Para instalar uno compilado en otra máquina hay que desinstalar antes la versión anterior, y eso borra el perfil y el historial.

## Privacidad

- El permiso de Accesibilidad solo detecta qué app pasa a primer plano. No lee la pantalla ni lo que escribes.
- Perfil, plantilla e historial viven en el teléfono. Al servidor solo van el perfil (para generar la plantilla) y cada excusa que pides juzgar.

## Créditos

Desarrollado por Sergio Daniel Flórez López

El gateway es un espejo de la API de xAI: solo cambian la URL base y la llave. El backend la lee de variables de entorno (`backend/.env`, que no se versiona):
