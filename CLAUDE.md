# CLAUDE.md

Bloqueador de apps para Android con una IA sarcástica. El producto, las reglas de trabajo y las fases están en [SPEC.md](SPEC.md), que es la fuente de verdad.

## Contexto
- Uso personal: la app se instala por adb o APK y nunca va a Play Store.
- Teléfono de pruebas: Samsung Galaxy A14. Su último sistema es Android 15 (One UI 7); no recibe Android 16.
- Backend en producción: Vercel. Corre como una sola función de Python 3.12 y sin disco persistente, así que nada se guarda en archivos ni en SQLite. Docker y `docker-compose.yml` son solo para desarrollo local.
- Se trabaja por fases: plan, aprobación del usuario, implementación. Al cerrar cada fase: build, tests y cómo probarlo en el teléfono.

## Estructura
- `android/`: Gradle 9.5.1, AGP 9.1.1 y Kotlin 2.4.20 (el Kotlin integrado de AGP 9, sin plugin `kotlin-android`). Un solo módulo, `:app`, paquete `com.lavidanoesunbanano.app`. Dentro de `app/src/main/kotlin/com/lavidanoesunbanano/app/`:
  - `core/designsystem`: colores y formas (`Theme.kt`), tipografía (`Type.kt`) y componentes (`Components.kt`).
  - `domain`: Kotlin puro y probado en JVM: horarios (`BlockRange`), formato de horarios, placeholders, orden de apps, selección del mensaje por contexto (`MessagePicker`), la decisión de bloqueo (`blocking`: `BlockPolicy` y `ForegroundTracker`) y la regla sin conexión del juez (`excuse/OfflineVerdict`).
  - `data`: Room (`local`, esquemas en `app/schemas`), DataStore (`prefs`: id y token del dispositivo), Retrofit (`remote`), conversiones (`mapper`), repositorios y apps instaladas. `BlockSessionRepository` guarda cada intento en `attempts` y elige su mensaje. `ExcuseRepository` pide el veredicto con un límite de 8 s, aplica la regla sin conexión si no llega y guarda cada excusa en `excuses`; de ahí salen los desbloqueos temporales (`unlocked_until`) y los cupos del día. Los cupos y el límite viven en `JudgeConfig`.
  - `service/blocker`: `BlockerService` (accesibilidad, solo `typeWindowStateChanged`, sin leer la pantalla) y `BlockerBridge` (estado del servicio y Salir con `GLOBAL_ACTION_HOME`).
  - `feature`: `onboarding` (11 pasos), `generation` (registro con código y generación de la plantilla), `home` (también pide el código otra vez si el backend rechazó el token) y `block` (pantalla de bloqueo, una `Activity` en su propia tarea: mensaje, pregunta de la excusa y veredicto). Las rutas están en `navigation`.
- `backend/`: servicio `lavidanoesunbanano-api`, paquete Python `banano`, dependencias con uv. Vercel lo carga desde `[tool.vercel] entrypoint` en `pyproject.toml`.
- `docker-compose.yml`: backend local en `http://localhost:8000`.

## Comandos

### Android (PowerShell, desde la raíz)
No hay `java` en el PATH; se usa el JBR de Android Studio.
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
android\gradlew.bat -p android :app:assembleDebug       # APK debug
android\gradlew.bat -p android :app:testDebugUnitTest   # tests JVM
android\gradlew.bat -p android :app:installDebug        # instala en el teléfono conectado
adb reverse tcp:8000 tcp:8000                           # el teléfono ve el backend local en 127.0.0.1:8000
adb logcat --pid=$(adb shell pidof com.lavidanoesunbanano.app)
adb logcat -s LaVidaBlock                               # debug: ms del evento al primer cuadro de la pantalla de bloqueo
```
Activar el servicio de bloqueo sin pasar por Ajustes (reemplaza la lista de servicios activos; sirve en el emulador):
```powershell
adb shell settings put secure enabled_accessibility_services com.lavidanoesunbanano.app/com.lavidanoesunbanano.app.service.blocker.BlockerService
adb shell settings put secure accessibility_enabled 1
```
La URL del backend se cambia con `-Plavida.apiBaseUrl.debug=...` o `-Plavida.apiBaseUrl.release=...`, o en `android/gradle.properties`. No es un secreto. En debug solo se permite HTTP hacia `127.0.0.1`, `localhost` y `10.0.2.2` (`src/debug/res/xml/network_security_config.xml`).

Emulador para revisar la interfaz sin teléfono. El AVD `LaVida_API37` se creó a mano en `~/.android/avd` con la pantalla del A14 (1080x2408, 400 dpi):
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd LaVida_API37 -no-window -no-snapshot -gpu swiftshader_indirect
adb -s emulator-5554 shell screencap -p /sdcard/s.png; adb -s emulator-5554 pull /sdcard/s.png
adb -s emulator-5554 emu kill
```

### Backend (desde la raíz)
```powershell
uv --directory backend sync                                     # instala dependencias (Python 3.12)
uv --directory backend run pytest                               # tests, siempre con LLM falso
uv --directory backend run ruff check .                         # lint
uv --directory backend run ruff format .                        # formato
uv --directory backend run uvicorn banano.main:app --reload     # servidor local en :8000
docker compose up --build                                       # el mismo backend en Docker
```

Prueba contra Grok real con `backend/.env`. Gasta tokens del gateway y nunca imprime la llave:
```powershell
$env:PYTHONIOENCODING = "utf-8"
uv --directory backend run python -m scripts.smoke_grok                    # todo
uv --directory backend run python -m scripts.smoke_grok --solo juez        # modelos | conectividad | estructurada | plantilla | juez
uv --directory backend run python -m scripts.smoke_grok --api http://localhost:8000   # API desplegada, de punta a punta
```
Para probar otro modelo sin tocar el `.env`: `$env:GROK_MODEL_JUDGE = "..."` antes de correrlo (el entorno pesa más que el `.env`).

## API del backend
- `GET /health`.
- `POST /devices/register` con `{device_id, enrollment_code}` devuelve `{device_token}`. El token lleva el id del dispositivo firmado con HMAC (`DEVICE_TOKEN_SECRET`); no hay base de datos. Cambiar el secreto obliga a registrar de nuevo.
- `POST /templates/generate` (token Bearer): perfil del onboarding, devuelve `messages`, `verdict_lines` y `profile_summary`. La app guarda `profile_summary` y lo manda al juez.
- `POST /excuses/judge` (token Bearer): devuelve el veredicto del spec. Un 503 significa que el LLM no respondió: la app aplica la regla sin conexión.
- Extensiones al spec, todas con valor por defecto: en el juez, `emergencies_granted_today`, `extra_time_daily_limit`, `emergency_daily_limit`, `sarcasm_level`, `profanity_allowed` y `country`. El `reason_code` suma `too_short`.
- Juez: `detectar_crisis` (palabras clave, sin LLM) aprueba directo con tono humano y va antes del `prefiltro` (cupo, menos de 15 caracteres, excusa repetida, sin LLM). `evaluar` hace una sola llamada al LLM, con 6 s de tiempo, y `redactar_veredicto` limpia y aplica las reglas sin LLM.
- Plantilla: los mensajes se piden en lotes paralelos de 20. La revisión cambia nombres de apps y del usuario por `{app}` y `{nombre}`, y descarta lo repetido, genérico o fuera de los guardarraíles.

## Despliegue en Vercel
1. Crear el proyecto desde el repo con Root Directory `backend`. Vercel detecta FastAPI por `[tool.vercel]` en `pyproject.toml`; `.vercelignore` evita subir el `.env`.
2. En Environment Variables: `XAI_BASE_URL`, `XAI_API_KEY`, `GROK_MODEL_GENERATE`, `GROK_MODEL_JUDGE`, `ENROLLMENT_CODE` y `DEVICE_TOKEN_SECRET`.
3. La app debe usar la URL de producción. Las de preview piden login de Vercel (Deployment Protection), así que la rama de producción debe ser la que tenga el código listo.
4. Verificar con `smoke_grok --api https://<proyecto>.vercel.app`.

## Convenciones
- **Cero emojis** en todo: UI, strings, mensajes de la IA, notificaciones y documentación. El backend filtra cualquier emoji que devuelva el LLM.
- **Secretos:** `XAI_API_KEY` vive solo en `backend/.env` (ignorado por git) o en las variables de Vercel. Nunca en Android, en git ni en logs. La URL del gateway y los nombres de modelo también llegan solo por entorno; no se escriben en el código.
- **Idioma:** los textos de la app van en español colombiano neutro, con frases cortas y voz activa, y siempre en `res/values/strings.xml`. Los identificadores de código van en inglés. Los nodos de LangGraph, los placeholders (`{nombre}`, `{meta}`...) y los campos JSON de la API usan los nombres del spec.
- **Android:** MVVM con repositorios y Hilt. La lógica de dominio va en Kotlin puro bajo `domain/`, con tests JVM. Las versiones solo se declaran en `android/gradle/libs.versions.toml`, y Gradle y AGP no se suben sin una razón concreta. Room tiene migración destructiva de respaldo (ver `di/DatabaseModule.kt`), pero si un cambio solo agrega tablas o columnas va con `AutoMigration`, para no borrar el perfil y la plantilla del teléfono.
- **Diseño:** los tokens están en `core/designsystem/Theme.kt`. Sin degradados ni sombras, bordes de 1 dp, radios de 4 a 8 dp y fundidos de 150 a 200 ms (`FadeIn` y `FadeOut`). Cinco tamaños de letra: 30, 22, 16, 14 y 12. Geist y Geist Mono van empaquetadas en `res/font` como fuentes variables, con la licencia OFL en `assets/licenses`; no se descargan. El ícono es un vector adaptativo (`drawable-v24/ic_launcher_foreground.xml`) con capa monocroma para los íconos temáticos.
- **Backend:** el LLM se inyecta y los tests nunca llaman a Grok real; eso lo hace el script de prueba de la Fase 1. El cliente es `ChatXAI` con `base_url` explícita, porque por su cuenta lee `XAI_API_BASE` y no `XAI_BASE_URL`. El límite de peticiones por dispositivo vive en memoria: en Vercel es aproximado porque cada instancia cuenta por separado, y el usuario lo aceptó así (sin Redis).
- **Git:** un commit al cerrar cada fase, en la rama `desarrollo`. Fuera de eso, solo cuando el usuario lo pida.

## Problemas conocidos
- **`grok-4.6` es lento:** razona antes de responder. Como juez tarda de 4 a 17 s y no alcanza los 8 s del teléfono; generando la plantilla, cada lote de 20 pasó de 120 s. `grok-4.20-0309-non-reasoning`, que también está en el gateway, juzga en 1 a 3 s y genera la plantilla en unos 18 s. Con `grok-4.6` como juez, el backend corta a los 6 s, devuelve 503 y la app aplica la regla sin conexión.
- **Tests con el juez:** `ExcuseRepositoryTest` usa `runBlocking` y no `runTest`, porque con el tiempo virtual el límite de 8 s vence apenas la petición se queda esperando.
- **Emulador lento:** dibuja por software, así que un arranque en frío en debug pasa de 10 s. No sirve para medir el criterio de menos de 1 s; eso se mide en el teléfono.
- **`uiautomator dump` falla** mientras un campo de texto tiene el foco, porque el cursor parpadeando no deja la pantalla quieta. En ese caso se toca por coordenadas.
- **Salir no cierra la app bloqueada:** desde Android 14 una app solo puede matar sus propios procesos. Salir manda al inicio y la app queda en segundo plano; si se reabre, se bloquea otra vez.
- **Pantalla de bloqueo:** se cierra en `onStop` después de Salir, no antes, porque cerrarla antes reanuda la app bloqueada. El servicio nunca descarta un evento de una app bloqueada (traer la pantalla de nuevo no cuesta nada), porque hay apps que abren dos actividades seguidas al arrancar.
- **Bloqueador automático de Samsung (Auto Blocker):** si está activo, bloquea instalar APKs y comandos por USB. Hay que apagarlo para desarrollar.
- **Ajustes restringidos (Android 13+):** si el APK se instala desde un gestor de archivos, hay que permitirlos antes de activar Accesibilidad. Instalando por adb normalmente no aparece.
